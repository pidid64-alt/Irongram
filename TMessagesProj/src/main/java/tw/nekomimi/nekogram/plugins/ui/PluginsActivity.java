/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import tw.nekomimi.nekogram.plugins.InstalledPlugin;
import tw.nekomimi.nekogram.plugins.PluginsConstants;
import tw.nekomimi.nekogram.plugins.PluginsController;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;

/**
 * List of installed plugins. Tap toggles the plugin, long press opens the actions menu.
 */
public class PluginsActivity extends BaseNekoSettingsActivity {

    private static final int REQUEST_INSTALL_PLUGIN = 41_001;

    private static final int INSTALL_ROW = 2;
    private static final int SAFE_MODE_ROW = 3;

    private final PluginsController.Listener listener = this::refresh;
    private boolean listenerRegistered;

    public PluginsActivity() {
        super();
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString(R.string.Plugins);
    }

    @Override
    protected String getKey() {
        return "plugins";
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        PluginsController.getInstance().addListener(listener);
        listenerRegistered = true;
        PluginsController.getInstance().init();
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

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final PluginsController controller = PluginsController.getInstance();
        final List<InstalledPlugin> plugins = controller.getPlugins();

        items.add(UItem.asHeader(LocaleController.getString(R.string.PluginsInstalled)));
        if (plugins.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.PluginsEmpty)));
        } else {
            int cellId = PluginsConstants.ROW_ID_OFFSET;
            for (InstalledPlugin plugin : plugins) {
                items.add(PluginCellFactory.of(cellId++, plugin));
            }
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asButton(INSTALL_ROW, R.drawable.msg_add, LocaleController.getString(R.string.PluginsInstall)));

        final UItem safeMode = UItem.asSwitch(SAFE_MODE_ROW, LocaleController.getString(R.string.PluginsSafeMode));
        safeMode.checked = controller.isSafeMode();
        items.add(safeMode);

        items.add(UItem.asShadow(LocaleController.getString(R.string.PluginsSafeModeInfo)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.PluginsHint)));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        final PluginsController controller = PluginsController.getInstance();
        if (item.instanceOf(PluginCellFactory.class)) {
            final InstalledPlugin plugin = item.object instanceof InstalledPlugin ? (InstalledPlugin) item.object : null;
            if (plugin == null) {
                return;
            }
            if (plugin.hasError()) {
                showPluginOptions(plugin, view);
                return;
            }
            controller.setPluginEnabled(plugin, !plugin.isEnabledByUser());
            return;
        }
        if (item.id == INSTALL_ROW) {
            pickPluginFile();
        } else if (item.id == SAFE_MODE_ROW) {
            controller.setSafeMode(!controller.isSafeMode());
            notifyItemChanged(SAFE_MODE_ROW);
        }
    }

    @Override
    protected boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.instanceOf(PluginCellFactory.class) && item.object instanceof InstalledPlugin) {
            showPluginOptions((InstalledPlugin) item.object, view);
            return true;
        }
        return super.onItemLongClick(item, view, position, x, y);
    }

    private void showPluginOptions(InstalledPlugin plugin, View view) {
        final ItemOptions options = ItemOptions.makeOptions(this, view)
                .setScrimViewBackground(listView.getClipBackground(view));
        options.add(R.drawable.msg_settings, LocaleController.getString(R.string.PluginSettings), () -> openPluginSettings(plugin));
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.PluginReload), () -> PluginsController.getInstance().reloadPlugin(plugin));
        options.add(R.drawable.msg_delete, LocaleController.getString(R.string.Delete), () -> confirmDeletePlugin(plugin));
        options.setMinWidth(190);
        options.show();
    }

    private void openPluginSettings(InstalledPlugin plugin) {
        if (plugin.isLoaded()) {
            try {
                final BaseFragment custom = plugin.getInstance().createSettingsFragment();
                if (custom != null) {
                    presentFragment(custom);
                    return;
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        final Bundle args = new Bundle();
        args.putString(PluginSettingsActivity.EXTRA_PLUGIN_ID, plugin.getId());
        presentFragment(new PluginSettingsActivity(args));
    }

    private void confirmDeletePlugin(InstalledPlugin plugin) {
        if (getParentActivity() == null) {
            return;
        }
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.PluginDeleteTitle));
        builder.setMessage(LocaleController.formatString(R.string.PluginDeleteText, plugin.getName()));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> PluginsController.getInstance().uninstallPlugin(plugin));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    //region Installation

    private void pickPluginFile() {
        if (getParentActivity() == null) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_INSTALL_PLUGIN);
        } catch (Throwable t) {
            FileLog.e(t);
            showError(LocaleController.getString(R.string.UnknownError));
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL_PLUGIN) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        installFromUri(data.getData());
    }

    private void installFromUri(Uri uri) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        final File target = new File(PluginsController.getInstance().getIncomingDirectory(),
                "plugin-" + System.currentTimeMillis() + PluginsConstants.PLUGIN_EXTENSION);
        Utilities.globalQueue.postRunnable(() -> {
            String error = null;
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) {
                    throw new IllegalStateException("Can't open the selected file");
                }
                final byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > PluginsConstants.MAX_ARCHIVE_SIZE) {
                        throw new IllegalStateException("File is too big");
                    }
                    output.write(buffer, 0, read);
                }
            } catch (Throwable t) {
                FileLog.e(t);
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            }
            final String failure = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (failure != null) {
                    showError(failure);
                    return;
                }
                PluginsController.getInstance().installPlugin(target, (plugin, message) -> {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    if (plugin != null) {
                        BulletinFactory.of(this)
                                .createSimpleBulletin(R.drawable.msg_settings, LocaleController.formatString(R.string.PluginInstalled, plugin.getName()))
                                .show();
                    } else {
                        showError(message);
                    }
                });
            });
        });
    }

    private void showError(String message) {
        BulletinFactory.of(this)
                .createErrorBulletin(message == null || message.isEmpty() ? LocaleController.getString(R.string.UnknownError) : message)
                .show();
    }

    //endregion
}
