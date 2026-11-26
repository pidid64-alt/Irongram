"""Subset of exteraGram's ``file_utils`` module.

The helpers return absolute paths as plain ``str`` values, so plugins can use them
with ``os.path.join`` and other standard library calls.
"""

import os

try:
    from java import jclass as _jclass
except Exception as _error:
    _jclass = None
    _JAVA_IMPORT_ERROR = _error
else:
    _JAVA_IMPORT_ERROR = None

_CLASS_CACHE = {}


def _java_class(name):
    if _jclass is None:
        raise RuntimeError(
            "file_utils is only available inside Irongram's Chaquopy runtime"
        ) from _JAVA_IMPORT_ERROR
    if name not in _CLASS_CACHE:
        _CLASS_CACHE[name] = _jclass(name)
    return _CLASS_CACHE[name]


def _app_context():
    return _java_class("org.telegram.messenger.ApplicationLoader").applicationContext


def _absolute_path(java_file):
    if java_file is None:
        return ""
    return str(java_file.getAbsolutePath())


def get_cache_dir():
    """Return the app cache directory (created on demand)."""
    context = _app_context()
    cache_dir = context.getCacheDir()
    if cache_dir is not None and not cache_dir.exists():
        cache_dir.mkdirs()
    return _absolute_path(cache_dir)


def get_files_dir():
    """Return the app private files directory."""
    return _absolute_path(_app_context().getFilesDir())


def get_external_files_dir():
    """Return the app specific external files directory, or the internal one as a fallback."""
    context = _app_context()
    external = None
    try:
        external = context.getExternalFilesDir(None)
    except Exception:
        external = None
    if external is None:
        return get_files_dir()
    return _absolute_path(external)


def get_external_cache_dir():
    """Return the external cache directory, or the internal cache directory as a fallback."""
    context = _app_context()
    external = None
    try:
        external = context.getExternalCacheDir()
    except Exception:
        external = None
    if external is None:
        return get_cache_dir()
    return _absolute_path(external)


def get_plugins_dir():
    """Return the directory where Irongram stores installed plugin files."""
    directory = _java_class("java.io.File")(get_files_dir(), "irongram/plugins")
    if not directory.exists():
        directory.mkdirs()
    return _absolute_path(directory)


def read_file(path):
    """Return the contents of ``path`` as text, or an empty string on failure."""
    try:
        with open(str(path), "r", encoding="utf-8", errors="replace") as handle:
            return handle.read()
    except Exception:
        return ""


def write_file(path, data, append=False):
    """Write ``data`` to ``path`` (creating parent directories) and return True on success."""
    try:
        target = str(path)
        parent = os.path.dirname(target)
        if parent:
            os.makedirs(parent, exist_ok=True)
        with open(target, "a" if append else "w", encoding="utf-8") as handle:
            handle.write(data if isinstance(data, str) else str(data))
        return True
    except Exception:
        return False
