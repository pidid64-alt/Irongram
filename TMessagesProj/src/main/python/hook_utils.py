"""Reflection helpers from exteraGram's ``hook_utils`` module.

Only the reflection part of the original module is implemented: ``find_class``,
``get_private_field`` and ``set_private_field``. Java method hooking
(``hook_method``, ``hook_all_methods``) is not available in Irongram, because the
client does not ship an ART hooking runtime; those helpers raise
``NotImplementedError`` instead of silently doing nothing.
"""

try:
    from java import jclass as _jclass
except Exception as _error:
    _jclass = None
    _JAVA_IMPORT_ERROR = _error
else:
    _JAVA_IMPORT_ERROR = None

from base_plugin import (  # noqa: F401  (re-exported for compatibility)
    BaseHook,
    HookFilter,
    MethodHook,
    MethodReplacement,
    hook_filters,
)

_CLASS_CACHE = {}

_HOOK_UNAVAILABLE_MESSAGE = (
    "Java method hooking (hook_utils.hook_method) requires an ART hooking runtime, "
    "which Irongram does not ship. Use the event hooks of BasePlugin instead: "
    "add_hook(), add_on_send_message_hook()."
)


def _require_java():
    if _jclass is None:
        raise RuntimeError(
            "hook_utils reflection helpers are only available inside Irongram's "
            "Chaquopy runtime"
        ) from _JAVA_IMPORT_ERROR


def find_class(name):
    """Return the Java class with the given binary name, e.g. ``org.telegram.ui.LaunchActivity``."""
    _require_java()
    name = str(name)
    if name not in _CLASS_CACHE:
        _CLASS_CACHE[name] = _jclass(name)
    return _CLASS_CACHE[name]


def _is_class(target):
    """True when ``target`` is a Java class object rather than an instance."""
    try:
        from java import jclass as java_class_type
    except Exception:
        java_class_type = None
    if java_class_type is not None and isinstance(target, java_class_type):
        return True
    # Chaquopy exposes Java classes as instances of a metaclass-like "JavaClass".
    return type(target).__name__ in ("JavaClass", "jclass", "JavaClassType")


def _resolve_field(target, name):
    if target is None:
        raise TypeError("get_private_field() requires a class or an instance")
    try:
        holder = target if _is_class(target) else target.getClass()
    except Exception:
        holder = target
    clazz = holder
    while clazz is not None:
        try:
            field = clazz.getDeclaredField(str(name))
            field.setAccessible(True)
            return field
        except Exception:
            try:
                clazz = clazz.getSuperclass()
            except Exception:
                clazz = None
    raise AttributeError("No field '{}' in {}".format(name, target))


def get_private_field(target, name, default=None):
    """Return the value of a (possibly private) Java field of a class or an instance."""
    try:
        field = _resolve_field(target, name)
    except AttributeError:
        return default
    try:
        return field.get(None if _is_class(target) else target)
    except Exception:
        return default


def set_private_field(target, name, value):
    """Assign a (possibly private) Java field of a class or an instance. Returns True on success."""
    try:
        field = _resolve_field(target, name)
        field.set(None if _is_class(target) else target, value)
        return True
    except Exception:
        return False


def find_method(target, name, *parameter_types):
    """Return a ``java.lang.reflect.Method`` declared by ``target`` or any of its superclasses."""
    _require_java()
    try:
        holder = target if _is_class(target) else target.getClass()
    except Exception:
        holder = target
    clazz = holder
    while clazz is not None:
        try:
            method = clazz.getDeclaredMethod(str(name), *parameter_types)
            method.setAccessible(True)
            return method
        except Exception:
            try:
                clazz = clazz.getSuperclass()
            except Exception:
                clazz = None
    raise AttributeError("No method '{}' in {}".format(name, target))


def hook_method(*args, **kwargs):
    raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)


def unhook_method(*args, **kwargs):
    raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)


def hook_all_methods(*args, **kwargs):
    raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)


def unhook_all_methods(*args, **kwargs):
    raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)


def hook_constructors(*args, **kwargs):
    raise NotImplementedError(_HOOK_UNAVAILABLE_MESSAGE)
