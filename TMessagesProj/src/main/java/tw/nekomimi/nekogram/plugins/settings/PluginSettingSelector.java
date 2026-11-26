/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

public class PluginSettingSelector extends PluginSettingItem {

    private final CharSequence text;
    private final CharSequence[] entries;
    private final String[] values;
    private final int defaultIndex;

    /**
     * @param entries entries shown to the user.
     * @param values  persisted values, {@code values[index]} is stored when {@code entries[index]} is picked.
     */
    public PluginSettingSelector(String key, CharSequence text, CharSequence[] entries, String[] values, int defaultIndex) {
        super(key);
        if (entries == null || values == null || entries.length != values.length || entries.length == 0) {
            throw new IllegalArgumentException("entries and values must be non empty and of the same length");
        }
        this.text = text;
        this.entries = entries;
        this.values = values;
        this.defaultIndex = Math.max(0, Math.min(defaultIndex, entries.length - 1));
    }

    public CharSequence getText() {
        return text;
    }

    public CharSequence[] getEntries() {
        return entries;
    }

    public String[] getValues() {
        return values;
    }

    public int getDefaultIndex() {
        return defaultIndex;
    }

    public int indexOf(String value) {
        if (value != null) {
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    return i;
                }
            }
        }
        return defaultIndex;
    }

    @Override
    public int getType() {
        return TYPE_SELECTOR;
    }
}
