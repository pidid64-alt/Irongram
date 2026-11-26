/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

public class PluginSettingHeader extends PluginSettingItem {

    private final CharSequence text;

    public PluginSettingHeader(CharSequence text) {
        super(null);
        this.text = text;
    }

    public CharSequence getText() {
        return text;
    }

    @Override
    public int getType() {
        return TYPE_HEADER;
    }
}
