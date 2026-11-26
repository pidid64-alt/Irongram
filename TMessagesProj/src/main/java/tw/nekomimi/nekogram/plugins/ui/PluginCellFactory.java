/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import tw.nekomimi.nekogram.plugins.InstalledPlugin;

/**
 * Row of the plugin list: name, version / author / error as subtext and the enabled switch.
 */
public class PluginCellFactory extends UItem.UItemFactory<TextCheckCell2> {

    static {
        setup(new PluginCellFactory());
    }

    @Override
    public TextCheckCell2 createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
        return new TextCheckCell2(context, resourcesProvider);
    }

    @Override
    public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
        final TextCheckCell2 cell = (TextCheckCell2) view;
        final InstalledPlugin plugin = item.object instanceof InstalledPlugin ? (InstalledPlugin) item.object : null;
        if (plugin == null) {
            cell.setTextAndCheck("", false, divider);
            return;
        }
        cell.setTextAndValueAndCheck(plugin.getName(), getSubtext(plugin), plugin.isEnabled(), true, divider);
        cell.setEnabled(!plugin.isLoading());
    }

    private static CharSequence getSubtext(InstalledPlugin plugin) {
        final String error = plugin.getErrorText();
        if (error != null) {
            return LocaleController.formatString(R.string.PluginError, error);
        }
        if (plugin.isLoading()) {
            return LocaleController.getString(R.string.PluginLoading);
        }
        final StringBuilder builder = new StringBuilder();
        builder.append(plugin.getInfo().getVersion());
        final String author = plugin.getInfo().getAuthor();
        if (!TextUtils.isEmpty(author)) {
            builder.append(" • ").append(author);   // version • author
        }
        if (!plugin.isEnabledByUser()) {
            builder.append(" • ").append(LocaleController.getString(R.string.PluginDisabled));
        }
        return builder.toString();
    }

    public static UItem of(int id, InstalledPlugin plugin) {
        final UItem item = UItem.ofFactory(PluginCellFactory.class);
        item.id = id;
        item.object = plugin;
        return item;
    }
}
