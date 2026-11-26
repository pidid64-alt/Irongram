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
- The app bundles `requests` for common exteraGram-style plugins. All plugins share an app-private
  Python library directory. Declared `__requirements__` are installed from PyPI the first time a
  plugin is enabled; during startup, a missing import can also trigger installation of its matching
  PyPI project (up to eight auto-detected imports per plugin). Use `__requirements__` to pin versions
  or when a PyPI distribution name differs from its import name. Installed packages are
  reused by every plugin, so adding a pure-Python dependency does not require rebuilding Irongram.
  The installer resolves transitive requirements, verifies wheel SHA-256, and accepts only
  pure-Python `none-any` wheels. Native extensions, direct URLs, and optional dependency extras are
  not supported. Network access is required the first time a dependency is installed; incompatible
  versions can still prevent a plugin from enabling. Java namespaces such as `org.telegram` are not
  Python packages and must use `java.jclass` or a supported Irongram helper instead.
- For enabled plugins, `create_settings()` is rendered in a per-plugin settings screen for the
  built-in `Header`, `Divider`, `Switch`, `Selector`, `Input`, `EditText`, and `Text` declarations.
  Switch, selector, and text input values are stored through `BasePlugin.set_setting()` and
  `on_change` callbacks are invoked after saving. `Text.on_click` is invoked on tap, and
  `Text.create_sub_fragment` opens a nested settings page.
- Plugin menu items: `add_menu_item(MenuItemData(menu_type=..., text=..., icon=..., on_click=...))`
  accepts the exteraGram `MenuItemType` values (`CHAT_ACTION_MENU`, `MESSAGE_CONTEXT_MENU`,
  `DRAWER_MENU`, `MAIN_MENU`, `PROFILE_ACTION_MENU`) and returns a token for
  `remove_menu_item(token)`. The click handler receives a context dictionary (for example
  `{"dialog_id": ...}`). Items are kept in sync with the plugin lifecycle and removed when the
  plugin is unloaded or disabled. **The client UI does not render these items yet** — registration
  succeeds so plugins load, but no menu entry is shown.
- exteraGram helper modules bundled with the app:
  - `hook_utils`: `find_class`, `find_method`, `get_private_field`, `set_private_field` (reflection
    helpers) plus the `MethodHook` / `MethodReplacement` / `HookFilter` names re-exported from
    `base_plugin`. `hook_method`, `unhook_method`, and `hook_all_methods` raise
    `NotImplementedError`.
  - `ui.alert.AlertDialogBuilder`: `set_title`, `set_message`, `set_view`, `set_items`,
    `set_positive_button`, `set_negative_button`, `set_neutral_button`, `make_button_red`,
    `create`, `show`; button callbacks receive `(dialog, which)`.
  - `ui.bulletin.BulletinHelper`: `show_info`, `show_success`, `show_error`, `show_with_button`,
    `show_copied_to_clipboard`.
  - `file_utils`: `get_cache_dir`, `get_files_dir`, `get_external_files_dir`,
    `get_external_cache_dir` (all return absolute paths as `str`).
  - `markdown_utils`: `parse_markdown(text)` returning `ParsedMarkdown(text, entities)` where each
    entity converts to a `TLRPC.TL_messageEntity*` object with `to_tlrpc_object()`; supports bold,
    italic, underline, strike, spoiler, inline code, fenced code, `[text](url)` links, and `\`
    escapes.
  - `client_utils`: `get_send_messages_helper`, `get_file_loader`, `get_last_fragment`,
    `get_user_config`, `get_account_instance`, `get_messages_controller`,
    `get_connections_manager`, `get_contacts_controller`, `get_notifications_controller`,
    `get_media_data_controller`, `get_queue_by_name`, `run_on_queue`, `send_request`,
    `RequestCallback`, `send_message`.
- `Custom` declarations (including existing `UItem`, Android `View`, and
  `SimpleSettingFactory` values) are shown as unsupported rows; custom Java/Android view factories
  are not executed. `EditText` supports multiline input and `max_length`; custom input masks and
  custom settings long-click callbacks are not implemented.

## Not yet compatible

This is **not full AyuGram/exteraGram compatibility yet**. In particular:

- Arbitrary Xposed-style Java method hooking (`hook_method`, `MethodHook`, `MethodReplacement`) is
  not implemented. Calling `BasePlugin.hook_method` raises `NotImplementedError`.
- Plugin menu items are registered but not displayed anywhere in the client yet (see above).
- A narrow subset of `android_utils` is implemented in
  `TMessagesProj/src/main/python/android_utils.py`: `R`, click/long-click listener wrappers,
  UI-thread scheduling, basic legacy string logging, and clipboard copying. This is not full
  exteraGram utility compatibility.
- `client_utils`, `android_utils`, `ui.bulletin`, and `hook_utils` cover the members listed above
  only. Anything else (for example `dialogs_utils`, `locale_utils`, `xposed_utils`,
  `dex_utils`, or arbitrary Android helpers) is not bundled.
- For the QR Generator sample, direct imports of `org.telegram.messenger.ApplicationLoader` and
  `org.telegram.ui.ActionBar.AlertDialog` are mapped to those Java classes. This is a narrow bridge,
  not general Java-package or method-hook compatibility; use `java.jclass` for other Java classes.
- Rendered plugin menus, custom Android-view settings factories, and application lifecycle events
  are not implemented. The supported built-in settings controls are a limited subset of the
  exteraGram settings API.
- `.elyx` / `.eaf` structured plugin formats and app/SDK version constraints are not implemented.

Host-side checks (`plugin_runtime.load_plugin` against a stubbed Chaquopy environment) currently load
`0x11DFE/Auto-Forwarder-Plugin` (`auto_forwarder.py`), `mr-Vestr/plugins` (`plugin_creator.py`,
`templates.py`), and `py-sato/Privacy-Cleaner` (`privacy_cleaner.py`). Those runs exercise imports,
plugin construction, `create_settings()`, and menu registration only — not on-device behaviour.

The compatibility boundary is also shown in the Plugins screen. Do not describe this build as
fully compatible until these APIs are implemented and exercised on Android devices.
