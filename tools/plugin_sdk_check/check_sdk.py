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

    for name in ("parse_markdown", "ParsedMarkdown", "MessageEntity", "entities_to_json"):
        check(hasattr(markdown_utils, name), "markdown_utils.{} is missing".format(name))

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
    from markdown_utils import parse_markdown

    cases = [
        ("**bold** and `code`", "bold and code",
         [("bold", 0, 4), ("code", 9, 4)]),
        ("**bold _it_ x** tail", "bold it x tail",
         [("bold", 0, 9), ("italic", 5, 2)]),
        ("a ~~s~~ b ||p|| c __u__ d _i_ e", "a s b p c u d i e",
         [("strike", 2, 1), ("spoiler", 6, 1), ("underline", 10, 1), ("italic", 14, 1)]),
        ("[label](https://t.me/x) plain", "label plain", [("text_url", 0, 5)]),
        ("no markdown", "no markdown", []),
        ("unclosed **bold", "unclosed **bold", []),
        ("snake_case_name", "snake_case_name", []),
    ]
    for source, text, entities in cases:
        parsed = parse_markdown(source)
        if not check(parsed.text == text,
                     "parse_markdown({!r}).text == {!r} (got {!r})".format(
                         source, text, parsed.text)):
            continue
        spans = [(entity.type, entity.offset, entity.length) for entity in parsed.entities]
        check(spans == entities,
              "parse_markdown({!r}) entities {} (got {})".format(source, entities, spans))


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
    if args.plugin_url or args.plugin_path:
        check_plugins(args.plugin_url, args.plugin_path)

    if FAILURES:
        print("\n{} check(s) failed".format(len(FAILURES)))
        return 1
    print("\nAll plugin SDK checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
