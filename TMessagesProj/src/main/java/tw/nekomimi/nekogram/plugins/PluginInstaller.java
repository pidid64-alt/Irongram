/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

import org.telegram.messenger.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads and extracts {@code .plugin} archives.
 * <p>
 * Archive layout:
 * <pre>
 * plugin.json   - metadata, see {@link PluginInfo}
 * classes.dex   - compiled plugin code
 * icon.png      - optional icon
 * </pre>
 * Only the known entries are extracted, so a crafted archive cannot write files outside of the
 * plugin directory.
 */
final class PluginInstaller {

    private PluginInstaller() {
    }

    static InstalledPlugin install(File archive, File pluginsDirectory, boolean enabled) throws PluginException {
        if (archive == null || !archive.exists() || !archive.isFile()) {
            throw new PluginException("Plugin file not found");
        }
        if (archive.length() > PluginsConstants.MAX_ARCHIVE_SIZE) {
            throw new PluginException("Plugin archive is too big");
        }
        final File tempDirectory = new File(pluginsDirectory, ".tmp-" + System.currentTimeMillis());
        try (ZipFile zip = new ZipFile(archive)) {
            final String manifest = readManifest(zip);
            final PluginInfo info = PluginInfo.parse(manifest);
            if (info.getMinAppVersionCode() > BuildConfig.VERSION_CODE) {
                throw new PluginException("Plugin requires a newer app version");
            }
            final ZipEntry dexEntry = zip.getEntry(PluginsConstants.DEX_FILE);
            if (dexEntry == null) {
                throw new PluginException("classes.dex is missing in the archive");
            }
            if (!tempDirectory.exists() && !tempDirectory.mkdirs()) {
                throw new PluginException("Can't create the plugin directory");
            }
            copyEntry(zip, dexEntry, new File(tempDirectory, PluginsConstants.DEX_FILE), PluginsConstants.MAX_DEX_SIZE);
            writeString(new File(tempDirectory, PluginsConstants.MANIFEST_FILE), manifest);
            final ZipEntry iconEntry = zip.getEntry(PluginsConstants.ICON_FILE);
            if (iconEntry != null) {
                try {
                    copyEntry(zip, iconEntry, new File(tempDirectory, PluginsConstants.ICON_FILE), PluginsConstants.MAX_ICON_SIZE);
                } catch (PluginException ignore) {
                    // icon is optional
                }
            }

            final File target = new File(pluginsDirectory, info.getId());
            deleteRecursive(target);
            if (!tempDirectory.renameTo(target)) {
                copyDirectory(tempDirectory, target);
                deleteRecursive(tempDirectory);
            }
            return new InstalledPlugin(info, target, enabled);
        } catch (IOException e) {
            throw new PluginException("Can't read the plugin archive", e);
        } finally {
            deleteRecursive(tempDirectory);
        }
    }

    static PluginInfo readInfo(File archive) throws PluginException {
        if (archive == null || !archive.exists() || !archive.isFile()) {
            throw new PluginException("Plugin file not found");
        }
        try (ZipFile zip = new ZipFile(archive)) {
            return PluginInfo.parse(readManifest(zip));
        } catch (IOException e) {
            throw new PluginException("Can't read the plugin archive", e);
        }
    }

    static PluginInfo readInfoFromDirectory(File directory) throws PluginException {
        final File manifest = new File(directory, PluginsConstants.MANIFEST_FILE);
        if (!manifest.exists()) {
            throw new PluginException("plugin.json is missing");
        }
        if (manifest.length() > PluginsConstants.MAX_MANIFEST_SIZE) {
            throw new PluginException("plugin.json is too big");
        }
        try (InputStream input = new FileInputStream(manifest)) {
            return PluginInfo.parse(readString(input, PluginsConstants.MAX_MANIFEST_SIZE));
        } catch (IOException e) {
            throw new PluginException("Can't read plugin.json", e);
        }
    }

    private static String readManifest(ZipFile zip) throws PluginException {
        final ZipEntry entry = zip.getEntry(PluginsConstants.MANIFEST_FILE);
        if (entry == null) {
            throw new PluginException("plugin.json is missing in the archive");
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return readString(input, PluginsConstants.MAX_MANIFEST_SIZE);
        } catch (IOException e) {
            throw new PluginException("Can't read plugin.json", e);
        }
    }

    private static String readString(InputStream input, long maxSize) throws IOException, PluginException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxSize) {
                throw new PluginException("plugin.json is too big");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void copyEntry(ZipFile zip, ZipEntry entry, File target, long maxSize) throws PluginException {
        try (InputStream input = zip.getInputStream(entry)) {
            copyStream(input, target, maxSize);
        } catch (IOException e) {
            throw new PluginException("Can't extract " + entry.getName(), e);
        }
    }

    private static void copyStream(InputStream input, File target, long maxSize) throws IOException, PluginException {
        final File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStream output = new FileOutputStream(target)) {
            final byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxSize) {
                    throw new PluginException("Entry " + target.getName() + " is too big");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static void writeString(File target, String value) throws IOException {
        try (OutputStream output = new FileOutputStream(target)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyDirectory(File from, File to) throws PluginException {
        if (!to.exists() && !to.mkdirs()) {
            throw new PluginException("Can't create the plugin directory");
        }
        final File[] files = from.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            final File destination = new File(to, file.getName());
            try (InputStream input = new FileInputStream(file);
                 OutputStream output = new FileOutputStream(destination)) {
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (IOException e) {
                throw new PluginException("Can't copy " + file.getName(), e);
            }
        }
    }

    static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
