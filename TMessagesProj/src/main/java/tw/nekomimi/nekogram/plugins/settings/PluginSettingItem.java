/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

/**
 * One row of the declarative plugin settings. The engine renders these rows with the same cells
 * the rest of the app uses, so plugins don't have to write any UI code for simple settings.
 */
public abstract class PluginSettingItem {

    public static final int TYPE_SWITCH = 0;
    public static final int TYPE_INPUT = 1;
    public static final int TYPE_SELECTOR = 2;
    public static final int TYPE_HEADER = 3;
    public static final int TYPE_DIVIDER = 4;
    public static final int TYPE_TEXT = 5;
    public static final int TYPE_CUSTOM = 6;

    private final String key;

    protected PluginSettingItem(String key) {
        this.key = key;
    }

    /**
     * @return preference key of this setting, may be {@code null} for headers and dividers.
     */
    public String getKey() {
        return key;
    }

    public abstract int getType();
}
