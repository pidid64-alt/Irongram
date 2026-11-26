package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import tw.nekomimi.nekogram.plugins.IrongramPluginsController;
import tw.nekomimi.nekogram.plugins.PluginMetadata;

public final class IrongramPluginInstallActivity extends BaseNekoSettingsActivity {
    private final int pluginInfoRow = rowId++;
    private final int authorRow = rowId++;
    private final int requirementsRow = rowId++;
    private final int descriptionRow = rowId++;
    private final int warningRow = rowId++;
    private final int installRow = rowId++;

    private final String pluginFilePath;
    private PluginMetadata metadata;
    private String loadError;
    private boolean loading = true;
    private boolean installing;

    public IrongramPluginInstallActivity(File pluginFile) {
        super();
        pluginFilePath = pluginFile == null ? "" : pluginFile.getAbsolutePath();
    }

    public static boolean isPluginFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".plugin");
    }

    public static boolean isPluginFile(File file) {
        return file != null && isPluginFile(file.getName());
    }

    public static boolean open(File pluginFile, String fileName, Activity activity, BaseFragment parentFragment) {
        if ((!isPluginFile(fileName) && !isPluginFile(pluginFile)) || pluginFile == null || !pluginFile.isFile()) {
            return false;
        }

        BaseFragment parent = parentFragment != null
                ? parentFragment
                : LaunchActivity.getLastFragmentIncludeMainTabs();
        if (parent == null) {
            parent = LaunchActivity.getSafeLastFragment();
        }
        if (parent != null && parent.presentFragment(new IrongramPluginInstallActivity(pluginFile))) {
            return true;
        }
        if (activity instanceof LaunchActivity) {
            ((LaunchActivity) activity).presentFragment(new IrongramPluginInstallActivity(pluginFile));
            return true;
        }
        return false;
    }

    @Override
    public boolean onFragmentCreate() {
        boolean created = super.onFragmentCreate();
        if (created) {
            inspectPlugin();
        }
        return created;
    }

    private void inspectPlugin() {
        IrongramPluginsController.getInstance().inspectPlugin(new File(pluginFilePath), (result, error) -> {
            metadata = result;
            loadError = error;
            loading = false;
            if (actionBar != null) {
                actionBar.setTitle(metadata == null
                        ? LocaleController.getString(R.string.PluginPreviewTitle)
                        : metadata.name);
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (loading) {
            items.add(TextDetailSettingsCellFactory.of(
                    pluginInfoRow,
                    LocaleController.getString(R.string.PluginPreviewTitle),
                    LocaleController.getString(R.string.PluginPreviewLoading)
            ).setEnabled(false));
            return;
        }
        if (metadata == null) {
            items.add(TextDetailSettingsCellFactory.of(
                    pluginInfoRow,
                    LocaleController.getString(R.string.PluginPreviewLoadFailed),
                    TextUtils.isEmpty(loadError) ? "" : loadError
            ).setEnabled(false));
            return;
        }

        items.add(TextDetailSettingsCellFactory.of(
                pluginInfoRow,
                metadata.name,
                LocaleController.formatString(R.string.PluginVersion, metadata.version)
        ).setEnabled(false));
        if (!TextUtils.isEmpty(metadata.author)) {
            items.add(TextDetailSettingsCellFactory.of(
                    authorRow,
                    LocaleController.getString(R.string.PluginAuthorLabel),
                    metadata.author
            ).setEnabled(false));
        }
        if (!metadata.requirements.isEmpty()) {
            items.add(TextDetailSettingsCellFactory.of(
                    requirementsRow,
                    LocaleController.getString(R.string.PluginDependenciesLabel),
                    TextUtils.join(", ", metadata.requirements)
            ).setEnabled(false));
        }
        if (!TextUtils.isEmpty(metadata.description)) {
            items.add(TextDetailSettingsCellFactory.of(
                    descriptionRow,
                    LocaleController.getString(R.string.PluginDescription),
                    metadata.description
            ).setEnabled(false));
        }
        items.add(UItem.asShadow(null));
        items.add(TextDetailSettingsCellFactory.of(
                warningRow,
                LocaleController.getString(R.string.PluginPreviewTrustTitle),
                LocaleController.getString(R.string.PluginPreviewTrust)
        ).setEnabled(false));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(
                installRow,
                R.drawable.msg_add,
                LocaleController.getString(installing
                        ? R.string.PluginInstallInProgress
                        : R.string.PluginInstallAction)
        ).setEnabled(!installing));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == installRow && !installing && metadata != null) {
            installPlugin();
        }
    }

    private void installPlugin() {
        installing = true;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        IrongramPluginsController.getInstance().importPlugin(new File(pluginFilePath), (installed, error) -> {
            installing = false;
            if (installed != null) {
                if (getParentActivity() != null) {
                    Toast.makeText(
                            getParentActivity(),
                            LocaleController.formatString(R.string.PluginInstalled, installed.name),
                            Toast.LENGTH_LONG
                    ).show();
                }
                finishFragment();
            } else {
                String message = TextUtils.isEmpty(error)
                        ? LocaleController.getString(R.string.PluginInstallFailed)
                        : error;
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), message, Toast.LENGTH_LONG).show();
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }
        });
    }

    @Override
    protected String getActionBarTitle() {
        return metadata == null
                ? LocaleController.getString(R.string.PluginPreviewTitle)
                : metadata.name;
    }
}
