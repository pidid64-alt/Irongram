/*
 * Irongram plugin system.
 *
 * Inspired by the exteraGram plugin engine (https://github.com/exteraSquad/exteraGram):
 * the same ideas (plugin archives with metadata, per-plugin settings, hooks, safe mode),
 * but plugins are plain JVM bytecode loaded with a DexClassLoader instead of Python scripts.
 */

package tw.nekomimi.nekogram.plugins;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import dalvik.system.DexClassLoader;

/**
 * Loads, unloads and stores plugins. Singleton, initialized once from
 * {@link ApplicationLoader#onCreate()}.
 * <p>
 * Threading model: the public methods may be called from any thread. Class loading, dex
 * optimization and file operations happen on {@link Utilities#globalQueue}, plugin lifecycle
 * callbacks are always invoked on the main thread.
 */
public final class PluginsController implements Application.ActivityLifecycleCallbacks {

    public interface Listener {
        /**
         * Called on the main thread whenever the list of plugins or their state changed.
         */
        void onPluginsChanged();
    }

    public interface InstallCallback {
        /**
         * Called on the main thread when the installation finished.
         *
         * @param plugin installed plugin or {@code null} on failure.
         * @param error  human readable error or {@code null} on success.
         */
        void onInstallFinished(InstalledPlugin plugin, String error);
    }

    private static final PluginsController INSTANCE = new PluginsController();

    public static PluginsController getInstance() {
        return INSTANCE;
    }

    private final CopyOnWriteArrayList<InstalledPlugin> plugins = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> loadingPlugins = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<Activity> resumedActivities = new HashSet<>();

    private final AtomicBoolean initStarted = new AtomicBoolean();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean lifecycleRegistered = new AtomicBoolean();

    private File pluginsDirectory;
    private File incomingDirectory;
    private SharedPreferences preferences;
    private volatile boolean appStarted;

    private PluginsController() {
    }

    //region Initialization

    /**
     * Scans the plugins directory and loads the enabled plugins. Safe to call multiple times,
     * only the first call does the actual work.
     */
    public void init() {
        if (!initStarted.compareAndSet(false, true)) {
            return;
        }
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        pluginsDirectory = new File(ApplicationLoader.getFilesDirFixed(), PluginsConstants.PLUGINS_DIRECTORY);
        if (!pluginsDirectory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pluginsDirectory.mkdirs();
        }
        incomingDirectory = new File(ApplicationLoader.getFilesDirFixed(), PluginsConstants.INCOMING_DIRECTORY);
        if (!incomingDirectory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            incomingDirectory.mkdirs();
        }
        preferences = context.getSharedPreferences(PluginsConstants.PREFERENCES_NAME, Context.MODE_PRIVATE);

        if (lifecycleRegistered.compareAndSet(false, true)) {
            try {
                ((Application) context).registerActivityLifecycleCallbacks(this);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }

        scanInstalledPlugins();
        cleanIncomingDirectory();

        Utilities.globalQueue.postRunnable(() -> {
            if (!isSafeMode()) {
                for (InstalledPlugin plugin : plugins) {
                    if (plugin.isEnabledByUser()) {
                        loadPlugin(plugin);
                    }
                }
            }
            initialized.set(true);
            notifyListeners();
            AndroidUtilities.runOnUIThread(() -> notifyAppEvent(PluginsConstants.EVENT_APP_START));
        });
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    private void scanInstalledPlugins() {
        final File[] files = pluginsDirectory == null ? null : pluginsDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File directory : files) {
            if (!directory.isDirectory() || directory.getName().startsWith(".")) {
                continue;
            }
            try {
                final PluginInfo info = PluginInstaller.readInfoFromDirectory(directory);
                final boolean enabled = preferences == null
                        || preferences.getBoolean(PluginsConstants.PREFERENCE_ENABLED_PREFIX + info.getId(), true);
                plugins.add(new InstalledPlugin(info, directory, enabled));
            } catch (Throwable t) {
                FileLog.e("Can't read plugin " + directory.getName(), t);
            }
        }
    }

    /**
     * Removes stale files that were left in the incoming directory by an interrupted installation.
     */
    private void cleanIncomingDirectory() {
        final File[] files = incomingDirectory == null ? null : incomingDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            PluginInstaller.deleteRecursive(file);
        }
    }

