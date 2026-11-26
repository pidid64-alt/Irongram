"""Small, documented subset of exteraGram's ``android_utils`` module.

Only UI-thread scheduling, Java Runnable/listener proxies, legacy logging, and
clipboard copying are provided. This module does not implement Java method
hooking or any of the separate exteraGram utility modules.
"""

import traceback

try:
    from java import dynamic_proxy as _dynamic_proxy
    from java import jclass as _jclass
except Exception as _error:
    _dynamic_proxy = None
    _jclass = None
    _JAVA_IMPORT_ERROR = _error
else:
    _JAVA_IMPORT_ERROR = None

_JAVA_CLASSES = {}
_LOG_TAG = "IrongramAndroidUtils"


def _java_class(name):
    if _jclass is None:
        raise RuntimeError(
            "android_utils Android helpers are only available inside Irongram's "
            "Chaquopy runtime"
        ) from _JAVA_IMPORT_ERROR
    if name not in _JAVA_CLASSES:
        _JAVA_CLASSES[name] = _jclass(name)
    return _JAVA_CLASSES[name]


def _write_log(level, message):
    text = str(message)
    try:
        getattr(_java_class("android.util.Log"), level)(_LOG_TAG, text)
    except Exception:
        # Keep the legacy logger usable in host-side tests and preserve diagnostics
        # if Android's logging bridge is unavailable for any reason.
        print("[{}] {}".format(_LOG_TAG, text))


def _call_callback(callback, *args, default=None):
    try:
        return callback(*args)
    except Exception:
        _write_log(
            "e",
            "Android utility callback failed:\n{}".format(traceback.format_exc()),
        )
        return default


def _check_callback(callback, name):
    if not callable(callback):
        raise TypeError("{} expects a callable".format(name))


if _dynamic_proxy is not None:
    try:
        _RunnableInterface = _java_class("java.lang.Runnable")
        _ClickListenerInterface = _java_class("android.view.View$OnClickListener")
        _LongClickListenerInterface = _java_class("android.view.View$OnLongClickListener")

        class _RunnableProxy(_dynamic_proxy(_RunnableInterface)):
            def __init__(self, callback):
                super().__init__()
                self._callback = callback

            def run(self):
                _call_callback(self._callback)

        class _ClickListenerProxy(_dynamic_proxy(_ClickListenerInterface)):
            def __init__(self, callback):
                super().__init__()
                self._callback = callback

            def onClick(self, view):
                _call_callback(self._callback, view)

        class _LongClickListenerProxy(_dynamic_proxy(_LongClickListenerInterface)):
            def __init__(self, callback):
                super().__init__()
                self._callback = callback

            def onLongClick(self, view):
                return bool(_call_callback(self._callback, view, default=False))

    except Exception as error:
        _JAVA_IMPORT_ERROR = error
        _RunnableProxy = None
        _ClickListenerProxy = None
        _LongClickListenerProxy = None
else:
    _RunnableProxy = None
    _ClickListenerProxy = None
    _LongClickListenerProxy = None


def _require_proxy(proxy_class, api_name):
    if proxy_class is None:
        raise RuntimeError(
            "{} requires Chaquopy Java interface proxies in Irongram"
            .format(api_name)
        ) from _JAVA_IMPORT_ERROR
    return proxy_class


def R(fn):
    """Return a Java ``Runnable`` which invokes ``fn``."""
    _check_callback(fn, "R")
    return _require_proxy(_RunnableProxy, "R")(fn)


def OnClickListener(fn):
    """Return a ``View.OnClickListener`` which passes the clicked view to ``fn``."""
    _check_callback(fn, "OnClickListener")
    return _require_proxy(_ClickListenerProxy, "OnClickListener")(fn)


def OnLongClickListener(fn):
    """Return a ``View.OnLongClickListener``; the callback result is converted to bool."""
    _check_callback(fn, "OnLongClickListener")
    return _require_proxy(_LongClickListenerProxy, "OnLongClickListener")(fn)


def run_on_ui_thread(func, delay=0):
    """Schedule ``func`` on Irongram's UI thread after ``delay`` milliseconds."""
    _check_callback(func, "run_on_ui_thread")
    try:
        delay_ms = int(delay)
    except (TypeError, ValueError, OverflowError):
        raise TypeError("delay must be an integer number of milliseconds")
    if delay_ms < 0:
        delay_ms = 0

    _java_class("org.telegram.messenger.AndroidUtilities").runOnUIThread(
        R(func), delay_ms
    )


def log(data):
    """Legacy debug logger; new plugins should use ``BasePlugin.logger``."""
    try:
        message = str(data)
    except Exception:
        message = "<{}: could not format value>".format(type(data).__name__)
    _write_log("d", message)


def _show_copy_bulletin():
    try:
        locale_controller = _java_class("org.telegram.messenger.LocaleController")
        bulletin_factory = _java_class("org.telegram.ui.Components.BulletinFactory")
        copied_message = locale_controller.getString("TextCopied")
        factory = getattr(bulletin_factory, "global")()
        factory.createCopyBulletin(copied_message).show()
    except Exception:
        _write_log(
            "e",
            "Could not show clipboard bulletin:\n{}".format(traceback.format_exc()),
        )


def copy_to_clipboard(text):
    """Copy text and show Irongram's standard copied bulletin on success."""
    try:
        copied = bool(
            _java_class("org.telegram.messenger.AndroidUtilities")
            .addToClipboard(str(text))
        )
    except Exception:
        _write_log(
            "e",
            "Could not copy text to clipboard:\n{}".format(traceback.format_exc()),
        )
        return False

    if copied:
        try:
            run_on_ui_thread(_show_copy_bulletin)
        except Exception:
            # Clipboard access has already succeeded; a missing bulletin should not
            # turn that successful operation into a reported copy failure.
            _write_log(
                "e",
                "Could not schedule clipboard bulletin:\n{}".format(traceback.format_exc()),
            )
    return copied
