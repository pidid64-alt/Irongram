package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.plugins.IrongramPluginsController;
import tw.nekomimi.nekogram.plugins.PluginMetadata;

public final class IrongramPluginsActivity extends BaseNekoSettingsActivity {
    private static final int PICK_PLUGIN_FILE = 19742;
    private static final int MENU_ADD_PLUGIN = 19743;
    private static final int MENU_PLUGIN_INFO = 19744;
    private static final int PLUGIN_ROW_BASE = 200000;

    private final int pluginSystemRow = rowId++;
    private final ArrayList<IrongramPluginsController.InstalledPlugin> pluginRows = new ArrayList<>();
    private String searchQuery = "";

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        ActionBarMenu menu = actionBar.createMenu();
        createSearchItem(menu, new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchCollapse() {
                searchQuery = "";
                updateActionBarVisible();
                updatePluginList();
            }

            @Override
            public void onSearchExpand() {
                updateActionBarVisible();
            }

            @Override
            public void onTextChanged(EditText editText) {
                searchQuery = editText.getText().toString();
                updatePluginList();
            }
        });

        ActionBarMenuItem infoItem = menu.addItem(MENU_PLUGIN_INFO, R.drawable.msg_info, resourcesProvider);
        infoItem.setContentDescription(LocaleController.getString(R.string.PluginManagerWarningTitle));
        ActionBarMenuItem addItem = menu.addItem(MENU_ADD_PLUGIN, R.drawable.msg_add, resourcesProvider);
        addItem.setContentDescription(LocaleController.getString(R.string.InstallPlugin));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment();
                    }
                } else if (id == MENU_ADD_PLUGIN) {
                    openPluginPicker();
                } else if (id == MENU_PLUGIN_INFO) {
                    showPluginSafetyInfo();
                }
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePluginList();
    }

    private void updatePluginList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        IrongramPluginsController controller = IrongramPluginsController.getInstance();
        items.add(PluginSystemCellFactory.of(
                pluginSystemRow,
                controller.isPluginSystemEnabled(),
                this
        ));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(LocaleController.getString(R.string.InstalledPlugins)));

        pluginRows.clear();
        List<IrongramPluginsController.InstalledPlugin> installed = controller.getInstalledPlugins();
        pluginRows.addAll(installed);
        if (pluginRows.isEmpty()) {
            items.add(TextDetailSettingsCellFactory.of(
                    PLUGIN_ROW_BASE,
                    LocaleController.getString(R.string.NoPluginsInstalled),
                    LocaleController.getString(R.string.NoPluginsInstalledDetail)
            ).setEnabled(false));
            return;
        }

        int visiblePlugins = 0;
        for (int index = 0; index < pluginRows.size(); index++) {
            IrongramPluginsController.InstalledPlugin plugin = pluginRows.get(index);
            if (!matchesSearch(plugin, searchQuery)) {
                continue;
            }
            items.add(PluginCardFactory.of(
                    PLUGIN_ROW_BASE + index,
                    plugin,
                    this,
                    controller.isPluginSystemEnabled()
            ));
            visiblePlugins++;
        }

        if (visiblePlugins == 0) {
            items.add(TextDetailSettingsCellFactory.of(
                    PLUGIN_ROW_BASE + pluginRows.size(),
                    LocaleController.getString(R.string.NoPluginSearchResults),
                    ""
            ).setEnabled(false));
        }
    }

    private static boolean matchesSearch(IrongramPluginsController.InstalledPlugin plugin, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        PluginMetadata metadata = plugin.metadata;
        if (contains(metadata.name, normalizedQuery)
                || contains(metadata.id, normalizedQuery)
                || contains(metadata.author, normalizedQuery)
                || contains(metadata.description, normalizedQuery)
                || contains(plugin.error, normalizedQuery)) {
            return true;
        }
        for (String requirement : metadata.requirements) {
            if (contains(requirement, normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        // The master switch and plugin cards handle their own clicks to keep their inline controls independent.
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_PLUGIN_FILE || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            showToast(R.string.PluginInstallFailed);
            return;
        }
        IrongramPluginsController.getInstance().importPlugin(uri, (metadata, error) -> {
            updatePluginList();
            if (metadata == null) {
                showToast(error == null ? LocaleController.getString(R.string.PluginInstallFailed) : error);
                return;
            }
            showToast(LocaleController.formatString(R.string.PluginInstalled, metadata.name));
        });
    }

    private void openPluginPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/x-python",
                "application/x-python-code",
                "text/plain",
                "application/octet-stream"
        });
        startActivityForResult(intent, PICK_PLUGIN_FILE);
    }

    private void showPluginSafetyInfo() {
        new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                .setTitle(LocaleController.getString(R.string.PluginManagerWarningTitle))
                .setMessage(LocaleController.getString(R.string.PluginManagerWarning))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }

    private void showPluginActions(IrongramPluginsController.InstalledPlugin entry) {
        PluginMetadata metadata = entry.metadata;
        StringBuilder details = new StringBuilder();
        details.append(LocaleController.formatString(R.string.PluginVersion, metadata.version));
        if (!metadata.author.isEmpty()) {
            details.append("\n").append(LocaleController.formatString(R.string.PluginAuthor, metadata.author));
        }
        if (!metadata.requirements.isEmpty()) {
            details.append("\n").append(LocaleController.formatString(
                    R.string.PluginDependencies,
                    TextUtils.join(", ", metadata.requirements)
            ));
        }
        if (!metadata.description.isEmpty()) {
            details.append("\n\n").append(metadata.description);
        }
        if (!entry.enabled && entry.error != null && !entry.error.isEmpty()) {
            details.append("\n\n").append(LocaleController.getString(R.string.PluginLastError))
                    .append(": ").append(entry.error);
        }
        if (entry.enabled && !IrongramPluginsController.getInstance().isPluginSystemEnabled()) {
            details.append("\n\n").append(LocaleController.getString(R.string.PluginSystemPaused));
        }

        CharSequence[] actions = entry.enabled
                ? new CharSequence[]{
                        LocaleController.getString(R.string.PluginSettings),
                        LocaleController.getString(R.string.DisablePlugin),
                        LocaleController.getString(R.string.RemovePlugin)
                }
                : new CharSequence[]{
                        LocaleController.getString(R.string.EnablePlugin),
                        LocaleController.getString(R.string.RemovePlugin)
                };
        new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                .setTitle(metadata.name)
                .setMessage(details.toString())
                .setItems(actions, (dialog, which) -> {
                    if (entry.enabled) {
                        if (which == 0) {
                            openPluginSettings(entry);
                        } else if (which == 1) {
                            setPluginEnabled(metadata, false);
                        } else if (which == 2) {
                            confirmRemove(metadata);
                        }
                    } else if (which == 0) {
                        confirmEnable(metadata);
                    } else if (which == 1) {
                        confirmRemove(metadata);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void openPluginSettings(IrongramPluginsController.InstalledPlugin entry) {
        if (!entry.enabled) {
            showToast(R.string.PluginSettingsUnavailable);
            return;
        }
        if (!IrongramPluginsController.getInstance().isPluginSystemEnabled()) {
            showToast(R.string.PluginSettingsUnavailable);
            return;
        }
        presentFragment(new IrongramPluginSettingsActivity(entry.metadata));
    }

    private void confirmEnable(PluginMetadata metadata) {
        new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                .setTitle(LocaleController.getString(R.string.EnablePlugin))
                .setMessage(LocaleController.getString(R.string.PluginTrustWarning))
                .setPositiveButton(LocaleController.getString(R.string.EnablePlugin), (dialog, which) ->
                        setPluginEnabled(metadata, true))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void confirmRemove(PluginMetadata metadata) {
        new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                .setTitle(LocaleController.getString(R.string.RemovePlugin))
                .setMessage(LocaleController.formatString(R.string.RemovePluginConfirmation, metadata.name))
                .setPositiveButton(LocaleController.getString(R.string.RemovePlugin), (dialog, which) ->
                        IrongramPluginsController.getInstance().removePlugin(metadata, (success, error) -> {
                            updatePluginList();
                            if (success) {
                                showToast(R.string.PluginRemoved);
                            } else {
                                showToast(error == null
                                        ? LocaleController.getString(R.string.PluginOperationFailed)
                                        : error);
                            }
                        }))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void setPluginEnabled(PluginMetadata metadata, boolean enabled) {
        IrongramPluginsController.getInstance().setPluginEnabled(metadata, enabled, (success, error) -> {
            updatePluginList();
            if (success) {
                if (enabled && !IrongramPluginsController.getInstance().isPluginSystemEnabled()) {
                    showToast(R.string.PluginEnabledWhilePaused);
                } else {
                    showToast(LocaleController.getString(enabled ? R.string.PluginEnabled : R.string.PluginDisabled));
                }
            } else {
                showToast(error == null ? LocaleController.getString(R.string.PluginOperationFailed) : error);
            }
        });
    }

    private void setPluginSystemEnabled(boolean enabled) {
        IrongramPluginsController controller = IrongramPluginsController.getInstance();
        controller.setPluginSystemEnabled(enabled, (success, error) -> {
            updatePluginList();
            if (success) {
                showToast(enabled ? R.string.PluginSystemEnabledStatus : R.string.PluginSystemDisabledStatus);
            } else {
                showToast(error == null ? LocaleController.getString(R.string.PluginOperationFailed) : error);
            }
        });
        updatePluginList();
    }

    private void sharePluginSource(PluginMetadata metadata) {
        try {
            File pluginFile = IrongramPluginsController.getInstance().getPluginFile(metadata);
            if (getParentActivity() == null || pluginFile == null || !pluginFile.isFile()) {
                showToast(R.string.PluginOperationFailed);
                return;
            }
            Uri uri = FileProvider.getUriForFile(
                    getParentActivity(),
                    ApplicationLoader.getApplicationId() + ".provider",
                    pluginFile
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/x-python");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(Intent.createChooser(
                    intent,
                    LocaleController.getString(R.string.ShareFile)
            ));
        } catch (Throwable error) {
            showToast(error.getMessage() == null
                    ? LocaleController.getString(R.string.PluginOperationFailed)
                    : error.getMessage());
        }
    }

    private void openPluginSource(PluginMetadata metadata) {
        try {
            File pluginFile = IrongramPluginsController.getInstance().getPluginFile(metadata);
            if (getParentActivity() == null || pluginFile == null || !pluginFile.isFile()) {
                showToast(R.string.PluginOperationFailed);
                return;
            }
            Uri uri = FileProvider.getUriForFile(
                    getParentActivity(),
                    ApplicationLoader.getApplicationId() + ".provider",
                    pluginFile
            );
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "text/x-python");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(intent);
        } catch (ActivityNotFoundException error) {
            sharePluginSource(metadata);
        } catch (Throwable error) {
            showToast(error.getMessage() == null
                    ? LocaleController.getString(R.string.PluginOperationFailed)
                    : error.getMessage());
        }
    }

    private void showToast(int stringId) {
        showToast(LocaleController.getString(stringId));
    }

    private void showToast(String message) {
        if (getParentActivity() != null && message != null) {
            Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.Plugins);
    }

    private static GradientDrawable createCardBackground(Theme.ResourcesProvider resourcesProvider) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        background.setCornerRadius(AndroidUtilities.dp(16));
        return background;
    }

    private static TextView createTextView(Context context, int size, int colorKey, boolean bold,
                                           Theme.ResourcesProvider resourcesProvider) {
        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        textView.setTextColor(Theme.getColor(colorKey, resourcesProvider));
        if (bold) {
            textView.setTypeface(AndroidUtilities.bold());
        }
        return textView;
    }

    private static class PluginSystemCellFactory extends UItem.UItemFactory<PluginSystemCell> {
        static {
            setup(new PluginSystemCellFactory());
        }

        @Override
        public PluginSystemCell createView(Context context, RecyclerListView listView, int currentAccount,
                                           int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PluginSystemCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            ((PluginSystemCell) view).bind(item.checked, (IrongramPluginsActivity) item.object);
        }

        public static UItem of(int id, boolean enabled, IrongramPluginsActivity activity) {
            UItem item = UItem.ofFactory(PluginSystemCellFactory.class);
            item.id = id;
            item.checked = enabled;
            item.object = activity;
            return item;
        }

        @Override
        public boolean equals(UItem left, UItem right) {
            return left.id == right.id;
        }

        @Override
        public boolean contentsEquals(UItem left, UItem right) {
            return left.id == right.id && left.checked == right.checked;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }

    private static class PluginCardFactory extends UItem.UItemFactory<PluginCardCell> {
        static {
            setup(new PluginCardFactory());
        }

        @Override
        public PluginCardCell createView(Context context, RecyclerListView listView, int currentAccount,
                                         int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PluginCardCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            ((PluginCardCell) view).bind(
                    (IrongramPluginsController.InstalledPlugin) item.object,
                    (IrongramPluginsActivity) item.object2,
                    item.checked
            );
        }

        public static UItem of(int id, IrongramPluginsController.InstalledPlugin plugin,
                               IrongramPluginsActivity activity, boolean pluginSystemEnabled) {
            UItem item = UItem.ofFactory(PluginCardFactory.class);
            item.id = id;
            item.object = plugin;
            item.object2 = activity;
            item.checked = pluginSystemEnabled;
            return item;
        }

        @Override
        public boolean equals(UItem left, UItem right) {
            return left.id == right.id;
        }

        @Override
        public boolean contentsEquals(UItem left, UItem right) {
            if (left.id != right.id || left.checked != right.checked) {
                return false;
            }
            Object leftObject = left.object;
            Object rightObject = right.object;
            if (!(leftObject instanceof IrongramPluginsController.InstalledPlugin)
                    || !(rightObject instanceof IrongramPluginsController.InstalledPlugin)) {
                return leftObject == rightObject;
            }
            IrongramPluginsController.InstalledPlugin a = (IrongramPluginsController.InstalledPlugin) leftObject;
            IrongramPluginsController.InstalledPlugin b = (IrongramPluginsController.InstalledPlugin) rightObject;
            PluginMetadata am = a.metadata;
            PluginMetadata bm = b.metadata;
            return a.enabled == b.enabled
                    && TextUtils.equals(a.error, b.error)
                    && TextUtils.equals(am.id, bm.id)
                    && TextUtils.equals(am.name, bm.name)
                    && TextUtils.equals(am.version, bm.version)
                    && TextUtils.equals(am.author, bm.author)
                    && TextUtils.equals(am.description, bm.description)
                    && TextUtils.equals(am.icon, bm.icon)
                    && am.requirements.equals(bm.requirements);
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }

    private static class PluginSystemCell extends FrameLayout {
        private final LinearLayout card;
        private final TextView titleView;
        private final TextView subtitleView;
        private final Switch systemSwitch;
        private IrongramPluginsActivity activity;
        private boolean resettingSwitch;

        PluginSystemCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);

            card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
            card.setBackground(createCardBackground(resourcesProvider));
            card.setMinimumHeight(AndroidUtilities.dp(76));

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
            LinearLayout textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            titleView = createTextView(context, 16, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
            subtitleView = createTextView(context, 13, Theme.key_windowBackgroundWhiteGrayText2, false, resourcesProvider);
            subtitleView.setLineSpacing(AndroidUtilities.dp(1), 1f);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            );
            subtitleParams.topMargin = AndroidUtilities.dp(4);
            textLayout.addView(titleView, new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            ));
            textLayout.addView(subtitleView, subtitleParams);
            card.addView(textLayout, textParams);

            systemSwitch = new Switch(context, resourcesProvider);
            systemSwitch.setColors(
                    Theme.key_switchTrack,
                    Theme.key_switchTrackChecked,
                    Theme.key_windowBackgroundWhite,
                    Theme.key_windowBackgroundWhite
            );
            LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(37),
                    AndroidUtilities.dp(20)
            );
            switchParams.setMarginStart(AndroidUtilities.dp(12));
            card.addView(systemSwitch, switchParams);

            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            );
            cardParams.leftMargin = AndroidUtilities.dp(16);
            cardParams.rightMargin = AndroidUtilities.dp(16);
            cardParams.topMargin = AndroidUtilities.dp(5);
            cardParams.bottomMargin = AndroidUtilities.dp(5);
            addView(card, cardParams);
        }

        void bind(boolean enabled, IrongramPluginsActivity activity) {
            this.activity = activity;
            titleView.setText(LocaleController.getString(R.string.PluginSystemToggle));
            subtitleView.setText(LocaleController.getString(R.string.PluginSystemToggleDescription));
            systemSwitch.setContentDescription(LocaleController.getString(R.string.PluginSystemToggle));
            setSystemSwitch(enabled, false);
            systemSwitch.setOnClickListener(v -> systemSwitch.setChecked(!systemSwitch.isChecked(), true));
            systemSwitch.setOnCheckedChangeListener((view, checked) -> {
                if (!resettingSwitch && this.activity != null) {
                    this.activity.setPluginSystemEnabled(checked);
                }
            });
            card.setOnClickListener(v -> systemSwitch.setChecked(!systemSwitch.isChecked(), true));
        }

        private void setSystemSwitch(boolean enabled, boolean animated) {
            resettingSwitch = true;
            systemSwitch.setChecked(enabled, animated);
            resettingSwitch = false;
        }
    }

    private static class PluginCardCell extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;
        private final LinearLayout card;
        private final TextView iconText;
        private final TextView nameView;
        private final TextView metadataView;
        private final TextView descriptionView;
        private final TextView dependenciesView;
        private final TextView errorView;
        private final Switch pluginSwitch;
        private final View shareAction;
        private final View openAction;
        private final View settingsAction;
        private final View removeAction;
        private IrongramPluginsController.InstalledPlugin plugin;
        private IrongramPluginsActivity activity;
        private boolean resettingSwitch;

        PluginCardCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setClipChildren(false);
            setClipToPadding(false);

            card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            card.setBackground(createCardBackground(resourcesProvider));

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout iconContainer = new FrameLayout(context);
            GradientDrawable iconBackground = new GradientDrawable();
            iconBackground.setColor(Theme.getColor(Theme.key_chat_inBubble, resourcesProvider));
            iconBackground.setCornerRadius(AndroidUtilities.dp(16));
            iconContainer.setBackground(iconBackground);
            iconText = createTextView(context, 27, Theme.key_windowBackgroundWhiteBlueText2, true, resourcesProvider);
            iconText.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams iconTextParams = new FrameLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT,
                    Gravity.CENTER
            );
            iconContainer.addView(iconText, iconTextParams);
            header.addView(iconContainer, new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(56),
                    AndroidUtilities.dp(56)
            ));

            LinearLayout details = new LinearLayout(context);
            details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
            detailsParams.setMarginStart(AndroidUtilities.dp(12));
            header.addView(details, detailsParams);

            nameView = createTextView(context, 17, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
            nameView.setMaxLines(2);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            details.addView(nameView, new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            ));

            metadataView = createTextView(context, 13, Theme.key_windowBackgroundWhiteGrayText2, false, resourcesProvider);
            metadataView.setMaxLines(2);
            metadataView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            );
            metadataParams.topMargin = AndroidUtilities.dp(4);
            details.addView(metadataView, metadataParams);

            pluginSwitch = new Switch(context, resourcesProvider);
            pluginSwitch.setColors(
                    Theme.key_switchTrack,
                    Theme.key_switchTrackChecked,
                    Theme.key_windowBackgroundWhite,
                    Theme.key_windowBackgroundWhite
            );
            LinearLayout.LayoutParams pluginSwitchParams = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(37),
                    AndroidUtilities.dp(20)
            );
            pluginSwitchParams.setMarginStart(AndroidUtilities.dp(12));
            header.addView(pluginSwitch, pluginSwitchParams);
            card.addView(header, new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            ));

            descriptionView = createTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText2, false, resourcesProvider);
            descriptionView.setMaxLines(4);
            descriptionView.setEllipsize(TextUtils.TruncateAt.END);
            descriptionView.setLineSpacing(AndroidUtilities.dp(1), 1f);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            );
            descriptionParams.topMargin = AndroidUtilities.dp(12);
            card.addView(descriptionView, descriptionParams);

            dependenciesView = createTextView(context, 12, Theme.key_windowBackgroundWhiteBlueText2, false, resourcesProvider);
            dependenciesView.setMaxLines(2);
            dependenciesView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dependenciesParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            );
            dependenciesParams.topMargin = AndroidUtilities.dp(8);
            card.addView(dependenciesView, dependenciesParams);

            errorView = createTextView(context, 12, Theme.key_text_RedRegular, false, resourcesProvider);
            errorView.setMaxLines(3);
            errorView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            );
            errorParams.topMargin = AndroidUtilities.dp(8);
            card.addView(errorView, errorParams);

            View divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    Math.max(1, AndroidUtilities.dp(1))
            );
            dividerParams.topMargin = AndroidUtilities.dp(10);
            card.addView(divider, dividerParams);

            LinearLayout actionRow = new LinearLayout(context);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    AndroidUtilities.dp(44)
            );
            actionRowParams.topMargin = AndroidUtilities.dp(2);
            card.addView(actionRow, actionRowParams);

            shareAction = addActionButton(context, actionRow, R.drawable.msg_share,
                    LocaleController.getString(R.string.ShareFile), false, null);
            openAction = addActionButton(context, actionRow, R.drawable.msg_openin,
                    LocaleController.getString(R.string.OpenInExternalApp), false, null);
            settingsAction = addActionButton(context, actionRow, R.drawable.msg_settings,
                    LocaleController.getString(R.string.PluginSettings), false, null);
            removeAction = addActionButton(context, actionRow, R.drawable.msg_delete,
                    LocaleController.getString(R.string.RemovePlugin), true, null);

            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT
            );
            cardParams.leftMargin = AndroidUtilities.dp(16);
            cardParams.rightMargin = AndroidUtilities.dp(16);
            cardParams.topMargin = AndroidUtilities.dp(5);
            cardParams.bottomMargin = AndroidUtilities.dp(5);
            addView(card, cardParams);
        }

        private View addActionButton(Context context, LinearLayout actionRow, int drawable,
                                     String contentDescription, boolean destructive, View.OnClickListener listener) {
            FrameLayout button = new FrameLayout(context);
            button.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector, resourcesProvider),
                    Theme.RIPPLE_MASK_CIRCLE_AUTO
            ));
            button.setContentDescription(contentDescription);
            button.setFocusable(true);

            ImageView image = new ImageView(context);
            image.setImageResource(drawable);
            image.setScaleType(ImageView.ScaleType.CENTER);
            int color = Theme.getColor(destructive
                    ? Theme.key_text_RedRegular
                    : Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
            image.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            button.addView(image, new FrameLayout.LayoutParams(
                    AndroidUtilities.dp(22),
                    AndroidUtilities.dp(22),
                    Gravity.CENTER
            ));
            actionRow.addView(button, new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(44),
                    AndroidUtilities.dp(44)
            ));
            if (listener != null) {
                button.setOnClickListener(listener);
            }
            return button;
        }

        void bind(IrongramPluginsController.InstalledPlugin plugin, IrongramPluginsActivity activity,
                  boolean pluginSystemEnabled) {
            this.plugin = plugin;
            this.activity = activity;
            PluginMetadata metadata = plugin.metadata;

            nameView.setText(metadata.name);
            StringBuilder subline = new StringBuilder();
            if (!TextUtils.isEmpty(metadata.version)) {
                subline.append(metadata.version);
            }
            if (!TextUtils.isEmpty(metadata.author)) {
                if (subline.length() > 0) {
                    subline.append(" · ");
                }
                subline.append(metadata.author);
            }
            metadataView.setText(subline);
            metadataView.setVisibility(subline.length() == 0 ? GONE : VISIBLE);

            String description = metadata.description == null ? "" : metadata.description.trim();
            descriptionView.setText(description);
            descriptionView.setVisibility(description.isEmpty() ? GONE : VISIBLE);

            if (metadata.requirements.isEmpty()) {
                dependenciesView.setVisibility(GONE);
            } else {
                dependenciesView.setText(LocaleController.formatString(
                        R.string.PluginDependencies,
                        TextUtils.join(", ", metadata.requirements)
                ));
                dependenciesView.setVisibility(VISIBLE);
            }

            if (plugin.error == null || plugin.error.isEmpty()) {
                errorView.setVisibility(GONE);
            } else {
                errorView.setText(LocaleController.getString(R.string.PluginLastError) + ": " + plugin.error);
                errorView.setVisibility(VISIBLE);
            }

            String icon = getPluginIcon(metadata);
            iconText.setText(Emoji.replaceEmoji(icon, iconText.getPaint().getFontMetricsInt(), false));
            pluginSwitch.setContentDescription(LocaleController.getString(
                    plugin.enabled ? R.string.DisablePlugin : R.string.EnablePlugin
            ));
            setPluginSwitch(plugin.enabled, false);
            pluginSwitch.setOnClickListener(v -> pluginSwitch.setChecked(!pluginSwitch.isChecked(), true));
            pluginSwitch.setOnCheckedChangeListener((view, checked) -> {
                if (resettingSwitch || this.plugin == null || this.activity == null) {
                    return;
                }
                if (checked) {
                    setPluginSwitch(false, true);
                    this.activity.confirmEnable(this.plugin.metadata);
                } else {
                    this.activity.setPluginEnabled(this.plugin.metadata, false);
                }
            });

            card.setOnClickListener(v -> {
                if (this.plugin != null && this.activity != null) {
                    this.activity.showPluginActions(this.plugin);
                }
            });
            settingsAction.setAlpha(plugin.enabled && pluginSystemEnabled ? 1f : 0.45f);
            settingsAction.setOnClickListener(v -> {
                if (this.plugin != null && this.activity != null) {
                    this.activity.openPluginSettings(this.plugin);
                }
            });
            shareAction.setOnClickListener(v -> {
                if (this.plugin != null && this.activity != null) {
                    this.activity.sharePluginSource(this.plugin.metadata);
                }
            });
            openAction.setOnClickListener(v -> {
                if (this.plugin != null && this.activity != null) {
                    this.activity.openPluginSource(this.plugin.metadata);
                }
            });
            removeAction.setOnClickListener(v -> {
                if (this.plugin != null && this.activity != null) {
                    this.activity.confirmRemove(this.plugin.metadata);
                }
            });
        }

        private void setPluginSwitch(boolean checked, boolean animated) {
            resettingSwitch = true;
            pluginSwitch.setChecked(checked, animated);
            resettingSwitch = false;
        }

        private static String getPluginIcon(PluginMetadata metadata) {
            String icon = metadata.icon == null ? "" : metadata.icon.trim();
            if (!icon.isEmpty() && icon.length() <= 8
                    && !icon.contains(":") && !icon.contains("/")
                    && !icon.matches("[0-9]+")) {
                return icon;
            }
            String name = metadata.name == null ? "" : metadata.name.trim();
            if (name.isEmpty()) {
                return "P";
            }
            int codePoint = name.codePointAt(0);
            return new String(Character.toChars(codePoint)).toUpperCase(Locale.ROOT);
        }
    }
}