    //endregion

    //region Public state

    public List<InstalledPlugin> getPlugins() {
        final ArrayList<InstalledPlugin> result = new ArrayList<>(plugins);
        Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    public InstalledPlugin findPlugin(String id) {
        if (id == null) {
            return null;
        }
        for (InstalledPlugin plugin : plugins) {
            if (id.equals(plugin.getId())) {
                return plugin;
            }
        }
        return null;
    }

    public File getPluginsDirectory() {
        return pluginsDirectory;
    }

    public File getIncomingDirectory() {
        ensureDirectories();
        return incomingDirectory;
    }

    /**
     * @return preferences of the given plugin. The same file is exposed to the plugin through
     * {@link PluginContext#getPreferences()}.
     */
    public PluginPreferences getPluginPreferences(InstalledPlugin plugin) {
        final Context context = ApplicationLoader.applicationContext;
        return new PluginPreferences(context.getSharedPreferences(
                PluginsConstants.PREFERENCES_PLUGIN_PREFIX + plugin.getId(), Context.MODE_PRIVATE));
    }

    /**
     * Forwards the "setting changed" callback to the plugin. Called by the settings screen after
     * the user has changed a declarative setting.
     */
    public void notifySettingChanged(InstalledPlugin plugin, String key) {
        if (plugin == null) {
            return;
        }
        final BasePlugin instance = plugin.getInstance();
        if (instance == null) {
            return;
        }
        final Runnable runnable = () -> {
            try {
                instance.onSettingChanged(key);
            } catch (Throwable t) {
                FileLog.e("[plugin " + plugin.getId() + "] onSettingChanged failed", t);
            }
        };
        if (isMainThread()) {
            runnable.run();
        } else {
            AndroidUtilities.runOnUIThread(runnable);
        }
    }

    public boolean isSafeMode() {
        return preferences != null && preferences.getBoolean(PluginsConstants.PREFERENCE_SAFE_MODE, false);
    }

    /**
     * In safe mode plugins are not loaded at all, which allows to recover from a broken plugin
     * without reinstalling the app.
     */
    public void setSafeMode(boolean safeMode) {
        if (preferences != null) {
            preferences.edit().putBoolean(PluginsConstants.PREFERENCE_SAFE_MODE, safeMode).apply();
        }
        for (InstalledPlugin plugin : plugins) {
            if (safeMode) {
                unloadPlugin(plugin);
            } else if (plugin.isEnabledByUser()) {
                loadPlugin(plugin);
            }
        }
        notifyListeners();
    }

    public void setPluginEnabled(InstalledPlugin plugin, boolean enabled) {
        if (plugin == null) {
            return;
        }
        plugin.setEnabled(enabled);
        if (enabled) {
            plugin.setError(null);
        }
        final SharedPreferences preferences = this.preferences;
        if (preferences != null) {
            preferences.edit().putBoolean(PluginsConstants.PREFERENCE_ENABLED_PREFIX + plugin.getId(), enabled).apply();
        }
        if (enabled) {
            loadPlugin(plugin);
        } else {
            unloadPlugin(plugin);
        }
        notifyListeners();
    }

    public void reloadPlugin(InstalledPlugin plugin) {
        if (plugin == null) {
            return;
        }
        plugin.setError(null);
        unloadPlugin(plugin);
        if (plugin.isEnabledByUser() && !isSafeMode()) {
            loadPlugin(plugin);
        }
        notifyListeners();
    }

    public void uninstallPlugin(InstalledPlugin plugin) {
        if (plugin == null) {
            return;
        }
        unloadPlugin(plugin);
        plugins.remove(plugin);
        final SharedPreferences preferences = this.preferences;
        if (preferences != null) {
            preferences.edit().remove(PluginsConstants.PREFERENCE_ENABLED_PREFIX + plugin.getId()).apply();
        }
        final Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            context.getSharedPreferences(PluginsConstants.PREFERENCES_PLUGIN_PREFIX + plugin.getId(), Context.MODE_PRIVATE)
                    .edit().clear().apply();
        }
        final File directory = plugin.getDirectory();
        Utilities.globalQueue.postRunnable(() -> PluginInstaller.deleteRecursive(directory));
        notifyListeners();
    }

