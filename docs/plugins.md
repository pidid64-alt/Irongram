# Irongram Python plugins (compatibility layer in progress)

Irongram can import a single Python plugin from Settings → Plugins, either as `.py` or as the
exteraGram-style `.plugin` file, and files copied by hand into the plugins directory are listed
under either extension. Tapping a `.plugin` attachment in a chat opens a metadata preview
and an install action. These are plain Python source files with a different extension, not `.elyx`
archives. Imported files start **disabled**. Enabling a plugin executes its Python code inside the
Irongram process; install only code and dependencies you trust. Plugins are not sandboxed.

The runtime is Python 3.11 via Chaquopy. The app minimum Android version is API 24 because
that is the minimum supported by this Python runtime.

## Vendored exteraGram SDK

Parts of exteraGram's own plugin SDK (`exteraSquad/exteraGram`, `plugins-pysdk/`) are vendored
verbatim into `TMessagesProj/src/main/python/`, so plugins see the same metadata parser, version
checks, and helper modules as they do on exteraGram:

- `_sdk_version.py` — the SDK bootstrap (`SafeModeImporter`, `check_safemode`, `__start__`).
  Vendored unmodified along with `markdown_utils.py`, `extera_utils/__init__.py`,
  `extera_utils/metadata_parser.py`, `extera_utils/text_formatting.py` and
  `extera_utils/get_caller.py`; every file keeps its upstream copyright header.
- `plugin_runtime.read_plugin_metadata(path)` runs `extera_utils.metadata_parser` over a plugin
  file with Python's `ast` module; the plugin source is never executed during import or preview.
  Settings → Plugins and the chat `.plugin` install preview both use it, so the metadata and error
  messages are the same as on exteraGram.
- The vendored code is GPLv3-or-later with exteraGram's additional terms; the terms are kept in
  `TMessagesProj/src/main/python/EXTERAGRAM_TERMS.md`. The Plugins screen carries the attribution
  they require.

## Metadata and example

A plugin needs literal top-level `__id__` and `__name__` values, exactly as on exteraGram:
`__id__` must match `^[a-zA-Z][a-zA-Z0-9_-]{1,31}$`, `__name__` must be non-empty. Also read are
`__version__` (defaults to `1.0`), `__author__`, `__description__`, `__icon__`, `__category__`,
`__requirements__` (a list, or a comma-separated string), `__min_version__` / `__app_version__`
(for example `">=11.12.1"`, compared against the app version) and `__sdk_version__` (compared
against the bundled SDK version, currently `1.4.6.5`). Metadata errors are reported verbatim, for
example `Plugin requires app version >=11.12.1, but current is 13.0.2`:

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

- Install, enable, disable, remove, and restore enabled `.py` and `.plugin` plugins at app
  startup.
- `on_plugin_load` / `on_plugin_unload`, per-plugin JSON settings (`get_setting`, `set_setting`,
  `export_settings`, `import_settings`), and Logcat logger methods. Settings are already readable
  and writable inside a plugin's `__init__`, which several real plugins rely on.
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
  plugin is unloaded or disabled. `CHAT_ACTION_MENU` entries are appended to the chat three-dot
  menu (icons are resolved by drawable name, falling back to `msg_list`); the click handler receives
  `{"dialog_id": ..., "account": ..., "user_id"|"chat_id": ...}`. Menu entries are added when the
  chat screen is created, so a newly registered item shows up after reopening the chat.
  `MESSAGE_CONTEXT_MENU`, `DRAWER_MENU`, `MAIN_MENU`, and `PROFILE_ACTION_MENU` entries are
  registered but not rendered yet.
- exteraGram helper modules bundled with the app:
  - `hook_utils`: exteraGram's own module — `find_class`, `get_method`, `get_constructor`,
    `get_field`, `get_private_field`, `set_private_field`, `get_static_private_field`,
    `set_static_private_field`, `java_class` (with `find_method`/`find_constructor`/`find_field`
    aliases), plus the `MethodHook` / `MethodReplacement` / `HookFilter` names re-exported from
    `base_plugin`.
  - `ui.alert.AlertDialogBuilder`: `set_title`, `set_message`, `set_view`, `set_items`,
    `set_positive_button`, `set_negative_button`, `set_neutral_button`, `make_button_red`,
    `create`, `show`; button callbacks receive `(dialog, which)`.
  - `ui.bulletin.BulletinHelper`: `show_info`, `show_success`, `show_error`, `show_with_button`,
    `show_copied_to_clipboard`.
  - `file_utils`: `get_cache_dir`, `get_files_dir`, `get_external_files_dir`,
    `get_external_cache_dir`, `get_plugins_dir` (all return absolute paths as `str`) plus
    `read_file(path)` and `write_file(path, data, append=False)`.
  - `markdown_utils`: exteraGram's own module, re-exported unchanged — `parse_markdown(text)`
    returning `ParsedMessage(text, entities)` where each `RawEntity` carries a `TLEntityType`
    (`BOLD`, `ITALIC`, `UNDERLINE`, `STRIKE`, `SPOILER`, `CODE`, `PRE`, `TEXT_LINK`, ...) and
    converts to a `TLRPC.TL_messageEntity*` object with `to_tlrpc_object()`; also `count_chars_until`
    and `to_utf16_len`. Offsets and lengths are UTF-16 units, as Telegram expects.
  - `client_utils`: `get_send_messages_helper`, `get_file_loader`, `get_last_fragment`,
    `get_user_config`, `get_account_instance`, `get_messages_controller`,
    `get_connections_manager`, `get_contacts_controller`, `get_notifications_controller`,
    `get_media_data_controller`, `get_queue_by_name`, `run_on_queue`, `send_request`,
    `RequestCallback`, `send_message`, `send_document`. `send_message` accepts a dictionary with
    `peer` (required), `message`, `caption`, `path`, `entities`, `replyToMsg`, `replyToTopMsg`,
    `searchLinks`, `silent`, and `webPage`; `send_document(peer, path, caption=None)` uploads a
    file as a document. Sends still pass through `on_send_message_hook`.
