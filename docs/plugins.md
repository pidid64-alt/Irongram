# Irongram Python plugins (compatibility layer in progress)

Irongram can import a single Python plugin from Settings → Plugins, either as `.py` or as the
exteraGram-style `.plugin` file. Tapping a `.plugin` attachment in a chat opens a metadata preview
and an install action. These are plain Python source files with a different extension, not `.elyx`
archives. Imported files start **disabled**. Enabling a plugin executes its Python code inside the
Irongram process; install only code and dependencies you trust. Plugins are not sandboxed.

The runtime is Python 3.11 via Chaquopy. The app minimum Android version is API 24 because
that is the minimum supported by this Python runtime.

## Metadata and example

A plugin needs literal top-level `__id__` and `__name__` values. `__version__`, `__author__`,
`__description__`, and `__requirements__` are also read:

```python
__id__ = "example_rewriter"
__name__ = "Example rewriter"
__version__ = "1.0.0"
__author__ = "Example"
__description__ = "Rewrites an outgoing message prefix."
__requirements__ = []

from base_plugin import BasePlugin, HookResult, HookStrategy


class ExampleRewriter(BasePlugin):
    def on_plugin_load(self):
        self.add_on_send_message_hook(priority=0)

    def on_plugin_unload(self):
        self.logger.info("Plugin stopped")

    def on_send_message_hook(self, account, params):
        if params.message and params.message.startswith(".hello"):
            params.message = params.message.replace(".hello", "Hello from Irongram", 1)
            return HookResult(strategy=HookStrategy.MODIFY, params=params)
        return HookResult()
```

## Implemented API surface

- Install, enable, disable, remove, and restore enabled `.py` plugins at app startup.
- `on_plugin_load` / `on_plugin_unload`, per-plugin JSON settings (`get_setting`, `set_setting`,
  `export_settings`, `import_settings`), and Logcat logger methods.
- Event hooks registered by `add_hook(name, match_substring=False, priority=0)`:
  - `pre_request_hook` and `post_request_hook` for Telegram RPC request classes.
  - `on_update_hook` for individual update classes and `on_updates_hook` for update containers.
  - `HookResult` supports default, cancel, modify, and modify-final outcomes. Objects passed by the
    app should normally be mutated in place.
- `add_on_send_message_hook(priority=0)` and `on_send_message_hook` for outgoing message params.
- The app bundles `requests` for common exteraGram-style plugins. Other `__requirements__` are
  downloaded from PyPI; the installer verifies wheel SHA-256 and accepts only pure-Python wheels
  (`none-any`). Native extensions, direct URLs, and optional dependency extras are not supported.
  Requirements share one app-wide library directory, so incompatible versions can prevent a
  plugin from enabling. Network access is required the first time a dependency is installed.
- For enabled plugins, `create_settings()` is rendered in a per-plugin settings screen for the
  built-in `Header`, `Divider`, `Switch`, `Selector`, `Input`, `EditText`, and `Text` declarations.
  Switch, selector, and text input values are stored through `BasePlugin.set_setting()` and
  `on_change` callbacks are invoked after saving. `Text.on_click` is invoked on tap, and
  `Text.create_sub_fragment` opens a nested settings page.
- `Custom` declarations (including existing `UItem`, Android `View`, and
  `SimpleSettingFactory` values) are shown as unsupported rows; custom Java/Android view factories
  are not executed. `EditText` supports multiline input and `max_length`; custom input masks and
  custom settings long-click callbacks are not implemented.

## Not yet compatible

This is **not full AyuGram/exteraGram compatibility yet**. In particular:

- Arbitrary Xposed-style Java method hooking (`hook_method`, `MethodHook`, `MethodReplacement`) is
  not implemented. Calling `BasePlugin.hook_method` raises `NotImplementedError`.
- A narrow subset of `android_utils` is implemented in
  `TMessagesProj/src/main/python/android_utils.py`: `R`, click/long-click listener wrappers,
  UI-thread scheduling, basic legacy string logging, and clipboard copying. This is not full
  exteraGram utility compatibility.
- The current `client_utils` subset provides `get_send_messages_helper`, `get_last_fragment`,
  and `run_on_queue`; `ui.bulletin.BulletinHelper` provides error and success bulletins. Other
  client, dialog, bulletin, and reflection APIs are not implemented.
- Plugin menu injection, custom Android-view settings factories, and application lifecycle events
  are not implemented. The supported built-in settings controls are a limited subset of the
  exteraGram settings API.
- `.elyx` / `.eaf` structured plugin formats and app/SDK version constraints are not implemented.

The compatibility boundary is also shown in the Plugins screen. Do not describe this build as
fully compatible until these APIs are implemented and exercised on Android devices.
