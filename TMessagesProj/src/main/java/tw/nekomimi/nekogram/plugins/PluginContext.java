/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins;

import android.content.Context;
import android.os.Looper;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;

/**
 * Per-plugin facade: preferences, hooks, logging, threading and notification observers.
 */
public final class PluginContext {

    private static final class ObserverRecord {
        final int account;
        final NotificationCenter.NotificationCenterDelegate delegate;
        final int[] notificationIds;

        ObserverRecord(int account, NotificationCenter.NotificationCenterDelegate delegate, int[] notificationIds) {
            this.account = account;
            this.delegate = delegate;
            this.notificationIds = notificationIds;
        }
    }

    private final InstalledPlugin plugin;
    private final PluginPreferences preferences;
    private final PluginsHooks hooks;
    private final ArrayList<ObserverRecord> observers = new ArrayList<>();

    PluginContext(InstalledPlugin plugin) {
        this.plugin = plugin;
        this.preferences = PluginsController.getInstance().getPluginPreferences(plugin);
        this.hooks = new PluginsHooks(this);
    }

    public InstalledPlugin getPlugin() {
        return plugin;
    }

    public PluginInfo getInfo() {
        return plugin.getInfo();
    }

    public String getId() {
        return plugin.getId();
    }

    public String getName() {
        return plugin.getInfo().getName();
    }

    public String getVersion() {
        return plugin.getInfo().getVersion();
    }

    public Context getApplicationContext() {
        return ApplicationLoader.applicationContext;
    }

    public File getPluginDirectory() {
        return plugin.getDirectory();
    }

    public PluginPreferences getPreferences() {
        return preferences;
    }

    public PluginsHooks getHooks() {
        return hooks;
    }

    public int getAppVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    public String getAppVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    public void runOnUiThread(Runnable runnable) {
        AndroidUtilities.runOnUIThread(runnable);
    }

    public void runOnUiThread(Runnable runnable, long delay) {
        AndroidUtilities.runOnUIThread(runnable, delay);
    }

    public void runOnBackgroundThread(Runnable runnable) {
        Utilities.globalQueue.postRunnable(runnable);
    }

    public void log(String message) {
        FileLog.d("[plugin " + getId() + "] " + message);
    }

    public void log(String message, Throwable throwable) {
        FileLog.e("[plugin " + getId() + "] " + message, throwable);
    }

    public void toast(CharSequence text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
        });
    }

    public NotificationCenter getNotificationCenter(int account) {
        return NotificationCenter.getInstance(account);
    }

    /**
     * Subscribes the plugin to the given notifications of the given account. All subscriptions are
     * removed automatically when the plugin is unloaded, so there is no need to unsubscribe.
     * Must be called from the main thread.
     */
    public void observe(int account, NotificationCenter.NotificationCenterDelegate delegate, int... notificationIds) {
        if (delegate == null || notificationIds == null) {
            return;
        }
        observers.add(new ObserverRecord(account, delegate, notificationIds));
        final NotificationCenter center = NotificationCenter.getInstance(account);
        for (int id : notificationIds) {
            center.addObserver(delegate, id);
        }
    }

    void destroy() {
        for (ObserverRecord record : observers) {
            try {
                final NotificationCenter center = NotificationCenter.getInstance(record.account);
                for (int id : record.notificationIds) {
                    center.removeObserver(record.delegate, id);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        observers.clear();
        hooks.clear();
    }

    boolean isMainThread() {
        return Looper.getMainLooper() != null && Thread.currentThread() == Looper.getMainLooper().getThread();
    }
}
