#!/usr/bin/env python3
"""Host-side checks for Irongram's exteraGram-compatible Python plugin SDK.

Run from the repository root:

    python tools/plugin_sdk_check/check_sdk.py
    python tools/plugin_sdk_check/check_sdk.py --plugin-url https://.../plugin.py

The script imports every bundled SDK module against a stubbed Java environment,
asserts the documented API surface, exercises the markdown parser, and optionally
loads real plugin sources through ``plugin_runtime.load_plugin``.
"""

import argparse
import os
import sys
import tempfile
import traceback
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
SDK_DIR = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "python")

sys.path.insert(0, HERE)
sys.path.insert(0, SDK_DIR)

import java_stub  # noqa: E402

java_stub.install()

FAILURES = []


def check(condition, message):
    if condition:
        return True
    FAILURES.append(message)
    print("FAIL " + message)
    return False


def check_sdk_modules():
    import android_utils
    import base_plugin
    import client_utils
    import file_utils
    import hook_utils
    import markdown_utils
    import plugin_runtime
    from ui import alert, bulletin, settings

    print("Imported SDK modules")

    for name in (
        "BasePlugin", "HookResult", "HookStrategy", "AppEvent", "MenuItemData",
        "MenuItemType", "MethodHook", "MethodReplacement", "HookFilter", "hook_filters",
    ):
        check(hasattr(base_plugin, name), "base_plugin.{} is missing".format(name))

    for name in (
        "register_menu_item", "unregister_menu_item", "get_menu_items", "trigger_menu_item",
        "load_plugin", "unload_plugin", "dispatch_pre_request", "dispatch_post_request",
        "dispatch_update", "dispatch_updates", "dispatch_send_message",
    ):
        check(hasattr(plugin_runtime, name), "plugin_runtime.{} is missing".format(name))

    for name in (
        "get_last_fragment", "get_send_messages_helper", "get_file_loader",
        "get_account_instance", "get_messages_controller", "get_connections_manager",
        "get_user_config", "get_queue_by_name", "run_on_queue", "send_request",
        "RequestCallback", "send_message", "send_document",
    ):
        check(hasattr(client_utils, name), "client_utils.{} is missing".format(name))

    for name in ("R", "OnClickListener", "OnLongClickListener", "run_on_ui_thread", "log",
                 "copy_to_clipboard"):
        check(hasattr(android_utils, name), "android_utils.{} is missing".format(name))

    for name in ("find_class", "find_method", "get_private_field", "set_private_field",
                 "MethodHook", "MethodReplacement"):
        check(hasattr(hook_utils, name), "hook_utils.{} is missing".format(name))

    for name in ("get_cache_dir", "get_files_dir", "get_external_files_dir",
                 "get_plugins_dir", "read_file", "write_file"):
        check(hasattr(file_utils, name), "file_utils.{} is missing".format(name))

    for name in ("parse_markdown", "ParsedMessage", "RawEntity", "TLEntityType",
                 "count_chars_until", "to_utf16_len"):
        check(hasattr(markdown_utils, name), "markdown_utils.{} is missing".format(name))

    import extera_utils
    from extera_utils import metadata_parser, text_formatting
    check(hasattr(extera_utils, "__name__"), "extera_utils package is missing")
    check(hasattr(metadata_parser, "get_metadata"), "extera_utils.metadata_parser.get_metadata is missing")
    for name in ("parse_text", "Markdown", "HTML"):
        check(hasattr(text_formatting, name),
              "extera_utils.text_formatting.{} is missing".format(name))

    for name in ("show_info", "show_success", "show_error", "show_with_button",
                 "show_copied_to_clipboard"):
        check(hasattr(bulletin.BulletinHelper, name),
              "ui.bulletin.BulletinHelper.{} is missing".format(name))

    builder = alert.AlertDialogBuilder
    check(builder is not None, "ui.alert.AlertDialogBuilder is missing")
    check(getattr(builder, "BUTTON_POSITIVE", None) == -1,
          "AlertDialogBuilder.BUTTON_POSITIVE must be -1")
    for name in ("set_title", "set_message", "set_view", "set_positive_button",
                 "set_negative_button", "set_neutral_button", "make_button_red", "create",
                 "show"):
        check(callable(getattr(builder, name, None)),
              "AlertDialogBuilder.{} is missing".format(name))

    for name in ("Header", "Divider", "Switch", "Selector", "Input", "EditText", "Text"):
        check(hasattr(settings, name), "ui.settings.{} is missing".format(name))