    public void installPlugin(File archive, InstallCallback callback) {
        ensureDirectories();
        final File directory = pluginsDirectory;
        if (directory == null) {
            if (callback != null) {
                callback.onInstallFinished(null, "Plugin storage is not available");
            }
            return;
        }
        final File source = archive;
        Utilities.globalQueue.postRunnable(() -> {
            InstalledPlugin installed = null;
            String error = null;
            try {
                installed = PluginInstaller.install(source, directory, true);
            } catch (Throwable t) {
                FileLog.e("Can't install plugin", t);
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            }
            final InstalledPlugin result = installed;
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (result != null) {
                    final InstalledPlugin existing = findPlugin(result.getId());
                    if (existing != null) {
                        unloadPlugin(existing);
                        plugins.remove(existing);
                    }
                    plugins.add(result);
                    pluginEnabledPreference(result, true);
                    loadPlugin(result);
                }
                notifyListeners();
                if (callback != null) {
                    callback.onInstallFinished(result, failure);
                }
            });
        });
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Dispatches an app lifecycle event to all loaded plugins.
     */
    public void notifyAppEvent(String event) {
        if (PluginsConstants.EVENT_APP_START.equals(event)) {
            appStarted = true;
        }
        PluginsHooks.dispatchAppEvent(event);
        for (InstalledPlugin plugin : plugins) {
            final BasePlugin instance = plugin.getInstance();
            if (instance == null) {
                continue;
            }
            try {
                instance.onAppEvent(event);
            } catch (Throwable t) {
                FileLog.e("[plugin " + plugin.getId() + "] app event " + event + " failed", t);
            }
        }
    }

    //endregion

    //region Loading

    private void loadPlugin(final InstalledPlugin plugin) {
        if (plugin == null || plugin.isLoaded() || isSafeMode()) {
            return;
        }
        if (!loadingPlugins.add(plugin.getId())) {
            return;
        }
        plugin.setLoading(true);
        plugin.setError(null);
        Utilities.globalQueue.postRunnable(() -> {
            BasePlugin created = null;
            ClassLoader classLoader = null;
            Throwable failure = null;
            try {
                final PluginInfo info = plugin.getInfo();
                final File dexFile = new File(plugin.getDirectory(), PluginsConstants.DEX_FILE);
                if (!dexFile.exists()) {
                    throw new PluginException("classes.dex is missing");
                }
                final File optimizedDirectory = new File(getOptimizedDirectory(), plugin.getId());
                if (!optimizedDirectory.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    optimizedDirectory.mkdirs();
                }
                classLoader = new DexClassLoader(dexFile.getAbsolutePath(), optimizedDirectory.getAbsolutePath(),
                        null, PluginsController.class.getClassLoader());
                final Class<?> entrypoint = Class.forName(info.getEntrypoint(), true, classLoader);
                final Object object = entrypoint.getDeclaredConstructor().newInstance();
                if (!(object instanceof BasePlugin)) {
                    throw new PluginException("entrypoint must extend " + BasePlugin.class.getName());
                }
                created = (BasePlugin) object;
            } catch (Throwable t) {
                failure = t;
            }
            final BasePlugin instance = created;
            final ClassLoader pluginClassLoader = classLoader;
            final Throwable error = failure;
            AndroidUtilities.runOnUIThread(() -> {
                loadingPlugins.remove(plugin.getId());
                plugin.setLoading(false);
                if (instance == null) {
                    final Throwable reason = error != null ? error : new PluginException("Unable to load the plugin");
                    FileLog.e("[plugin " + plugin.getId() + "] failed to load", reason);
                    plugin.setError(reason);
                    plugin.setInstance(null);
                    plugin.setClassLoader(null);
                    plugin.setSettings(null);
                    plugin.setEnabled(false);
                    pluginEnabledPreference(plugin, false);
                    notifyListeners();
                    return;
                }
                if (!plugin.isEnabledByUser() || isSafeMode()) {
                    // The plugin was disabled (or the safe mode was turned on) while it was loading.
                    plugin.setInstance(null);
                    plugin.setClassLoader(null);
                    plugin.setSettings(null);
                    notifyListeners();
                    return;
                }
                try {
                    instance.attach(new PluginContext(plugin));
                    instance.onLoad();
                    plugin.setInstance(instance);
                    plugin.setClassLoader(pluginClassLoader);
                    try {
                        plugin.setSettings(instance.createSettings());
                    } catch (Throwable t) {
                        FileLog.e("[plugin " + plugin.getId() + "] createSettings failed", t);
                        plugin.setSettings(null);
                    }
                    if (appStarted) {
                        try {
                            instance.onAppEvent(PluginsConstants.EVENT_APP_START);
                        } catch (Throwable t) {
                            FileLog.e("[plugin " + plugin.getId() + "] app start event failed", t);
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e("[plugin " + plugin.getId() + "] onLoad failed", t);
                    plugin.setError(t);
                    plugin.setEnabled(false);
                    pluginEnabledPreference(plugin, false);
                    unloadInstance(plugin, instance);
                }
                notifyListeners();
            });
        });
    }

    private void unloadPlugin(final InstalledPlugin plugin) {
        if (plugin == null) {
            return;
        }
        final BasePlugin instance = plugin.getInstance();
        plugin.setInstance(null);
        plugin.setClassLoader(null);
        plugin.setSettings(null);
        if (instance != null) {
            unloadInstance(plugin, instance);
        }
    }

    private void unloadInstance(final InstalledPlugin plugin, final BasePlugin instance) {
        final Runnable unload = () -> {
            try {
                instance.onUnload();
            } catch (Throwable t) {
                FileLog.e("[plugin " + plugin.getId() + "] onUnload failed", t);
            } finally {
                final PluginContext context = instance.getContext();
                if (context != null) {
                    context.destroy();
                }
            }
        };
        if (isMainThread()) {
            unload.run();
        } else {
            AndroidUtilities.runOnUIThread(unload);
        }
    }

    //endregion

    //region Helpers

    private void ensureDirectories() {
        if (pluginsDirectory == null) {
            pluginsDirectory = new File(ApplicationLoader.getFilesDirFixed(), PluginsConstants.PLUGINS_DIRECTORY);
            if (!pluginsDirectory.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pluginsDirectory.mkdirs();
            }
        }
        if (incomingDirectory == null) {
            incomingDirectory = new File(ApplicationLoader.getFilesDirFixed(), PluginsConstants.INCOMING_DIRECTORY);
            if (!incomingDirectory.exists()) {
                //noinspection ResultOfMethodCallIgnored
                incomingDirectory.mkdirs();
            }
        }
    }

    private File getOptimizedDirectory() {
        final Context context = ApplicationLoader.applicationContext;
        File base = null;
        if (context != null) {
            try {
                base = context.getCodeCacheDir();
            } catch (Throwable ignore) {
            }
            if (base == null) {
                base = context.getCacheDir();
            }
        }
        final File directory = new File(base != null ? base : ApplicationLoader.getFilesDirFixed(), "plugins");
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
        }
        return directory;
    }

    private void pluginEnabledPreference(InstalledPlugin plugin, boolean enabled) {
        final SharedPreferences preferences = this.preferences;
        if (preferences != null) {
            preferences.edit().putBoolean(PluginsConstants.PREFERENCE_ENABLED_PREFIX + plugin.getId(), enabled).apply();
        }
    }

    private void notifyListeners() {
        final Runnable runnable = () -> {
            for (Listener listener : listeners) {
                try {
                    listener.onPluginsChanged();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        };
        if (isMainThread()) {
            runnable.run();
        } else {
            AndroidUtilities.runOnUIThread(runnable);
        }
    }

    private static boolean isMainThread() {
        return Looper.getMainLooper() != null && Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    //endregion

    //region App lifecycle

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
        final boolean resumed;
        synchronized (resumedActivities) {
            resumed = resumedActivities.isEmpty();
            resumedActivities.add(activity);
        }
        if (resumed) {
            notifyAppEvent(PluginsConstants.EVENT_APP_RESUME);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        final boolean paused;
        synchronized (resumedActivities) {
            resumedActivities.remove(activity);
            paused = resumedActivities.isEmpty();
        }
        if (paused) {
            notifyAppEvent(PluginsConstants.EVENT_APP_PAUSE);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        synchronized (resumedActivities) {
            resumedActivities.remove(activity);
        }
    }

    //endregion
}