- `Custom` declarations (including existing `UItem`, Android `View`, and
  `SimpleSettingFactory` values) are shown as unsupported rows; custom Java/Android view factories
  are not executed. `EditText` supports multiline input and `max_length`; custom input masks and
  custom settings long-click callbacks are not implemented.

## Java method hooking

Plugins can patch Java methods exactly as on exteraGram:

```python
from base_plugin import BasePlugin, MethodHook, MethodReplacement, HookFilter
from hook_utils import find_class, get_method

class Plugin(BasePlugin):
    def on_plugin_load(self):
        controller = find_class("org.telegram.messenger.MessagesController")
        method = get_method(controller, "isOnline")
        self.hook_method(method, self.only_before)          # before/after callbacks
        self.hook_all_methods(find_class("org.telegram.ui.ChatActivity"),
                              "onBackPressedPressed", self.block_all)  # every overload
        self.hook_method(method, MethodReplacement(...))     # full replacement
```

`hook_method`, `hook_all_methods`, `hook_all_constructors` and `unhook_method` accept
`MethodHook`/`MethodReplacement` objects or `before=`/`after=` callables, plus `priority=` and
`before_filters=`/`after_filters=` (also as a `@hook_filters(...)` decorator, including
`HookFilter.Condition("...")` MVEL expressions). Callbacks receive the real
`XC_MethodHook.MethodHookParam`, so `param.thisObject`, `param.args[i] = value`,
`param.getResult()`/`param.setResult()` and `param.setThrowable()` behave as in Xposed. Hooks are
removed automatically when the plugin is disabled or unloaded, and they are skipped while the
plugin is inactive.

Under the hood this uses exteraHook — the same ART hook runtime exteraGram ships: `hook-core`
(the `de.robv.android.xposed` compatibility layer) plus its LSPlant fork, both vendored in
`Tools/maven` and linked into `libtmessages.49.so`. Two consequences:

- Hooking is available on **arm64-v8a and armeabi-v7a** only; the vendored runtime ships no x86
  native libraries, so on x86/x86_64 the client still runs but `hook_method` reports that hooking
  is unavailable.
- A hook that crashes inside a plugin is caught and logged, but a plugin that hooks core client
  code can still destabilise the process. Enable hooking plugins one at a time.

## Not yet compatible

This is **not full AyuGram/exteraGram compatibility yet**. In particular:

- Partial hooks (`hook_call_site`, `hook_field_read`, `hook_field_write`,
  `hook_string_constant`, `hook_return_value`, `unhook_partial`) are not implemented and raise
  `NotImplementedError`; plain `hook_method` / `hook_all_methods` / `hook_all_constructors` are.
- Only the chat three-dot menu renders plugin entries; message context menus, the drawer, and the
  profile menu do not show plugin entries yet.
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
- `.elyx` / `.eaf` structured plugin formats are not implemented; `.plugin` and `.py` files are
  plain Python source and are treated identically.
- exteraGram's SDK refuses to run plugins when its own build counter is too low and raises
  "App version is too old" through `SafeModeImporter`. Irongram has no such counter, so
  `plugin_runtime.start_sdk()` calls `__start__()` and then clears that gate. Compatibility is
  still enforced by `__app_version__` / `__sdk_version__` metadata against the real app version.

`python tools/plugin_sdk_check/check_sdk.py --plugin-path <file>` loads a plugin through
`plugin_runtime.load_plugin` against a stubbed Chaquopy environment (`tools/plugin_sdk_check/`); the
`Plugin API check` workflow runs it on every change to the SDK. It loads the bundled SDK contract
checks plus these plugins, pinned to commits known to load:

| Plugin | File | Kind |
| --- | --- | --- |
| `0x11DFE/Auto-Forwarder-Plugin` | `auto_forwarder.py` | `.py`, 19 settings, 1 chat menu entry |
| `mr-Vestr/plugins` | `Plugin creator/plugin_creator.py` | `.py`, 14 settings |
| `mr-Vestr/plugins` | `Templates/templates.py` | `.py`, 13 settings |
| `mr-Vestr/plugins` | `ADB Lite/adb_lite.plugin` | `.plugin`, 14 settings |
| `py-sato/Privacy-Cleaner` | `privacy_cleaner.plugin` | `.plugin`, 17 settings, 1 chat menu entry |
| `Th3Nekit/Th3ProxyTools` | `Th3ProxyTools.plugin` | `.plugin`, 15 settings |
| `n08i40k/tg-streaks` | `tg-streaks.py` | `.py` |

Those runs exercise imports, plugin construction, `create_settings()`, and menu registration only —
not on-device behaviour. Plugins that patch Java methods (`AlexeiCrystal/extera-plugins`,
`nonFeature/LiteGram`, `repinek/LegacyGram`) stop at the `NotImplementedError` described above. Whole-client mods such as
`nonFeature/LiteGram` and `repinek/LegacyGram` are not compatible: they are Python packages that
expect Java method hooking (`hook_method`) and their own DEX utilities.

The compatibility boundary is also shown in the Plugins screen. Do not describe this build as
fully compatible until these APIs are implemented and exercised on Android devices.
