"""Bulletin helpers backed by Irongram's ``BulletinFactory``.

Compatible with the exteraGram ``ui.bulletin.BulletinHelper`` calls used by real
plugins: ``show_info``, ``show_success``, ``show_error``, ``show_with_button`` and
``show_copied_to_clipboard``.
"""

try:
    from java import jclass as _jclass
except Exception as error:
    _jclass = None
    _JAVA_IMPORT_ERROR = error
else:
    _JAVA_IMPORT_ERROR = None


_CLASS_CACHE = {}


def _java_class(name):
    if _jclass is None:
        raise RuntimeError(
            "ui.bulletin is only available inside Irongram's Chaquopy runtime"
        ) from _JAVA_IMPORT_ERROR
    if name not in _CLASS_CACHE:
        _CLASS_CACHE[name] = _jclass(name)
    return _CLASS_CACHE[name]


def _is_main_thread():
    try:
        looper = _java_class("android.os.Looper")
        return bool(looper.myLooper() == looper.getMainLooper())
    except Exception:
        return False


def _factory(fragment=None):
    factory_class = _java_class("org.telegram.ui.Components.BulletinFactory")
    if fragment is None:
        return getattr(factory_class, "global")()
    return factory_class.of(fragment)


def _icon(name, fallback):
    try:
        raw = _java_class("org.telegram.messenger.R$raw")
        return int(getattr(raw, name))
    except Exception:
        return int(fallback)


def _default_info_icon():
    return _icon("info", 0)


def _present(build, fragment=None):
    """Build and show a bulletin.

    When called on the UI thread the bulletin object is returned so callers can
    ``hide()`` it; otherwise the work is posted to the UI thread and ``None`` is
    returned, mirroring exteraGram's asynchronous behaviour.
    """
    from android_utils import run_on_ui_thread

    if _is_main_thread():
        return build()

    def show():
        try:
            build()
        except Exception:
            pass

    run_on_ui_thread(show)
    return None


class BulletinHelper:
    """Small subset of exteraGram's helper for standard bulletins."""

    @staticmethod
    def show_info(message, fragment=None):
        def build():
            bulletin = _factory(fragment).createSimpleBulletin(
                _default_info_icon(), str(message)
            )
            bulletin.show()
            return bulletin

        return _present(build)

    @staticmethod
    def show_error(message, fragment=None):
        def build():
            bulletin = _factory(fragment).createErrorBulletin(str(message))
            bulletin.show()
            return bulletin

        return _present(build)

    @staticmethod
    def show_success(message, fragment=None):
        def build():
            bulletin = _factory(fragment).createSuccessBulletin(str(message))
            bulletin.show()
            return bulletin

        return _present(build)

    @staticmethod
    def show_with_button(message, icon=None, button_text=None, on_button_click=None,
                         fragment=None, **kwargs):
        """Show a bulletin with an action button.

        ``on_button_click`` is called without arguments when the button is tapped.
        """
        if button_text is None:
            button_text = kwargs.get("button")
        if on_button_click is None:
            on_button_click = kwargs.get("on_click")
        raw_icon = _default_info_icon() if icon is None else int(icon)

        def build():
            factory = _factory(fragment)
            if on_button_click is None:
                bulletin = factory.createSimpleBulletin(raw_icon, str(message))
            else:
                from android_utils import R

                bulletin = factory.createSimpleBulletin(
                    raw_icon, str(message), str(button_text or ""),
                    R(lambda: on_button_click()),
                )
            bulletin.show()
            return bulletin

        return _present(build)

    @staticmethod
    def show_copied_to_clipboard(fragment=None):
        def build():
            locale_controller = _java_class("org.telegram.messenger.LocaleController")
            bulletin = _factory(fragment).createCopyBulletin(
                locale_controller.getString("TextCopied")
            )
            bulletin.show()
            return bulletin

        return _present(build)
