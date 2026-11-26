/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

public class PluginSettingDivider extends PluginSettingItem {

    private final CharSequence text;

    public PluginSettingDivider() {
        this(null);
    }

    /**
     * @param text optional explanation shown under the shadow divider.
     */
    public PluginSettingDivider(CharSequence text) {
        super(null);
        this.text = text;
    }

    public CharSequence getText() {
        return text;
    }

    @Override
    public int getType() {
        return TYPE_DIVIDER;
    }
}
