/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

public class PluginSettingInput extends PluginSettingItem {

    private final CharSequence text;
    private final String hint;
    private final String defaultValue;
    private final boolean multiline;
    private final int maxLength;

    public PluginSettingInput(String key, CharSequence text, String hint, String defaultValue) {
        this(key, text, hint, defaultValue, false, 0);
    }

    public PluginSettingInput(String key, CharSequence text, String hint, String defaultValue, boolean multiline, int maxLength) {
        super(key);
        this.text = text;
        this.hint = hint;
        this.defaultValue = defaultValue;
        this.multiline = multiline;
        this.maxLength = maxLength;
    }

    public CharSequence getText() {
        return text;
    }

    public String getHint() {
        return hint;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public int getMaxLength() {
        return maxLength;
    }

    @Override
    public int getType() {
        return TYPE_INPUT;
    }
}
