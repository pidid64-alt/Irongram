/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import tw.nekomimi.nekogram.plugins.InstalledPlugin;
import tw.nekomimi.nekogram.plugins.PluginPreferences;
import tw.nekomimi.nekogram.plugins.PluginsController;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingCustom;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingDivider;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingHeader;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingInput;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingItem;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingSelector;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingSwitch;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingText;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * Renders the declarative settings of a plugin (or its meta information when it has none).
 */
public class PluginSettingsActivity extends BaseNekoSettingsActivity {

    public static final String EXTRA_PLUGIN_ID = "plugin_id";

    private static final int ENABLED_ROW = 1;
    private static final int DELETE_ROW = 2;
    private static final int SETTINGS_ROW_START = 100;

    private final PluginsController.Listener listener = this::refresh;
    private final ArrayList<PluginSettingItem> currentSettings = new ArrayList<>();
    private boolean listenerRegistered;

    public PluginSettingsActivity() {
        super();
    }

    public PluginSettingsActivity(android.os.Bundle args) {
        super(args);
    }

    private InstalledPlugin getPlugin() {
        if (arguments == null) {
            return null;
        }
        return PluginsController.getInstance().findPlugin(arguments.getString(EXTRA_PLUGIN_ID));
    }

    @Override
    protected String getActionBarTitle() {
        final InstalledPlugin plugin = getPlugin();
        return plugin == null ? LocaleController.getString(R.string.Plugins) : plugin.getName();
    }

    @Override
    protected String getKey() {
        final InstalledPlugin plugin = getPlugin();
        return plugin == null ? "plugin" : "plugin_" + plugin.getId();
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        PluginsController.getInstance().addListener(listener);
        listenerRegistered = true;
        return view;
    }

    @Override
    public void onFragmentDestroy() {
        if (listenerRegistered) {
            listenerRegistered = false;
            PluginsController.getInstance().removeListener(listener);
        }
        super.onFragmentDestroy();
    }

