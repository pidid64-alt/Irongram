"""Basic bulletin helpers backed by Irongram's BulletinFactory."""

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


def _show_bulletin(method_name, message, fragment=None):
    def show():
        factory_class = _java_class("org.telegram.ui.Components.BulletinFactory")
        if fragment is None:
            factory = getattr(factory_class, "global")()
        else:
            factory = factory_class.of(fragment)
        getattr(factory, method_name)(str(message)).show()

    from android_utils import run_on_ui_thread

    run_on_ui_thread(show)


class BulletinHelper:
    """Small subset of exteraGram's helper for standard bulletins."""

    @staticmethod
    def show_info(message, fragment=None):
        def show():
            factory_class = _java_class("org.telegram.ui.Components.BulletinFactory")
            if fragment is None:
                factory = getattr(factory_class, "global")()
            else:
                factory = factory_class.of(fragment)
            info_animation = _java_class("org.telegram.messenger.R$raw").info
            factory.createSimpleBulletin(info_animation, str(message)).show()

        from android_utils import run_on_ui_thread

        run_on_ui_thread(show)

    @staticmethod
    def show_error(message, fragment=None):
        _show_bulletin("createErrorBulletin", message, fragment)

    @staticmethod
    def show_success(message, fragment=None):
        _show_bulletin("createSuccessBulletin", message, fragment)
