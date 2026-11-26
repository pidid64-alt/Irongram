/*
 * Irongram plugin system.
 *
 * Based on the idea of the exteraGram plugin system (https://github.com/exteraSquad/exteraGram),
 * but implemented as a self-contained JVM/DEX engine: plugins are compiled to classes.dex and
 * loaded with a DexClassLoader, so no external scripting runtime is required.
 */

package tw.nekomimi.nekogram.plugins;

public final class PluginsConstants {

    private PluginsConstants() {
    }

    /**
     * Version of the plugin API. Plugins that were compiled against a newer API version are
     * rejected, because the classes they reference may no longer exist.
     */
    public static final int API_VERSION = 1;

    /** Directory (inside {@link org.telegram.messenger.ApplicationLoader#getFilesDirFixed()}) with installed plugins. */
    public static final String PLUGINS_DIRECTORY = "plugins";

    /** Temporary directory used while an archive is being installed. */
    public static final String INCOMING_DIRECTORY = "plugins-incoming";

    /** Manifest file of a plugin archive. Must be a valid JSON object. */
    public static final String MANIFEST_FILE = "plugin.json";

    /** Compiled plugin code. */
    public static final String DEX_FILE = "classes.dex";

    /** Optional plugin icon, PNG. */
    public static final String ICON_FILE = "icon.png";

    /** Extension used for plugin archives. */
    public static final String PLUGIN_EXTENSION = ".plugin";

    /** SharedPreferences file with the global plugin state. */
    public static final String PREFERENCES_NAME = "plugins";

    /** Prefix of the boolean preference that remembers whether the plugin is enabled. */
    public static final String PREFERENCE_ENABLED_PREFIX = "plugin_enabled_";

    /** Preference that switches the whole engine into safe mode (plugins are not loaded at all). */
    public static final String PREFERENCE_SAFE_MODE = "plugins_safe_mode";

    /** SharedPreferences file prefix for the per-plugin settings. */
    public static final String PREFERENCES_PLUGIN_PREFIX = "plugin_prefs_";

    /** Maximum size of a plugin archive. */
    public static final long MAX_ARCHIVE_SIZE = 16L * 1024L * 1024L;

    /** Maximum size of a plugin manifest. */
    public static final long MAX_MANIFEST_SIZE = 128L * 1024L;

    /** Maximum size of the plugin dex file. */
    public static final long MAX_DEX_SIZE = 16L * 1024L * 1024L;

    /** Maximum size of the plugin icon. */
    public static final long MAX_ICON_SIZE = 1024L * 1024L;

    public static final String EVENT_APP_START = "app_start";
    public static final String EVENT_APP_STOP = "app_stop";
    public static final String EVENT_APP_RESUME = "app_resume";
    public static final String EVENT_APP_PAUSE = "app_pause";

    /** Ids of the plugin cells, so that they don't clash with the ids of the settings rows. */
    public static final int ROW_ID_OFFSET = 100_000;
}
