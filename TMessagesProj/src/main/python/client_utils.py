"""Small exteraGram ``client_utils`` subset used by current Irongram plugins.

This module provides selected queue, fragment, send-helper, and file-loader accessors.
It is intentionally not the full exteraGram API.
"""

import threading

try:
    from java import dynamic_proxy as _dynamic_proxy
    from java import jclass as _jclass
except Exception as error:
    _dynamic_proxy = None
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


def get_file_loader(account=None):
    """Return FileLoader for ``account`` or the currently selected account."""
    if account is None:
        account = _java_class("org.telegram.messenger.UserConfig").selectedAccount
    return _java_class("org.telegram.messenger.FileLoader").getInstance(int(account))


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


# --- account and controller accessors -------------------------------------

def get_user_config():
    """Return the ``UserConfig`` Java class (static access, as in exteraGram)."""
    return _java_class("org.telegram.messenger.UserConfig")


def _account_number(account=None):
    if account is None:
        return int(get_user_config().selectedAccount)
    return int(account)


def get_account_instance(account=None):
    """Return ``AccountInstance`` for ``account`` or the currently selected account."""
    return _java_class("org.telegram.messenger.AccountInstance").getInstance(
        _account_number(account)
    )


def get_messages_controller(account=None):
    """Return ``MessagesController`` for ``account`` or the currently selected account."""
    return _java_class("org.telegram.messenger.MessagesController").getInstance(
        _account_number(account)
    )


def get_connections_manager(account=None):
    """Return ``ConnectionsManager`` for ``account`` or the currently selected account."""
    return _java_class("org.telegram.tgnet.ConnectionsManager").getInstance(
        _account_number(account)
    )


def get_contacts_controller(account=None):
    """Return ``ContactsController`` for ``account`` or the currently selected account."""
    return _java_class("org.telegram.messenger.ContactsController").getInstance(
        _account_number(account)
    )


def get_notifications_controller(account=None):
    """Return ``NotificationsController`` for ``account`` or the currently selected account."""
    return _java_class("org.telegram.messenger.NotificationsController").getInstance(
        _account_number(account)
    )


def get_media_data_controller(account=None):
    """Return ``MediaDataController`` for ``account`` or the currently selected account."""
    return _java_class("org.telegram.messenger.MediaDataController").getInstance(
        _account_number(account)
    )


# --- TL requests ----------------------------------------------------------

if _dynamic_proxy is not None:
    try:
        _RequestDelegateInterface = _java_class("org.telegram.tgnet.RequestDelegate")

        class _RequestDelegateProxy(_dynamic_proxy(_RequestDelegateInterface)):
            def __init__(self, callback):
                super().__init__()
                self._callback = callback

            def run(self, response, error):
                self._callback(response, error)

    except Exception as error:
        _JAVA_IMPORT_ERROR = error
        _RequestDelegateInterface = None
        _RequestDelegateProxy = None
else:
    _RequestDelegateInterface = None
    _RequestDelegateProxy = None


def RequestCallback(callback):
    """Wrap ``callback(response, error)`` into a ``RequestDelegate`` for ``sendRequest``."""
    if not callable(callback):
        raise TypeError("RequestCallback expects a callable")
    if _RequestDelegateProxy is None:
        raise RuntimeError(
            "RequestCallback needs Chaquopy Java interface proxies in Irongram"
        ) from _JAVA_IMPORT_ERROR
    return _RequestDelegateProxy(callback)


def send_request(request, callback=None, flags=0, account=None):
    """Send a TL request through ``ConnectionsManager``; ``callback`` may be a Python callable."""
    if request is None:
        raise TypeError("send_request expects a TL request object")
    manager = get_connections_manager(account)
    delegate = callback
    if callback is not None and not (_RequestDelegateInterface is not None
                                     and isinstance(callback, _RequestDelegateInterface)):
        delegate = RequestCallback(callback)
    if delegate is None:
        return manager.sendRequest(request)
    return manager.sendRequest(request, delegate, int(flags))


def _send_params_class():
    return _java_class("org.telegram.messenger.SendMessagesHelper$SendMessageParams")


def _java_list(values):
    array_list = _java_class("java.util.ArrayList")()
    for value in values or []:
        array_list.add(value)
    return array_list


def _peer_from(message_data):
    for key in ("peer", "dialog_id", "to_peer", "chat_id", "user_id"):
        value = message_data.get(key)
        if value is not None:
            return int(value)
    raise ValueError("send_message requires a 'peer' entry")


def send_message(message_data, account=None):
    """Send a message described by a dictionary, as exteraGram plugins expect.

    Supported keys: ``peer`` (required, dialog id), ``message``, ``caption``,
    ``path`` (attach a file), ``entities``, ``replyToMsg``, ``replyToTopMsg``,
    ``searchLinks``, ``silent``, and ``webPage``.
    """
    if message_data is None:
        raise TypeError("send_message expects a SendMessagesHelperParams or a dictionary")
    helper = get_send_messages_helper(account)
    if not isinstance(message_data, dict):
        return helper.sendMessage(message_data)

    params = _send_params_class()()
    params.peer = _peer_from(message_data)
    message = message_data.get("message")
    if message is not None:
        params.message = str(message)
    caption = message_data.get("caption")
    if caption is not None:
        params.caption = str(caption)
    path = message_data.get("path") or message_data.get("document_path")
    if path:
        params.path = str(path)
    entities = message_data.get("entities")
    if entities:
        params.entities = _java_list(entities)
    reply = message_data.get("replyToMsg", message_data.get("reply_to_msg"))
    if reply is not None:
        params.replyToMsg = reply
    reply_top = message_data.get("replyToTopMsg", message_data.get("reply_to_top_msg"))
    if reply_top is not None:
        params.replyToTopMsg = reply_top
    if "searchLinks" in message_data:
        params.searchLinks = bool(message_data["searchLinks"])
    elif "search_links" in message_data:
        params.searchLinks = bool(message_data["search_links"])
    web_page = message_data.get("webPage")
    if web_page is not None:
        params.webPage = web_page
    params.notify = not bool(message_data.get("silent", False))
    return helper.sendMessage(params)


def send_document(peer, path, caption=None, entities=None, account=None, notify=True):
    """Send the file at ``path`` as a document to ``peer``."""
    helper_class = _java_class("org.telegram.messenger.SendMessagesHelper")
    helper_class.prepareSendingDocument(
        get_account_instance(account),
        str(path),
        None,
        None,
        None if caption is None else str(caption),
        None,
        int(peer),
        None,
        None,
        None,
        None,
        None,
        bool(notify),
        0,
        None,
        None,
        False
    )
    return True