def check_markdown():
    from markdown_utils import TLEntityType, parse_markdown, to_utf16_len

    parsed = parse_markdown("**bold** and `code`")
    check(parsed.text == "bold and code",
          "parse_markdown('**bold** and `code`').text == 'bold and code' (got {!r})".format(parsed.text))
    spans = [(entity.type, entity.offset, entity.length) for entity in parsed.entities]
    check((TLEntityType.CODE, 9, 4) in spans,
          "parse_markdown should mark inline code at 9:13 (got {})".format(spans))
    check(parsed.entities[0].to_tlrpc_object() is not None,
          "RawEntity.to_tlrpc_object() should return a TLRPC entity")

    link = parse_markdown("[label](https://t.me/x)")
    check(link.text == "label", "markdown link text should be 'label' (got {!r})".format(link.text))
    check(link.entities and link.entities[0].type is TLEntityType.TEXT_LINK,
          "markdown link should produce a TEXT_LINK entity")
    check(link.entities and link.entities[0].url == "https://t.me/x",
          "markdown link should carry its url")

    check(to_utf16_len("\U0001F600") == 2, "to_utf16_len should count surrogate pairs")


def check_metadata():
    import os
    import tempfile

    import plugin_runtime

    workdir = tempfile.mkdtemp(prefix="irongram-metadata-")

    def parse(name, source):
        path = os.path.join(workdir, name)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(source)
        return plugin_runtime.read_plugin_metadata(path)

    ok = parse("good.plugin",
               '__id__ = "test_plugin"\n'
               '__name__ = "Test plugin"\n'
               '__version__ = "1.2.3"\n'
               '__author__ = "Tester"\n'
               '__description__ = "Smoke test"\n'
               '__requirements__ = ["requests>=2.0", "packaging"]\n')
    check(ok.get("error") is None, "valid .plugin metadata rejected: {}".format(ok.get("error")))
    check(ok.get("id") == "test_plugin", "metadata id mismatch: {}".format(ok.get("id")))
    check(ok.get("version") == "1.2.3", "metadata version mismatch: {}".format(ok.get("version")))
    check(ok.get("requirements") == ["requests>=2.0", "packaging"],
          "requirements mismatch: {}".format(ok.get("requirements")))

    bad_id = parse("bad_id.plugin", '__id__ = "1nope"\n__name__ = "Bad"\n')
    check(bad_id.get("error"), "invalid __id__ should be rejected")

    no_name = parse("no_name.plugin", '__id__ = "no_name"\n')
    check(no_name.get("error"), "missing __name__ should be rejected")

    too_new = parse("too_new.plugin",
                    '__id__ = "too_new"\n__name__ = "Too new"\n__app_version__ = ">=99.0.0"\n')
    check(too_new.get("error"), "unsatisfied __app_version__ should be rejected")

    probe = os.path.join(workdir, "meta_probe.plugin")
    with open(probe, "w", encoding="utf-8") as handle:
        handle.write(
            '__id__ = "meta_probe"\n'
            '__name__ = "Meta probe"\n'
            '__version__ = "2.5.0"\n'
            '__author__ = "Tester"\n'
            'from base_plugin import BasePlugin\n'
            'class Probe(BasePlugin):\n'
            '    pass\n'
        )
    import plugin_runtime
    plugin_runtime.load_plugin("meta_probe", probe, workdir)
    instance = plugin_runtime._PLUGINS["meta_probe"]["plugin"]
    check(getattr(instance, "id", None) == "meta_probe",
          "loaded .plugin should expose self.id (got {!r})".format(getattr(instance, "id", None)))
    check(getattr(instance, "name", None) == "Meta probe",
          "loaded .plugin should expose self.name (got {!r})".format(getattr(instance, "name", None)))
    check(getattr(instance, "version", None) == "2.5.0",
          "loaded .plugin should expose self.version (got {!r})".format(getattr(instance, "version", None)))
    check(getattr(instance, "initialized", False) is True,
          "loaded .plugin should be marked initialized")
    plugin_runtime.unload_plugin("meta_probe")
    check(getattr(instance, "initialized", True) is False,
          "unloaded .plugin should clear initialized")

    sdk_gate = parse("sdk.plugin",
                     '__id__ = "sdk_gate"\n__name__ = "SDK gate"\n'
                     '__sdk_version__ = ">=1.0.0"\n')
    check(sdk_gate.get("error") is None,
          "satisfied __sdk_version__ rejected: {}".format(sdk_gate.get("error")))


