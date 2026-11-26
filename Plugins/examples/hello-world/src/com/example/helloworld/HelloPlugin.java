package com.example.helloworld;

import android.text.TextUtils;

import java.util.Arrays;
import java.util.List;

import tw.nekomimi.nekogram.plugins.BasePlugin;
import tw.nekomimi.nekogram.plugins.PluginContext;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingDivider;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingHeader;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingInput;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingItem;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingSelector;
import tw.nekomimi.nekogram.plugins.settings.PluginSettingSwitch;

/**
 * Minimal plugin that demonstrates the whole API surface:
 * lifecycle, declarative settings, preferences, hooks and app events.
 */
public class HelloPlugin extends BasePlugin {

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SUFFIX = "suffix";
    private static final String KEY_MODE = "mode";

    @Override
    public void onLoad() {
        final PluginContext context = getContext();
        context.log("onLoad");
        context.toast("Hello world plugin loaded");

        context.getHooks().onMessageSending((ctx, params, account) -> {
            if (!ctx.getPreferences().getBoolean(KEY_ENABLED, false)) {
                return false;
            }
            if (params.message == null || params.message.isEmpty()) {
                return false;
            }
            final String suffix = ctx.getPreferences().getString(KEY_SUFFIX, "");
            if (TextUtils.isEmpty(suffix)) {
                return false;
            }
            final boolean prepend = "prepend".equals(ctx.getPreferences().getString(KEY_MODE, "append"));
            params.message = prepend ? suffix + params.message : params.message + suffix;
            return false;
        });

        context.getHooks().onAppEvent((ctx, event) -> ctx.log("app event: " + event));
    }

    @Override
    public void onUnload() {
        getContext().log("onUnload");
    }

    @Override
    public List<PluginSettingItem> createSettings() {
        return Arrays.asList(
                new PluginSettingHeader("Outgoing messages"),
                new PluginSettingSwitch(KEY_ENABLED, "Append a suffix to outgoing messages", false),
                new PluginSettingInput(KEY_SUFFIX, "Suffix", "e.g. \" 🐾\"", ""),
                new PluginSettingSelector(KEY_MODE, "Position",
                        new CharSequence[]{"Append", "Prepend"},
                        new String[]{"append", "prepend"}, 0),
                new PluginSettingDivider("The hook runs right before the message is sent. " +
                        "Works only for text messages.")
        );
    }

    @Override
    public void onSettingChanged(String key) {
        getContext().log("setting changed: " + key);
    }
}
