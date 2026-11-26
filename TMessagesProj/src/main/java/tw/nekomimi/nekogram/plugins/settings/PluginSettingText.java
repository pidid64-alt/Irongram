/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

/**
 * Read only text row, used for descriptions.
 */
public class PluginSettingText extends PluginSettingItem {

    private final CharSequence text;

    public PluginSettingText(CharSequence text) {
        super(null);
        this.text = text;
    }

    public CharSequence getText() {
        return text;
    }

    @Override
    public int getType() {
        return TYPE_TEXT;
    }
}
