/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

import java.io.File;
import java.util.Collections;
import java.util.List;

import tw.nekomimi.nekogram.plugins.settings.PluginSettingItem;

/**
 * Runtime state of one installed plugin: metadata, on-disk location and the loaded instance.
 * Instances are shared between the engine and the UI, so all mutable state is volatile and
 * guarded by the engine.
 */
public final class InstalledPlugin {

    private final PluginInfo info;
    private final File directory;

    private volatile boolean enabled;
    private volatile boolean loading;
    private volatile Throwable error;
    private volatile BasePlugin instance;
    private volatile ClassLoader classLoader;
    private volatile List<PluginSettingItem> settings;

    InstalledPlugin(PluginInfo info, File directory, boolean enabled) {
        this.info = info;
        this.directory = directory;
        this.enabled = enabled;
    }

    public PluginInfo getInfo() {
        return info;
    }

    public String getId() {
        return info.getId();
    }

    public String getName() {
        return info.getName();
    }

    public File getDirectory() {
        return directory;
    }

    public boolean isEnabled() {
        return enabled && error == null;
    }

    public boolean isEnabledByUser() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLoading() {
        return loading;
    }

    void setLoading(boolean loading) {
        this.loading = loading;
    }

    public Throwable getError() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }

    void setError(Throwable error) {
        this.error = error;
    }

    public BasePlugin getInstance() {
        return instance;
    }

    void setInstance(BasePlugin instance) {
        this.instance = instance;
    }

    public boolean isLoaded() {
        return instance != null;
    }

    ClassLoader getClassLoader() {
        return classLoader;
    }

    void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public List<PluginSettingItem> getSettings() {
        final List<PluginSettingItem> list = settings;
        return list == null ? Collections.emptyList() : list;
    }

    void setSettings(List<PluginSettingItem> settings) {
        this.settings = settings;
    }

    public String getErrorText() {
        final Throwable t = error;
        if (t == null) {
            return null;
        }
        final String message = t.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return t.getClass().getSimpleName();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof InstalledPlugin)) {
            return false;
        }
        return info.getId().equals(((InstalledPlugin) object).info.getId());
    }

    @Override
    public int hashCode() {
        return info.getId().hashCode();
    }

    @Override
    public String toString() {
        return "InstalledPlugin{" + info.getId() + ", enabled=" + enabled + ", loaded=" + isLoaded() + "}";
    }
}
