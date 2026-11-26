"""exteraGram compatible ``markdown_utils`` module.

``parse_markdown(source)`` converts a markdown string into plain text plus
Telegram message entities, so plugins can send formatted messages::

    parsed = parse_markdown("**bold** and `code`")
    parsed.text        # "bold and code"
    parsed.entities    # [<bold 0:4>, <code 9:13>]
    parsed.entities[0].to_tlrpc_object()   # TLRPC.TL_messageEntityBold

Supported markdown: ``**bold**``, ``__underline__``, ``~~strike~~``,
``||spoiler||``, ``_italic_``, ``*italic*``, `` `code` ``, fenced code blocks,
``[text](url)`` links and ``\\`` escapes.

Nesting is supported; anything the parser does not understand stays in the text
verbatim, so nothing is lost.
"""

import re

try:
    from java import jclass as _jclass
except Exception as _error:
    _jclass = None
    _JAVA_IMPORT_ERROR = _error
else:
    _JAVA_IMPORT_ERROR = None

_ENTITY_CLASSES = {}

_ENTITY_NAMES = {
    "bold": "Bold",
    "italic": "Italic",
    "underline": "Underline",
    "strike": "Strike",
    "code": "Code",
    "pre": "Pre",
    "spoiler": "Spoiler",
    "text_url": "TextUrl",
    "url": "Url",
    "blockquote": "Blockquote",
}

# Longest markers first so "**" wins over "*".
_INLINE_PATTERNS = (
    ("pre", re.compile(r"```([\s\S]*?)```")),
    ("code", re.compile(r"`([^`\n]+)`")),
    ("bold", re.compile(r"\*\*([\s\S]+?)\*\*")),
    ("underline", re.compile(r"__([\s\S]+?)__")),
    ("strike", re.compile(r"~~([\s\S]+?)~~")),
    ("spoiler", re.compile(r"\|\|([\s\S]+?)\|\|")),
    ("link", re.compile(r"\[([^\]\n]*)\]\(([^)\s]+)\)")),
    ("italic", re.compile(r"(?<![*\w])\*([^*\n]+)\*(?![*\w])")),
    ("italic", re.compile(r"(?<![_\w])_([^_\n]+)_(?![_\w])")),
)


class ParsedMarkdown:
    """Result of :func:`parse_markdown`."""

    def __init__(self, text, entities):
        self.text = text
        self.entities = list(entities)

    def __str__(self):
        return self.text

    def __repr__(self):
        return "ParsedMarkdown(text={!r}, entities={!r})".format(self.text, self.entities)


class MessageEntity:
    """A markdown entity which can be converted into a TLRPC entity object."""

    def __init__(self, entity_type, offset, length, url=None, language=None):
        self.type = entity_type
        self.offset = int(offset)
        self.length = int(length)
        self.url = url
        self.language = language

    def to_tlrpc_object(self):
        """Return the matching ``TLRPC.TL_messageEntity*`` instance."""
        if _jclass is None:
            raise RuntimeError(
                "markdown_utils entities need Irongram's Chaquopy runtime"
            ) from _JAVA_IMPORT_ERROR
        suffix = _ENTITY_NAMES.get(self.type)
        if suffix is None:
            raise ValueError("Unsupported markdown entity: {}".format(self.type))
        if suffix not in _ENTITY_CLASSES:
            _ENTITY_CLASSES[suffix] = _jclass(
                "org.telegram.tgnet.TLRPC$TL_messageEntity" + suffix
            )
        entity = _ENTITY_CLASSES[suffix]()
        entity.offset = self.offset
        entity.length = self.length
        if self.type == "text_url":
            entity.url = self.url
        if self.type == "pre" and self.language:
            entity.language = self.language
        return entity

    def __repr__(self):
        return "MessageEntity({}, {}, {})".format(self.type, self.offset, self.length)


class _Builder:
    def __init__(self):
        self.text = []
        self.entities = []
        self.length = 0

    def append(self, value):
        value = value if value else ""
        start = self.length
        self.text.append(value)
        self.length += len(value)
        return start, len(value)

    def _length(self):
        return self.length


def _is_escaped(source, position):
    backslashes = 0
    position -= 1
    while position >= 0 and source[position] == "\\":
        backslashes += 1
        position -= 1
    return backslashes % 2 == 1


def _next_match(source, index):
    """Return ``(kind, match)`` for the first non-escaped inline markdown match after ``index``."""
    best = None
    for kind, pattern in _INLINE_PATTERNS:
        candidate = None
        for match in pattern.finditer(source, index):
            if _is_escaped(source, match.start()):
                continue
            candidate = match
            break
        if candidate is None:
            continue
        if best is None or candidate.start() < best[1].start():
            best = (kind, candidate)
    return best


def _unescape(value):
    return re.sub(r"\\([\\`*_{}\[\]()#+\-.!~|>])", r"\1", value)


def _parse_into(source, builder):
    index = 0
    length = len(source)
    while index < length:
        found = _next_match(source, index)
        if found is None:
            builder.append(_unescape(source[index:]))
            return
        kind, match = found
        start, end = match.start(), match.end()
        if start > index:
            builder.append(_unescape(source[index:start]))
        if kind == "link":
            label = _unescape(match.group(1))
            url = match.group(2)
            label_start, label_length = builder.append(label)
            if label_length > 0:
                builder.entities.append(
                    MessageEntity("text_url", label_start, label_length, url=url)
                )
            index = end
            continue
        inner = match.group(1)
        entity_start = builder._length()
        if kind in ("code", "pre"):
            builder.append(inner)
        else:
            _parse_into(inner, builder)
        entity_length = builder._length() - entity_start
        if entity_length > 0:
            builder.entities.append(MessageEntity(kind, entity_start, entity_length))
        index = end


def parse_markdown(source):
    """Parse ``source`` as markdown and return :class:`ParsedMarkdown`."""
    if source is None:
        return ParsedMarkdown("", [])
    text = str(source)
    builder = _Builder()
    _parse_into(text, builder)
    result_text = "".join(builder.text)
    entities = [entity for entity in builder.entities if entity.length > 0]
    entities.sort(key=lambda entity: (entity.offset, -entity.length))
    return ParsedMarkdown(result_text, entities)


def entities_to_json(entities):
    """Serialize TLRPC entities into plain dictionaries (handy for plugin settings)."""
    values = []
    for entity in entities or []:
        try:
            values.append({
                "type": str(entity.getClass().getSimpleName()).lower(),
                "offset": int(getattr(entity, "offset", 0)),
                "length": int(getattr(entity, "length", 0)),
                "url": getattr(entity, "url", None),
            })
        except Exception:
            continue
    return values