    private void refresh() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private PluginPreferences getPreferences(InstalledPlugin plugin) {
        return PluginsController.getInstance().getPluginPreferences(plugin);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final InstalledPlugin plugin = getPlugin();
        if (plugin == null) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.PluginsEmpty)));
            return;
        }
        final PluginPreferences preferences = getPreferences(plugin);

        items.add(UItem.asHeader(plugin.getName()));

        final StringBuilder meta = new StringBuilder();
        meta.append(LocaleController.formatString(R.string.PluginVersion, plugin.getInfo().getVersion()));
        if (!TextUtils.isEmpty(plugin.getInfo().getAuthor())) {
            meta.append(" • ").append(LocaleController.formatString(R.string.PluginAuthor, plugin.getInfo().getAuthor()));
        }
        items.add(UItem.asShadow(meta));
        if (!TextUtils.isEmpty(plugin.getInfo().getDescription())) {
            items.add(UItem.asShadow(plugin.getInfo().getDescription()));
        }

        final UItem enabled = UItem.asSwitchNoIcon(ENABLED_ROW, LocaleController.getString(R.string.PluginEnabled));
        enabled.checked = plugin.isEnabledByUser();
        items.add(enabled);

        final String error = plugin.getErrorText();
        if (error != null) {
            items.add(UItem.asShadow(LocaleController.formatString(R.string.PluginError, error)));
        }

        currentSettings.clear();
        currentSettings.addAll(plugin.getSettings());
        if (currentSettings.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.PluginNoSettings)));
        } else {
            for (int i = 0; i < currentSettings.size(); i++) {
                addSetting(items, adapter, preferences, currentSettings.get(i), SETTINGS_ROW_START + i);
            }
        }

        items.add(UItem.asButton(DELETE_ROW, R.drawable.msg_delete, LocaleController.getString(R.string.PluginDeleteTitle)));
    }

    private void addSetting(ArrayList<UItem> items, UniversalAdapter adapter, PluginPreferences preferences,
                            PluginSettingItem setting, int rowId) {
        switch (setting.getType()) {
            case PluginSettingItem.TYPE_HEADER:
                items.add(UItem.asHeader(((PluginSettingHeader) setting).getText()));
                break;
            case PluginSettingItem.TYPE_DIVIDER:
                items.add(UItem.asShadow(((PluginSettingDivider) setting).getText()));
                break;
            case PluginSettingItem.TYPE_TEXT:
                items.add(UItem.asShadow(((PluginSettingText) setting).getText()));
                break;
            case PluginSettingItem.TYPE_SWITCH: {
                final PluginSettingSwitch pluginSetting = (PluginSettingSwitch) setting;
                final UItem item = UItem.asSwitchNoIcon(rowId, pluginSetting.getText());
                item.checked = preferences.getBoolean(pluginSetting.getKey(), pluginSetting.getDefaultValue());
                item.object = pluginSetting;
                items.add(item);
                break;
            }
            case PluginSettingItem.TYPE_INPUT: {
                final PluginSettingInput pluginSetting = (PluginSettingInput) setting;
                final String value = preferences.getString(pluginSetting.getKey(), pluginSetting.getDefaultValue());
                final UItem item = UItem.asButton(rowId, pluginSetting.getText(), TextUtils.isEmpty(value) ? "" : value);
                item.object = pluginSetting;
                items.add(item);
                break;
            }
            case PluginSettingItem.TYPE_SELECTOR: {
                final PluginSettingSelector pluginSetting = (PluginSettingSelector) setting;
                final int index = pluginSetting.indexOf(preferences.getString(pluginSetting.getKey(), null));
                final UItem item = UItem.asButton(rowId, pluginSetting.getText(), pluginSetting.getEntries()[index]);
                item.object = pluginSetting;
                items.add(item);
                break;
            }
            case PluginSettingItem.TYPE_CUSTOM: {
                final PluginSettingCustom pluginSetting = (PluginSettingCustom) setting;
                final View view;
                try {
                    view = pluginSetting.getProvider().createView(getContext());
                } catch (Throwable t) {
                    break;
                }
                if (view == null) {
                    break;
                }
                pluginSetting.setPendingView(view);
                try {
                    pluginSetting.getProvider().bindView(view);
                } catch (Throwable ignore) {
                }
                final UItem item = UItem.asCustom(rowId, view);
                item.object = pluginSetting;
                items.add(item);
                break;
            }
            default:
                break;
        }
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        final InstalledPlugin plugin = getPlugin();
        if (plugin == null) {
            return;
        }
        if (item.id == ENABLED_ROW) {
            PluginsController.getInstance().setPluginEnabled(plugin, !plugin.isEnabledByUser());
            notifyItemChanged(ENABLED_ROW);
            return;
        }
        if (item.id == DELETE_ROW) {
            confirmDelete(plugin);
            return;
        }
        if (!(item.object instanceof PluginSettingItem)) {
            return;
        }
        final PluginSettingItem setting = (PluginSettingItem) item.object;
        final PluginPreferences preferences = getPreferences(plugin);
        switch (setting.getType()) {
            case PluginSettingItem.TYPE_SWITCH: {
                final PluginSettingSwitch pluginSetting = (PluginSettingSwitch) setting;
                final boolean value = !preferences.getBoolean(pluginSetting.getKey(), pluginSetting.getDefaultValue());
                preferences.putBoolean(pluginSetting.getKey(), value);
                PluginsController.getInstance().notifySettingChanged(plugin, pluginSetting.getKey());
                notifyItemChanged(item.id);
                break;
            }
            case PluginSettingItem.TYPE_INPUT:
                showInputDialog(plugin, (PluginSettingInput) setting);
                break;
            case PluginSettingItem.TYPE_SELECTOR:
                showSelector(plugin, (PluginSettingSelector) setting, item, view);
                break;
            case PluginSettingItem.TYPE_CUSTOM: {
                final PluginSettingCustom pluginSetting = (PluginSettingCustom) setting;
                try {
                    pluginSetting.getProvider().onClick(view);
                } catch (Throwable ignore) {
                }
                break;
            }
            default:
                break;
        }
    }

    private void showSelector(InstalledPlugin plugin, PluginSettingSelector setting, UItem item, View view) {
        final PluginPreferences preferences = getPreferences(plugin);
        final int checkedIndex = setting.indexOf(preferences.getString(setting.getKey(), null));
        final List<CharSequence> entries = Arrays.asList(setting.getEntries());
        showPopup(entries, checkedIndex, item, view, index -> {
            preferences.putString(setting.getKey(), setting.getValues()[index]);
            PluginsController.getInstance().notifySettingChanged(plugin, setting.getKey());
            notifyItemChanged(item.id);
        });
    }

    private void showInputDialog(InstalledPlugin plugin, PluginSettingInput setting) {
        if (getParentActivity() == null) {
            return;
        }
        final PluginPreferences preferences = getPreferences(plugin);
        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setBackground(null);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        editText.setHint(setting.getHint() == null ? "" : setting.getHint());
        editText.setText(preferences.getString(setting.getKey(), setting.getDefaultValue()));
        editText.setSingleLine(!setting.isMultiline());
        editText.setMaxLines(setting.isMultiline() ? 6 : 1);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | (setting.isMultiline() ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (setting.getMaxLength() > 0) {
            editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(setting.getMaxLength())});
        }

        final FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle(setting.getText());
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            preferences.putString(setting.getKey(), editText.getText().toString());
            PluginsController.getInstance().notifySettingChanged(plugin, setting.getKey());
            refresh();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (DialogInterface.OnClickListener) null);
        showDialog(builder.create());
    }

    private void confirmDelete(InstalledPlugin plugin) {
        if (getParentActivity() == null) {
            return;
        }
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.PluginDeleteTitle));
        builder.setMessage(LocaleController.formatString(R.string.PluginDeleteText, plugin.getName()));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            PluginsController.getInstance().uninstallPlugin(plugin);
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
}
