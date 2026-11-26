/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

public class PluginSettingSwitch extends PluginSettingItem {

    private final CharSequence text;
    private final boolean defaultValue;

    public PluginSettingSwitch(String key, CharSequence text, boolean defaultValue) {
        super(key);
        this.text = text;
        this.defaultValue = defaultValue;
    }

    public CharSequence getText() {
        return text;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    @Override
    public int getType() {
        return TYPE_SWITCH;
    }
}
