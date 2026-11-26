"""A small compatibility layer for exteraGram-style Python plugins.

The host deliberately runs plugin code inside Irongram's process. Plugins therefore have
full process privileges and should only be installed from a source the user trusts.
"""

import json
import os
import sys
import tempfile
import traceback
from enum import Enum

try:
    from java import jclass

    _Log = jclass("android.util.Log")
except Exception:
    _Log = None


class HookStrategy:
    DEFAULT = "default"
    CANCEL = "cancel"
    MODIFY = "modify"
    MODIFY_FINAL = "modify_final"


class HookResult:
    def __init__(self, strategy=HookStrategy.DEFAULT, request=None, response=None,
                 update=None, updates=None, params=None):
        self.strategy = strategy
        self.request = request
        self.response = response
        self.update = update
        self.updates = updates
        self.params = params


class AppEvent(Enum):
    START = "start"
    STOP = "stop"
    PAUSE = "pause"
    RESUME = "resume"


class MenuItemType(Enum):
    MESSAGE_CONTEXT_MENU = "message_context_menu"
    DRAWER_MENU = "drawer_menu"
    MAIN_MENU = "main_menu"
    CHAT_ACTION_MENU = "chat_action_menu"
    PROFILE_ACTION_MENU = "profile_action_menu"


class MenuItemData:
    def __init__(self, menu_type, text, on_click, item_id=None, icon=None,
                 subtext=None, condition=None, priority=0):
        self.menu_type = menu_type
        self.text = text
        self.on_click = on_click
        self.item_id = item_id
        self.icon = icon
        self.subtext = subtext
        self.condition = condition
        self.priority = priority


class _PluginLogger:
    def __init__(self, plugin):
        self._plugin = plugin

    @property
    def _tag(self):
        return "IrongramPlugin/" + (getattr(self._plugin, "_plugin_id", None) or "unknown")

    @staticmethod
    def _format(message, args):
        if not args:
            return str(message)
        try:
            return str(message) % args
        except Exception:
            return "{} {}".format(message, " ".join(map(str, args)))

    def _write(self, level, message, *args):
        text = self._format(message, args)
        if _Log is None:
            print("[{}] {}".format(self._tag, text))
            return
        getattr(_Log, level)(self._tag, text)

    def debug(self, message, *args):
        self._write("d", message, *args)

    def info(self, message, *args):
        self._write("i", message, *args)

    def warning(self, message, *args):
        self._write("w", message, *args)

    warn = warning

    def error(self, message, *args):
        self._write("e", message, *args)

    def exception(self, message, *args):
        self._write("e", "{}\n{}".format(self._format(message, args), traceback.format_exc()))

    def dump(self, value):
        self.info("%r", value)


_HOOK_UNAVAILABLE_MESSAGE = (
    "Low-level Xposed method hooks are not available in this Irongram build. "
    "Plugin features that patch Java methods cannot work; the event hooks "
    "(pre_request_hook, post_request_hook, on_update_hook, on_send_message_hook) do work."
)


class XposedHook:
    """Base class for Java method hooks, mirroring exteraGram's ``XposedHook``."""


class MethodHook(XposedHook):
    """Before/after callback pair for ``hook_method``; override either phase."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class MethodReplacement(XposedHook):
    """Replacement for a hooked method: whatever ``replace_hooked_method`` returns is used."""

    def replace_hooked_method(self, param):
        raise NotImplementedError("replace_hooked_method must be overridden")


class HookFilterData:
    """One hook filter, matching exteraGram's ``HookFilterData``."""

    def __init__(self, filter_type, arg_index=None, or_filters=None, mvel_expression=None,
                 instance_of=None, object=None):
        self.filter_type = filter_type
        self.arg_index = arg_index
        self.or_filters = or_filters
        self.mvel_expression = mvel_expression
        self.instance_of = instance_of
        self.object = object

    def to_java_filter(self):
        bridge = _hook_bridge()
        java_filter = bridge.createFilter(self.filter_type)
        if self.arg_index is not None:
            java_filter.argIndex = self.arg_index
        if self.or_filters:
            java_filter.orFilters = _java_filter_list(self.or_filters)
        if self.mvel_expression is not None:
            java_filter.mvelExpression = self.mvel_expression
        if self.instance_of is not None:
            java_filter.instanceOf = self.instance_of
        if self.object is not None:
            java_filter.object = self.object
        return java_filter

    def __repr__(self):
        return "HookFilterData({!r})".format(self.filter_type)


