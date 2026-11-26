"""Selected Java classes from ``org.telegram.ui.ActionBar`` for plugin imports."""

try:
    from java import jclass as _jclass
except Exception as error:
    raise RuntimeError(
        "org.telegram.ui.ActionBar imports are available only inside Irongram's Chaquopy runtime"
    ) from error


AlertDialog = _jclass("org.telegram.ui.ActionBar.AlertDialog")

__all__ = ["AlertDialog"]
