"""Small exteraGram ``client_utils`` subset used by current Irongram plugins.

This module is intentionally limited to the queue, fragment, and send-helper accessors
currently used by the included compatibility examples. It is not the full exteraGram API.
"""

import threading

try:
    from java import jclass as _jclass
except Exception as error:
    _jclass = None
    _JAVA_IMPORT_ERROR = error
else:
    _JAVA_IMPORT_ERROR = None


STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"

_CLASS_CACHE = {}
_plugins_queue = None
_plugins_queue_lock = threading.Lock()


def _java_class(name):
    if _jclass is None:
        raise RuntimeError(
            "client_utils is only available inside Irongram's Chaquopy runtime"
        ) from _JAVA_IMPORT_ERROR
    if name not in _CLASS_CACHE:
        _CLASS_CACHE[name] = _jclass(name)
    return _CLASS_CACHE[name]


def _get_plugins_queue():
    global _plugins_queue
    if _plugins_queue is None:
        with _plugins_queue_lock:
            if _plugins_queue is None:
                _plugins_queue = _java_class("org.telegram.messenger.DispatchQueue")(
                    "IrongramPluginsQueue"
                )
    return _plugins_queue


def get_send_messages_helper(account=None):
    """Return SendMessagesHelper for ``account`` or the currently selected account."""
    if account is None:
        account = _java_class("org.telegram.messenger.UserConfig").selectedAccount
    return _java_class("org.telegram.messenger.SendMessagesHelper").getInstance(int(account))


def get_last_fragment():
    """Return the currently visible Telegram fragment, or None when none is available."""
    return _java_class("org.telegram.ui.LaunchActivity").getLastFragment()


def get_queue_by_name(queue_name):
    """Return a built-in DispatchQueue, or None for an unknown queue name."""
    if queue_name == PLUGINS_QUEUE:
        return _get_plugins_queue()
    if queue_name not in {
        STAGE_QUEUE,
        GLOBAL_QUEUE,
        CACHE_CLEAR_QUEUE,
        SEARCH_QUEUE,
        PHONE_BOOK_QUEUE,
        THEME_QUEUE,
        EXTERNAL_NETWORK_QUEUE,
    }:
        return None
    return getattr(_java_class("org.telegram.messenger.Utilities"), queue_name)


def run_on_queue(func, queue=PLUGINS_QUEUE, delay=0):
    """Post ``func`` to a DispatchQueue; defaults to Irongram's plugin queue."""
    if not callable(func):
        raise TypeError("run_on_queue expects a callable")
    try:
        delay_ms = int(delay)
    except (TypeError, ValueError, OverflowError):
        raise TypeError("delay must be an integer number of milliseconds")
    if delay_ms < 0:
        delay_ms = 0

    if queue is None or queue == PLUGINS_QUEUE:
        dispatch_queue = _get_plugins_queue()
    elif isinstance(queue, str):
        dispatch_queue = get_queue_by_name(queue)
        if dispatch_queue is None:
            raise ValueError("Unknown Irongram DispatchQueue: {}".format(queue))
    else:
        dispatch_queue = queue

    from android_utils import R

    return dispatch_queue.postRunnable(R(func), delay_ms)