class HookFilter(Enum):
    """Result filters that can be attached to a hook phase, as in exteraGram."""

    RESULT_IS_NULL = "result_is_null"
    RESULT_IS_TRUE = "result_is_true"
    RESULT_IS_FALSE = "result_is_false"
    RESULT_NOT_NULL = "result_not_null"
    ARGUMENT_IS_NULL = "argument_is_null"
    ARGUMENT_IS_TRUE = "argument_is_true"
    ARGUMENT_IS_FALSE = "argument_is_false"
    ARGUMENT_NOT_NULL = "argument_not_null"

    @property
    def filter_data(self):
        return HookFilterData(self.value)

    @staticmethod
    def ResultIsInstanceOf(clazz):
        return HookFilterData("result_is_instance_of", instance_of=clazz)

    @staticmethod
    def ResultEqual(value):
        return HookFilterData("result_equal", object=value)

    @staticmethod
    def ResultNotEqual(value):
        return HookFilterData("result_not_equal", object=value)

    @staticmethod
    def ArgumentIsInstanceOf(index, clazz):
        return HookFilterData("argument_is_instance_of", arg_index=index, instance_of=clazz)

    @staticmethod
    def ArgumentEqual(index, value):
        return HookFilterData("argument_equal", arg_index=index, object=value)

    @staticmethod
    def ArgumentNotEqual(index, value):
        return HookFilterData("argument_not_equal", arg_index=index, object=value)

    @staticmethod
    def ArgumentIsNull(index):
        return HookFilterData("argument_is_null", arg_index=index)

    @staticmethod
    def ArgumentIsTrue(index):
        return HookFilterData("argument_is_true", arg_index=index)

    @staticmethod
    def ArgumentIsFalse(index):
        return HookFilterData("argument_is_false", arg_index=index)

    @staticmethod
    def ArgumentNotNull(index):
        return HookFilterData("argument_not_null", arg_index=index)

    @staticmethod
    def Condition(expression):
        return HookFilterData("condition", mvel_expression=expression)

    @staticmethod
    def Or(*filters):
        return HookFilterData("or", or_filters=list(filters))


_HOOK_BRIDGE = None


def _hook_bridge():
    """The Java side of the hook runtime (tw.nekomimi.nekogram.plugins.hooks.HookBridge)."""
    global _HOOK_BRIDGE
    if _HOOK_BRIDGE is None:
        from java import jclass
        _HOOK_BRIDGE = jclass("tw.nekomimi.nekogram.plugins.hooks.HookBridge")
    return _HOOK_BRIDGE


def hook_runtime_available():
    """True when low-level Java method hooking works in this build."""
    try:
        return bool(_hook_bridge().isAvailable())
    except Exception:
        return False


def _java_filter_list(filters):
    from java import jclass
    array_list = jclass("java.util.ArrayList")()
    for item in filters or ():
        if isinstance(item, HookFilter):
            item = item.filter_data
        if isinstance(item, HookFilterData):
            item = item.to_java_filter()
        if item is not None:
            array_list.add(item)
    return array_list


def _filters_of(callback):
    """Filters attached to a hook phase with @hook_filters / @fn_hook_filters."""
    if callback is None:
        return None
    filters = getattr(callback, "__hook_filters__", None)
    if filters:
        return filters
    source = getattr(callback, "__hook_filters_source__", None)
    if source:
        values = getattr(getattr(callback, "__self__", None), source, None)
        if values:
            return list(values)
    return None


def _phase_callback(hook, name):
    """Return a bound method if the plugin actually overrides the given hook phase."""
    method = getattr(hook, name, None)
    if not callable(method):
        return None
    base = MethodReplacement if name == "replace_hooked_method" else MethodHook
    base_method = getattr(base, name, None)
    if base_method is not None and getattr(method, "__func__", None) is base_method:
        return None
    return method