def load_plugin(plugin_id, path, workdir):
    import plugin_runtime

    settings_path = os.path.join(workdir, plugin_id + ".json")
    plugin_runtime.load_plugin(plugin_id, path, settings_path)
    plugin = plugin_runtime._PLUGINS[plugin_id]["plugin"]
    settings_list = plugin.create_settings()
    check(isinstance(settings_list, list), "{}: create_settings() must return a list".format(plugin_id))
    menu_items = plugin_runtime.get_menu_items("chat_action_menu")
    print("Loaded {}: {} settings entries, {} chat menu entries".format(
        plugin_id, len(settings_list), len(menu_items)))
    if menu_items:
        first = menu_items[0]
        check(plugin_runtime.trigger_menu_item(first["token"], {"dialog_id": -1001}) in (True, False),
              "trigger_menu_item should return a bool")
    plugin_runtime.unload_plugin(plugin_id)


def _download(url, target):
    print("Downloading " + url)
    with urllib.request.urlopen(url, timeout=60) as response:
        data = response.read()
    with open(target, "wb") as handle:
        handle.write(data)


def check_plugins(urls, paths):
    workdir = tempfile.mkdtemp(prefix="irongram-plugins-")
    jobs = []
    for index, url in enumerate(urls):
        name = url.rstrip("/").split("/")[-1] or "plugin_{}.py".format(index)
        target = os.path.join(workdir, name)
        try:
            _download(url, target)
        except Exception as error:
            # Network flakiness or an upstream change must not turn the SDK check red; the
            # bundled SDK assertions above already ran.
            print("WARNING could not download {}: {}".format(url, error))
            continue
        jobs.append((name, target))
    for path in paths:
        jobs.append((os.path.basename(path), os.path.abspath(path)))
    for name, target in jobs:
        plugin_id = os.path.basename(name).rsplit(".", 1)[0].replace("-", "_") or "plugin"
        try:
            load_plugin(plugin_id, target, workdir)
        except Exception:
            check(False, "{} failed to load:\n{}".format(name, traceback.format_exc()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--plugin-url", action="append", default=[],
                        help="URL of a plugin .py file to load through plugin_runtime")
    parser.add_argument("--plugin-path", action="append", default=[],
                        help="local path of a plugin .py file to load through plugin_runtime")
    args = parser.parse_args()

    check_sdk_modules()
    check_markdown()
    check_metadata()
    if args.plugin_url or args.plugin_path:
        check_plugins(args.plugin_url, args.plugin_path)

    if FAILURES:
        print("\n{} check(s) failed".format(len(FAILURES)))
        return 1
    print("\nAll plugin SDK checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
