"""exteraGram compatible ``AlertDialogBuilder`` on top of Irongram's AlertDialog.

The class mirrors the small builder used by exteraGram plugins::

    builder = AlertDialogBuilder(context)
    builder.set_title("Title")
    builder.set_message("Text")
    builder.set_positive_button("OK", lambda dialog, which: dialog.dismiss())
    builder.show()

Callback arguments are ``(dialog, which)`` for every button.
"""

try:
    from java import dynamic_proxy as _dynamic_proxy
    from java import jclass as _jclass
except Exception as _error:
    _dynamic_proxy = None
    _jclass = None
    _JAVA_IMPORT_ERROR = _error
else:
    _JAVA_IMPORT_ERROR = None

_CLASS_CACHE = {}


def _java_class(name):
    if _jclass is None:
        raise RuntimeError(
            "ui.alert is only available inside Irongram's Chaquopy runtime"
        ) from _JAVA_IMPORT_ERROR
    if name not in _CLASS_CACHE:
        _CLASS_CACHE[name] = _jclass(name)
    return _CLASS_CACHE[name]


if _dynamic_proxy is not None:
    try:
        _ClickListener = _dynamic_proxy(
            _java_class("android.content.DialogInterface$OnClickListener")
        )

        class _DialogClickListener(_ClickListener):
            def __init__(self, callback):
                super().__init__()
                self._callback = callback

            def onClick(self, dialog, which):
                self._callback(dialog, which)

    except Exception as error:
        _JAVA_IMPORT_ERROR = error
        _DialogClickListener = None
else:
    _DialogClickListener = None


def _current_context():
    """Return an Activity suitable for creating dialogs."""
    try:
        fragment = _java_class("org.telegram.ui.LaunchActivity").getLastFragment()
        activity = fragment.getParentActivity() if fragment is not None else None
        if activity is not None:
            return activity
    except Exception:
        pass
    return _java_class("org.telegram.messenger.ApplicationLoader").applicationContext


def _listener(callback):
    if callback is None:
        return None
    if not callable(callback):
        return callback
    if _DialogClickListener is None:
        raise RuntimeError(
            "ui.alert needs Chaquopy Java interface proxies in Irongram"
        ) from _JAVA_IMPORT_ERROR
    return _DialogClickListener(callback)


def _java_text(text):
    if text is None:
        return None
    if isinstance(text, str):
        return text
    return str(text)


class AlertDialogBuilder:
    """Subset of exteraGram's ``AlertDialogBuilder`` that wraps ``AlertDialog.Builder``."""

    BUTTON_POSITIVE = -1
    BUTTON_NEGATIVE = -2
    BUTTON_NEUTRAL = -3

    def __init__(self, context=None, resources_provider=None):
        self._context = context if context is not None else None
        builder_class = _java_class("org.telegram.ui.ActionBar.AlertDialog$Builder")
        if resources_provider is not None:
            self._builder = builder_class(
                self._context or _current_context(), resources_provider
            )
        else:
            self._builder = builder_class(self._context or _current_context())
        self._red_buttons = set()
        self._listeners = []
        self._dialog = None

    # -- content -----------------------------------------------------------
    def set_title(self, text):
        try:
            self._builder.setTitle(_java_text(text))
        except Exception:
            self._builder.setTitle(_java_text(text), False)
        return self

    def set_message(self, text):
        self._builder.setMessage(_java_text(text))
        return self

    def set_view(self, view, width=None, height=None):
        self._builder.setView(view)
        return self

    def set_items(self, items, callback=None):
        self._builder.setItems(list(items), _listener(callback))
        return self

    def set_cancelable(self, cancelable):
        self._builder.setCancelable(bool(cancelable))
        return self

    # -- buttons -----------------------------------------------------------
    def _set_button(self, method_name, text, callback):
        listener = _listener(callback)
        self._listeners.append(listener)
        getattr(self._builder, method_name)(_java_text(text), listener)
        return self

    def set_positive_button(self, text, callback=None):
        return self._set_button("setPositiveButton", text, callback)

    def set_negative_button(self, text, callback=None):
        return self._set_button("setNegativeButton", text, callback)

    def set_neutral_button(self, text, callback=None):
        return self._set_button("setNeutralButton", text, callback)

    def make_button_red(self, button):
        """Mark a button as destructive (follows the current theme's red color)."""
        try:
            button = int(button)
        except (TypeError, ValueError):
            button = self.BUTTON_POSITIVE
        self._red_buttons.add(button)
        return self

    # -- lifecycle ---------------------------------------------------------
    def _apply_red_buttons(self, dialog):
        if dialog is None or not self._red_buttons:
            return
        try:
            theme = _java_class("org.telegram.ui.ActionBar.Theme")
            color = theme.getColor(getattr(theme, "key_text_RedRegular"))
        except Exception:
            return
        for button in self._red_buttons:
            try:
                dialog.getButton(button).setTextColor(color)
            except Exception:
                pass

    def create(self):
        dialog = self._builder.create()
        self._dialog = dialog
        self._apply_red_buttons(dialog)
        return dialog

    def show(self):
        dialog = self._builder.show()
        self._dialog = dialog
        self._apply_red_buttons(dialog)
        return dialog
