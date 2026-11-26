package org.telegram.messenger;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Starts the embedded interpreter and installs the bundled exteraGram plugin SDK. */
public final class PythonPluginSdk {
    private static final String TAG = "IrongramPluginSdk";
    private static final String ASSET_ROOT = "plugins_pysdk/";
    private static final long MAX_UNCOMPRESSED_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 4096;

    private static volatile boolean initialized;
    private static volatile String sdkVersion;
    private static volatile String initializationError;

    private PythonPluginSdk() {
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static String getSdkVersion() {
        return sdkVersion;
    }

    public static String getInitializationError() {
        return initializationError;
    }

    /** Safe to call repeatedly. The archive is verified and extracted atomically before use. */
    public static synchronized boolean initialize(Context context) {
        if (initialized) {
            return true;
        }
        try {
            Context appContext = context.getApplicationContext();
            String version = readAssetText(appContext, ASSET_ROOT + "v.txt", 128).trim();
            if (!version.matches("[0-9]+(\\.[0-9]+)*")) {
                throw new IllegalStateException("Invalid bundled SDK version");
            }

            File sdkDir = new File(appContext.getFilesDir(), "plugins_pysdk/" + version);
            if (!isSdkDirectoryReady(sdkDir, version)) {
                installSdkArchive(appContext, version, sdkDir);
            }

            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(appContext));
            }
            Python python = Python.getInstance();
            PyObject sysPath = python.getModule("sys").get("path");
            sysPath.callAttr("insert", 0, sdkDir.getAbsolutePath());
            PyObject versionModule = python.getModule("_sdk_version");
            sdkVersion = versionModule.get("__version__").toString();
            if (!version.equals(sdkVersion)) {
                throw new IllegalStateException("SDK version mismatch: expected " + version + ", got " + sdkVersion);
            }
            initialized = true;
            initializationError = null;
            return true;
        } catch (Throwable error) {
            initializationError = error.toString();
            Log.e(TAG, "Unable to initialize embedded Python SDK", error);
            return false;
        }
    }

    private static String readAssetText(Context context, String asset, int maxChars) throws Exception {
        StringBuilder result = new StringBuilder();
        try (InputStream input = context.getAssets().open(asset);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[256];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (result.length() + count > maxChars) {
                    throw new IllegalStateException("Oversized SDK metadata");
                }
                result.append(buffer, 0, count);
            }
        }
        return result.toString();
    }

    private static String sdkArchiveName() {
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        for (String abi : supportedAbis) {
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)
                    || "x86".equals(abi) || "x86_64".equals(abi)) {
                return "sdk-" + abi + ".zip";
            }
        }
        throw new IllegalStateException("No Python SDK archive for this device ABI");
    }

    private static boolean isSdkDirectoryReady(File directory, String expectedVersion) {
        if (!new File(directory, "_sdk_version.pyc").isFile()
                || !new File(directory, "base_plugin.pyc").isFile()
                || !new File(directory, "plugin_settings.pyc").isFile()) {
            return false;
        }
        try {
            return readSmallFile(new File(directory, "v.txt"), 128).trim().startsWith(expectedVersion + "|");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void installSdkArchive(Context context, String expectedVersion, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IllegalStateException("Unable to create SDK directory");
        }
        File staging = new File(parent, destination.getName() + ".tmp-" + android.os.Process.myPid());
        deleteRecursively(staging);
        if (!staging.mkdirs()) {
            throw new IllegalStateException("Unable to create SDK staging directory");
        }

        boolean valid = false;
        try (InputStream input = context.getAssets().open(ASSET_ROOT + sdkArchiveName());
             ZipInputStream zip = new ZipInputStream(input)) {
            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            int entries = 0;
            boolean foundVersion = false;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalStateException("SDK archive contains too many files");
                }
                File output = new File(staging, entry.getName());
                String root = staging.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(root)) {
                    throw new IllegalStateException("Invalid path in SDK archive");
                }
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IllegalStateException("Unable to create SDK package directory");
                    }
                } else {
                    File outputParent = output.getParentFile();
                    if (outputParent == null || (!outputParent.isDirectory() && !outputParent.mkdirs())) {
                        throw new IllegalStateException("Unable to create SDK package directory");
                    }
                    try (FileOutputStream fileOutput = new FileOutputStream(output)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            totalBytes += read;
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw new IllegalStateException("SDK archive is too large");
                            }
                            fileOutput.write(buffer, 0, read);
                        }
                    }
                    if ("v.txt".equals(entry.getName())) {
                        String archiveVersion = readSmallFile(output, 128).trim();
                        if (!archiveVersion.startsWith(expectedVersion + "|")) {
                            throw new IllegalStateException("SDK archive version does not match app assets");
                        }
                        foundVersion = true;
                    }
                }
                zip.closeEntry();
            }
            valid = foundVersion
                    && new File(staging, "_sdk_version.pyc").isFile()
                    && new File(staging, "base_plugin.pyc").isFile()
                    && new File(staging, "plugin_settings.pyc").isFile();
        } finally {
            if (!valid) {
                deleteRecursively(staging);
            }
        }

        if (!valid) {
            throw new IllegalStateException("SDK archive is incomplete");
        }
        deleteRecursively(destination);
        if (!staging.renameTo(destination)) {
            deleteRecursively(staging);
            throw new IllegalStateException("Unable to install SDK archive");
        }
    }

    private static String readSmallFile(File file, int maxChars) throws Exception {
        StringBuilder result = new StringBuilder();
        try (FileInputStream input = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[128];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (result.length() + count > maxChars) {
                    throw new IllegalStateException("Oversized SDK version file");
                }
                result.append(buffer, 0, count);
            }
        }
        return result.toString();
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Unable to delete temporary file: " + file);
        }
    }
}
