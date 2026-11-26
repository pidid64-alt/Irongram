package tw.nekomimi.nekogram.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ChatActivity;

import java.util.HashMap;

/**
 * Renders menu entries registered by Python plugins in the client UI.
 *
 * <p>Plugins register entries through {@code BasePlugin.add_menu_item}. The Python runtime owns the
 * registry; this class only asks it which entries apply to the screen being shown and forwards
 * clicks back with a context dictionary.</p>
 */
public final class IrongramPluginMenu {

    /** Menu id base offset, chosen to stay clear of Telegram's own chat menu ids. */
    private static final int ITEM_ID_BASE = 0x51F000;

    private IrongramPluginMenu() {
    }

    /**
     * Append the plugin entries of {@code chat_action_menu} to the chat's three-dot menu.
     *
     * <p>Failures are logged and ignored: a broken plugin must never break the chat menu.</p>
     */
    public static void addChatActionMenuItems(ChatActivity fragment, ActionBarMenuItem headerItem) {
        if (fragment == null || headerItem == null) {
            return;
        }
        try {
            IrongramPluginsController controller = IrongramPluginsController.getInstance();
            if (controller == null || !controller.isPluginSystemEnabled()) {
                return;
            }
            long dialogId = fragment.getDialogId();
            HashMap<String, Object> context = new HashMap<>();
            context.put("dialog_id", dialogId);
            context.put("account", fragment.getCurrentAccount());
            if (dialogId > 0) {
                context.put("user_id", dialogId);
            } else {
                context.put("chat_id", -dialogId);
            }
            for (IrongramPluginsController.PluginMenuItem item
                    : controller.getMenuItems("chat_action_menu", context)) {
                ActionBarMenuItem.Item menuItem = headerItem.lazilyAddSubItem(
                        ITEM_ID_BASE + item.token,
                        iconResource(fragment, item.icon),
                        item.text
                );
                if (menuItem == null) {
                    continue;
                }
                menuItem.setOnClickListener(view -> {
                    try {
                        IrongramPluginsController.getInstance().triggerMenuItem(item.token, context);
                    } catch (Throwable error) {
                        FileLog.e(error);
                    }
                });
            }
        } catch (Throwable error) {
            FileLog.e(error);
        }
    }

    private static int iconResource(ChatActivity fragment, String icon) {
        if (icon != null && !icon.isEmpty()) {
            try {
                int resource = fragment.getResources().getIdentifier(
                        icon, "drawable", fragment.getContext().getPackageName());
                if (resource != 0) {
                    return resource;
                }
            } catch (Throwable error) {
                FileLog.e(error);
            }
        }
        return R.drawable.msg_list;
    }
}
