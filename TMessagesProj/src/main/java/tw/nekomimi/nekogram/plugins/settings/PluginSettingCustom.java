/*
 * Irongram plugin system.
 */

package tw.nekomimi.nekogram.plugins.settings;

import android.content.Context;
import android.view.View;

/**
 * Row with a fully custom view, provided by the plugin.
 */
public class PluginSettingCustom extends PluginSettingItem {

    public interface Provider {
        /**
         * @return view to show, must be a new instance on every call.
         */
        View createView(Context context);

        /**
         * Called every time the row is bound to the view.
         */
        default void bindView(View view) {
        }

        /**
         * @return {@code true} if the click was handled.
         */
        default boolean onClick(View view) {
            return false;
        }
    }

    /** View instance created for the current binding, it is reused by the list adapter. */
    private View pendingView;

    private final Provider provider;

    public PluginSettingCustom(String key, Provider provider) {
        super(key);
        this.provider = provider;
    }

    public Provider getProvider() {
        return provider;
    }

    public View getPendingView() {
        return pendingView;
    }

    public void setPendingView(View pendingView) {
        this.pendingView = pendingView;
    }

    @Override
    public int getType() {
        return TYPE_CUSTOM;
    }
}
