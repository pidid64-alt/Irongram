/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

import org.telegram.ui.ActionBar.BaseFragment;

import java.util.List;

import tw.nekomimi.nekogram.plugins.settings.PluginSettingItem;

/**
 * Entry point of every plugin.
 * <p>
 * A plugin is a {@code .plugin} archive that contains {@code plugin.json} and {@code classes.dex}.
 * Its {@link PluginInfo#getEntrypoint() entrypoint} class must extend this class and have a public
 * no-argument constructor.
 * <p>
 * Lifecycle:
 * <ol>
 *     <li>the class is instantiated by the engine,</li>
 *     <li>{@link #onLoad()} is called on the main thread,</li>
 *     <li>{@link #onUnload()} is called on the main thread when the plugin is disabled, updated,
 *     deleted or when the app shuts the engine down.</li>
 * </ol>
 * A plugin gets full access to the app classes, because it is loaded inside the app process. Use it
 * carefully: an uncaught exception inside a callback is logged and the plugin is disabled.
 */
public abstract class BasePlugin {

    private PluginContext context;

    final void attach(PluginContext context) {
        this.context = context;
    }

    /**
     * @return context that gives access to the plugin preferences, hooks and app helpers.
     */
    public final PluginContext getContext() {
        return context;
    }

    public final PluginInfo getInfo() {
        return context.getInfo();
    }

    /**
     * Called when the plugin is loaded. Always invoked on the main thread.
     */
    public void onLoad() {
    }

    /**
     * Called when the plugin is unloaded. Always invoked on the main thread.
     * All hooks and notification observers registered through the context are removed automatically.
     */
    public void onUnload() {
    }

    /**
     * Called on app lifecycle events, see {@code PluginsConstants.EVENT_APP_*}.
     */
    public void onAppEvent(String event) {
    }

    /**
     * Declarative settings of the plugin. The engine renders them in the plugin settings screen and
     * stores the values in the plugin preferences, so the plugin only has to read them back.
     *
     * @return list of settings or {@code null} if the plugin has no settings.
     */
    public List<PluginSettingItem> createSettings() {
        return null;
    }

    /**
     * Called on the main thread after a declarative setting has been changed by the user.
     */
    public void onSettingChanged(String key) {
    }

    /**
     * Allows the plugin to provide a fully custom settings screen instead of the declarative one.
     *
     * @return fragment to show or {@code null} to use the declarative settings.
     */
    public BaseFragment createSettingsFragment() {
        return null;
    }
}