def _member_name(member):
    try:
        return str(member.getName())
    except Exception:
        return repr(member)


def _class_name(clazz):
    try:
        return str(clazz.getName())
    except Exception:
        return repr(clazz)


class PluginError(Exception):
    """Raised by the exteraGram SDK when plugin code reports an error."""

    def __init__(self, message, plugin_id=None):
        super().__init__(message)
        self.plugin_id = plugin_id


class PluginMetadata:
    """Metadata of an installed plugin, mirroring exteraGram's ``PluginMetadata`` dataclass."""

    def __init__(self, id="", name="", version="1.0", author="", description="", icon=None,
                 requirements=None, category=None):
        self.id = id
        self.name = name
        self.version = version
        self.author = author
        self.description = description
        self.icon = icon
        self.requirements = list(requirements or [])
        self.category = category

    def __repr__(self):
        return "PluginMetadata(id={!r}, name={!r}, version={!r})".format(
            self.id, self.name, self.version
        )


class IntentHookType(Enum):
    BEFORE = "before"
    AFTER = "after"


class BasePlugin:
    """Base class matching the lifecycle and event-hook surface used by exteraGram."""

    def __init__(self):
        # Attributes with the same names/semantics as exteraGram's BasePlugin, because plugins
        # read them (``self.id``, ``self.version``, ...) and the host fills them from metadata.
        self.id = ""
        self.name = ""
        self.description = ""
        self.author = ""
        self.version = ""
        self.icon = None
        self.category = None
        self.requirements = []
        self.app_version = ""
        self.sdk_version = ""
        self.error_message = None
        self.enabled = False
        self.initialized = False
        # Unhook handles of Java method hooks installed by this plugin instance.
        self._java_hooks = []
        self._plugin_id = None
        self._plugin_path = None
        self._settings_path = None
        self._runtime = None
        self.logger = _PluginLogger(self)
        self._registered_hooks = []
        self._registered_send_hook = False
        self._registered_menu_items = []
        self._apply_load_context()

    def _apply_load_context(self):
        """Adopt plugin identity/settings paths while the runtime constructs this plugin.

        Plugins commonly read settings in their ``__init__``, before the runtime had a chance to
        assign these values, so the runtime publishes them before instantiating the class.
        """
        runtime = sys.modules.get("plugin_runtime")
        context = getattr(runtime, "_LOAD_CONTEXT", None)
        if not context:
            return
        self._plugin_id = context.get("plugin_id")
        self._plugin_path = context.get("plugin_path")
        self._settings_path = context.get("settings_path")
        self._runtime = runtime
        self.logger = _PluginLogger(self)

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type):
        pass

    def pre_request_hook(self, request_name, account, request):
        return HookResult()

    def post_request_hook(self, request_name, account, response, error):
        return HookResult()

    def on_update_hook(self, update_name, account, update):
        return HookResult()

    def on_updates_hook(self, container_name, account, updates):
        return HookResult()

    def on_send_message_hook(self, account, params):
        return HookResult()

    def create_settings(self):
        return []

    def add_hook(self, name, match_substring=False, priority=0):
        if self._runtime is None:
            raise RuntimeError("Plugin runtime is not initialized")
        token = self._runtime.register_request_hook(
            self, str(name), bool(match_substring), int(priority)
        )
        self._registered_hooks.append(token)
        return token

    def add_on_send_message_hook(self, priority=0):
        if self._runtime is None:
            raise RuntimeError("Plugin runtime is not initialized")
        token = self._runtime.register_send_message_hook(self, int(priority))
        self._registered_send_hook = True
        return token

    def hook_method(self, method_or_constructor, xposed_hook=None, priority=None, *,
                    before=None, after=None, before_filters=None, after_filters=None):
        """Hook a Java method or constructor; returns an ``XC_MethodHook.Unhook`` or ``None``."""
        if xposed_hook is None and before is None and after is None:
            raise TypeError("xposed_hook or before or after cannot be None")
        bridge = _hook_bridge()
        unhook = None
        try:
            replacement = None if xposed_hook is None else _phase_callback(
                xposed_hook, "replace_hooked_method")
            if replacement is not None:
                unhook = bridge.hookReplacement(
                    method_or_constructor, self._hook_plugin_id(), replacement, priority
                )
            else:
                if before is None and after is None and xposed_hook is not None:
                    before = _phase_callback(xposed_hook, "before_hooked_method")
                    after = _phase_callback(xposed_hook, "after_hooked_method")
                if before is None and after is None:
                    raise TypeError("xposed_hook has no hook phases to install")
                if before_filters is None:
                    before_filters = _filters_of(before)
                if after_filters is None:
                    after_filters = _filters_of(after)
                unhook = bridge.hookMethod(
                    method_or_constructor,
                    self._hook_plugin_id(),
                    before,
                    after,
                    priority,
                    _java_filter_list(before_filters),
                    _java_filter_list(after_filters),
                )
        except Exception as error:
            self.log("Error while hooking {}: {}".format(_member_name(method_or_constructor), error))
            if unhook is not None:
                bridge.unhook(unhook)
            return None
        self._track_java_hook(unhook)
        return unhook

    def hook_all_methods(self, hook_class, method_name, xposed_hook=None, priority=None, *,
                         before=None, after=None, before_filters=None, after_filters=None):
        """Hook every method with the given name on a class; returns a list of unhooks."""
        if xposed_hook is None and before is None and after is None:
            raise TypeError("xposed_hook or before or after cannot be None")
        if method_name is None:
            raise TypeError("method_name cannot be None")
        bridge = _hook_bridge()
        if before is None and after is None and xposed_hook is not None:
            before = _phase_callback(xposed_hook, "before_hooked_method")
            after = _phase_callback(xposed_hook, "after_hooked_method")
        if before_filters is None:
            before_filters = _filters_of(before)
        if after_filters is None:
            after_filters = _filters_of(after)
        try:
            unhooks = bridge.hookAllMethods(
                hook_class,
                method_name,
                self._hook_plugin_id(),
                before,
                after,
                priority,
                _java_filter_list(before_filters),
                _java_filter_list(after_filters),
            )
        except Exception as error:
            self.log("Error while hooking {}.{}: {}".format(_class_name(hook_class), method_name, error))
            return None
        unhooks = list(unhooks) if unhooks else []
        for unhook in unhooks:
            self._track_java_hook(unhook)
        return unhooks or None

    def hook_all_constructors(self, hook_class, xposed_hook=None, priority=None, *,
                              before=None, after=None, before_filters=None, after_filters=None):
        """Hook every constructor of a class; returns a list of unhooks."""
        if xposed_hook is None and before is None and after is None:
            raise TypeError("xposed_hook or before or after cannot be None")
        bridge = _hook_bridge()
        if before is None and after is None and xposed_hook is not None:
            before = _phase_callback(xposed_hook, "before_hooked_method")
            after = _phase_callback(xposed_hook, "after_hooked_method")
        if before_filters is None:
            before_filters = _filters_of(before)
        if after_filters is None:
            after_filters = _filters_of(after)
        try:
            unhooks = bridge.hookAllConstructors(
                hook_class,
                self._hook_plugin_id(),
                before,
                after,
                priority,
                _java_filter_list(before_filters),
                _java_filter_list(after_filters),
            )
        except Exception as error:
            self.log("Error while hooking constructors of {}: {}".format(_class_name(hook_class), error))
            return None
        unhooks = list(unhooks) if unhooks else []
        for unhook in unhooks:
            self._track_java_hook(unhook)
        return unhooks or None

    def unhook_method(self, unhook):
        """Remove a hook installed by any of the hook_method helpers."""
        if unhook is None:
            return False
        try:
            removed = bool(_hook_bridge().unhook(unhook))
        except Exception:
            removed = False
        try:
            self._java_hooks.remove(unhook)
        except (AttributeError, ValueError):
            pass
        return removed

    def _hook_plugin_id(self):
        return self._plugin_id or self.id or ""

    def _track_java_hook(self, unhook):
        if unhook is None:
            return
        if not hasattr(self, "_java_hooks") or self._java_hooks is None:
            self._java_hooks = []
        self._java_hooks.append(unhook)

    def hook_call_site(self, *args, **kwargs):
        raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)

    def hook_field_read(self, *args, **kwargs):
        raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)

    def hook_field_write(self, *args, **kwargs):
        raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)

    def hook_string_constant(self, *args, **kwargs):
        raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)

    def hook_return_value(self, *args, **kwargs):
        raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)

    def unhook_partial(self, *args, **kwargs):
        raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)

    def unhook_all(self):
        if self._runtime is not None:
            self._runtime.unregister_plugin_hooks(self)
        self._registered_hooks.clear()
        self._registered_menu_items = []
        self._registered_send_hook = False

    def add_menu_item(self, item):
        """Register a ``MenuItemData`` entry; returns a token usable with ``remove_menu_item``."""
        if self._runtime is None:
            raise RuntimeError("Plugin runtime is not initialized")
        token = self._runtime.register_menu_item(self, item)
        self._registered_menu_items.append(token)
        return token

    def remove_menu_item(self, item_id):
        if self._runtime is None:
            return False
        removed = self._runtime.unregister_menu_item(self, item_id)
        if removed:
            self._registered_menu_items = [
                token for token in self._registered_menu_items if token != item_id
            ]
        return removed

    def get_setting(self, key, default=None):
        values = self._read_settings()
        return values.get(str(key), default)

    def set_setting(self, key, value, reload_settings=False):
        values = self._read_settings()
        values[str(key)] = value
        self._write_settings(values)

    def export_settings(self):
        return self._read_settings()

    def import_settings(self, settings, reload_settings=True):
        if not isinstance(settings, dict):
            raise TypeError("settings must be a dictionary")
        self._write_settings(settings)

    def get_plugin_dir(self):
        return os.path.dirname(self._plugin_path) if self._plugin_path else ""

    def get_data_dir(self):
        path = self._settings_path
        if not path:
            return ""
        directory = os.path.dirname(path)
        os.makedirs(directory, exist_ok=True)
        return directory

    def log(self, message, *args):
        self.logger.info(message, *args)

    def _read_settings(self):
        path = self._settings_path
        if not path or not os.path.isfile(path):
            return {}
        try:
            with open(path, "r", encoding="utf-8") as settings_file:
                value = json.load(settings_file)
            return value if isinstance(value, dict) else {}
        except Exception:
            self.logger.exception("Could not read plugin settings")
            return {}

    def _write_settings(self, values):
        path = self._settings_path
        if not path:
            raise RuntimeError("Plugin settings are unavailable before initialization")
        directory = os.path.dirname(path)
        os.makedirs(directory, exist_ok=True)
        fd, temp_path = tempfile.mkstemp(prefix="settings-", suffix=".tmp", dir=directory)
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as settings_file:
                json.dump(values, settings_file, ensure_ascii=False, sort_keys=True)
                settings_file.flush()
                os.fsync(settings_file.fileno())
            os.replace(temp_path, path)
        finally:
            if os.path.exists(temp_path):
                os.unlink(temp_path)


class BaseHook(MethodHook):
    def __init__(self, before=None, after=None):
        self.before = before
        self.after = after

    def before_hooked_method(self, param):
        if self.before is not None:
            return self.before(param)

    def after_hooked_method(self, param):
        if self.after is not None:
            return self.after(param)


def _normalize_hook_filters(filters):
    """Turn HookFilter/HookFilterData values into HookFilterData, flattening one list level."""
    if filters and len(filters) == 1 and isinstance(filters[0], (list, tuple)):
        filters = filters[0]
    normalized = []
    for item in filters or ():
        if isinstance(item, HookFilter):
            item = item.filter_data
        if isinstance(item, HookFilterData):
            normalized.append(item)
    return normalized


def hook_filters(*filters):
    """Attach filters to a hook phase function, as in exteraGram."""

    def decorate(function):
        normalized = _normalize_hook_filters(filters)
        function.__hook_filters__ = normalized
        function.__irongram_hook_filters__ = normalized
        return function

    return decorate


def fn_hook_filters(field_name):
    """Filters resolved from a plugin attribute, as in exteraGram (``fn_hook_filters``)."""

    def decorate(function):
        function.__hook_filters_source__ = field_name
        return function

    return decorate
