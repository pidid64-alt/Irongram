/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hooks of the plugin API. Unlike the exteraGram engine, which hooks arbitrary JVM methods, this
 * engine exposes a curated set of extension points that are called by the app itself. This keeps
 * the client stable: a plugin can only interfere with the places that are explicitly opened for it.
 * <p>
 * All hooks must be registered from the plugin's {@code onLoad()} (main thread). They are removed
 * automatically on unload.
 */
public final class PluginsHooks {

    /**
     * Called before an outgoing message is actually prepared. The plugin may modify the params
     * (for example {@link SendMessagesHelper.SendMessageParams#message}) or cancel sending.
     *
     * @return {@code true} to cancel sending of the message.
     */
    public interface OnMessageSendHook {
        boolean onMessageSending(PluginContext context, SendMessagesHelper.SendMessageParams params, int currentAccount);
    }

    /**
     * Called on app lifecycle events: {@code app_start}, {@code app_resume}, {@code app_pause},
     * {@code app_stop} and any other event that comes from {@link PluginsController#notifyAppEvent(String)}.
     */
    public interface OnAppEventHook {
        void onAppEvent(PluginContext context, String event);
    }

    private static final class Entry<T> {
        final PluginContext context;
        final T hook;

        Entry(PluginContext context, T hook) {
            this.context = context;
            this.hook = hook;
        }
    }

    private static final CopyOnWriteArrayList<Entry<OnMessageSendHook>> messageSendHooks = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Entry<OnAppEventHook>> appEventHooks = new CopyOnWriteArrayList<>();

    private final PluginContext context;
    private final CopyOnWriteArrayList<Runnable> unsubscribers = new CopyOnWriteArrayList<>();

    PluginsHooks(PluginContext context) {
        this.context = context;
    }

    public void onMessageSending(OnMessageSendHook hook) {
        if (hook == null) {
            return;
        }
        final Entry<OnMessageSendHook> entry = new Entry<>(context, hook);
        messageSendHooks.add(entry);
        unsubscribers.add(() -> messageSendHooks.remove(entry));
    }

    public void onAppEvent(OnAppEventHook hook) {
        if (hook == null) {
            return;
        }
        final Entry<OnAppEventHook> entry = new Entry<>(context, hook);
        appEventHooks.add(entry);
        unsubscribers.add(() -> appEventHooks.remove(entry));
    }

    void clear() {
        for (Runnable unsubscriber : unsubscribers) {
            try {
                unsubscriber.run();
            } catch (Throwable ignore) {
            }
        }
        unsubscribers.clear();
    }

    /**
     * Dispatches the send message hook. Never throws: a broken plugin must not break the client.
     *
     * @return {@code true} if some plugin cancelled sending.
     */
    public static boolean dispatchMessageSending(SendMessagesHelper.SendMessageParams params, int currentAccount) {
        if (params == null || messageSendHooks.isEmpty()) {
            return false;
        }
        boolean cancelled = false;
        for (Entry<OnMessageSendHook> entry : messageSendHooks) {
            try {
                if (entry.hook.onMessageSending(entry.context, params, currentAccount)) {
                    cancelled = true;
                }
            } catch (Throwable t) {
                FileLog.e("[plugin " + entry.context.getId() + "] send message hook failed", t);
            }
        }
        return cancelled;
    }

    /**
     * Dispatches an app event to all registered hooks. Never throws.
     */
    public static void dispatchAppEvent(String event) {
        if (event == null || appEventHooks.isEmpty()) {
            return;
        }
        for (Entry<OnAppEventHook> entry : appEventHooks) {
            try {
                entry.hook.onAppEvent(entry.context, event);
            } catch (Throwable t) {
                FileLog.e("[plugin " + entry.context.getId() + "] app event hook failed", t);
            }
        }
    }


    /**
     * @return number of registered hooks, used by {@link PluginsController} to avoid useless work.
     */
    public static boolean hasAnyHooks() {
        return !messageSendHooks.isEmpty() || !appEventHooks.isEmpty();
    }

    /**
     * @return ids of the plugins that currently have at least one hook registered.
     */
    public static List<String> getHookedPluginIds() {
        final ArrayList<String> ids = new ArrayList<>();
        for (Entry<OnMessageSendHook> entry : messageSendHooks) {
            if (!ids.contains(entry.context.getId())) {
                ids.add(entry.context.getId());
            }
        }
        for (Entry<OnAppEventHook> entry : appEventHooks) {
            if (!ids.contains(entry.context.getId())) {
                ids.add(entry.context.getId());
            }
        }
        return ids;
    }
}
