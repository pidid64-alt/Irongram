"""Selected Java classes from ``org.telegram.messenger`` for plugin imports."""

try:
    from java import jclass as _jclass
except Exception as error:
    raise RuntimeError(
        "org.telegram.messenger imports are available only inside Irongram's Chaquopy runtime"
    ) from error


ApplicationLoader = _jclass("org.telegram.messenger.ApplicationLoader")

__all__ = ["ApplicationLoader"]
