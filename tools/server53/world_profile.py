"""Derive reviewed gameplay metadata in a new run; never edit the source map.

Unchanged NBT tags retain their exact uncompressed bytes, including tag types,
ordering and unknown extension data. This module performs no filesystem writes.
"""
from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
import gzip
import hashlib
import struct

LIMIT = 16 * 1024 * 1024
PROFILE_ID = "catancraft-protected-map-progression53-v1"
FIELDS = {"GameType": (3, 2), "Difficulty": (1, 2)}
# Preserve every other authored rule, including protected scenery, daylight,
# weather and keepInventory. This is the CatanCraft no-grant test economy;
# packaging does not introduce vanilla survival or inventory-loss mechanics.
RULES = {"randomTickSpeed": "3"}


class ProfileError(ValueError):
    pass


@dataclass(frozen=True)
class Node:
    kind: int
    name: str
    raw: bytes
    value: object


class Reader:
    def __init__(self, raw: bytes):
        self.raw, self.pos, self.nodes = raw, 0, 0

    def take(self, n: int) -> bytes:
        if n < 0 or n > LIMIT or self.pos + n > len(self.raw):
            raise ProfileError("Truncated or oversized NBT field")
        result = self.raw[self.pos:self.pos + n]
        self.pos += n
        return result

    def number(self, fmt: str):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(">" + fmt)))[0]

    def string(self) -> str:
        return self.take(self.number("H")).decode("utf-8")

    def payload(self, kind: int, depth: int):
        self.nodes += 1
        if depth > 64 or self.nodes > 1_000_000:
            raise ProfileError("NBT nesting/node limit")
        if kind in range(1, 7):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 8:
            return self.string()
        if kind in (7, 11, 12):
            count = self.number("i")
            if count < 0:
                raise ProfileError("Negative NBT array length")
            return self.take(count * {7: 1, 11: 4, 12: 8}[kind])
        if kind == 9:
            subtype, count = self.number("B"), self.number("i")
            if subtype not in range(13) or count < 0 or count > 1_000_000 or subtype == 0 and count:
                raise ProfileError("Invalid NBT list")
            return (subtype, [self.payload(subtype, depth + 1) for _ in range(count)])
        if kind == 10:
            result = {}
            while True:
                start = self.pos
                subtype = self.number("B")
                if subtype == 0:
                    return result
                name = self.string()
                if name in result:
                    raise ProfileError("Duplicate NBT name")
                value = self.payload(subtype, depth + 1)
                result[name] = Node(subtype, name, self.raw[start:self.pos], value)
        raise ProfileError("Unknown NBT type")


def text(value: str) -> bytes:
    raw = value.encode("utf-8")
    if len(raw) > 65535:
        raise ProfileError("NBT string too long")
    return struct.pack(">H", len(raw)) + raw


def tag(kind: int, name: str, value) -> bytes:
    if kind == 8:
        payload = text(value)
    elif kind == 10:
        payload = b"".join(value) + b"\0"
    else:
        payload = struct.pack(">" + {1: "b", 3: "i"}[kind], value)
    return bytes([kind]) + text(name) + payload


def decode(blob: bytes) -> tuple[str, dict[str, Node]]:
    try:
        with gzip.GzipFile(fileobj=BytesIO(blob)) as stream:
            raw = stream.read(LIMIT + 1)
        if len(raw) > LIMIT:
            raise ProfileError("level.dat expands beyond bound")
        reader = Reader(raw)
        if reader.number("B") != 10:
            raise ProfileError("NBT root must be compound")
        root_name = reader.string()
        root = reader.payload(10, 0)
        if reader.pos != len(raw):
            raise ProfileError("Trailing level.dat NBT")
        return root_name, root
    except (OSError, EOFError, UnicodeError, struct.error) as ex:
        raise ProfileError("Malformed level.dat") from ex


def derive(blob: bytes) -> tuple[bytes, dict]:
    root_name, root = decode(blob)
    data = root.get("Data")
    if data is None or data.kind != 10 or "Player" in data.value:
        raise ProfileError("Fresh world Data compound without inherited Player required")
    fields = data.value
    rules = fields.get("GameRules")
    if rules is None or rules.kind != 10:
        raise ProfileError("Reviewed authored GameRules required")
    for name, (kind, _) in FIELDS.items():
        if name not in fields or fields[name].kind != kind:
            raise ProfileError("Missing/wrong typed gameplay field: " + name)
    for name in RULES:
        if name in rules.value and rules.value[name].kind != 8:
            raise ProfileError("Wrong typed gamerule: " + name)
    # Account for every changed field in provenance; untouched fields are reused
    # verbatim rather than converting the entire world through a generic encoder.
    changes = {name: {"type": kind, "before": fields[name].value, "after": value}
               for name, (kind, value) in FIELDS.items()}
    rule_changes = {name: {"before": rules.value[name].value if name in rules.value else None,
                           "after": value} for name, value in RULES.items()}
    rule_raw = [tag(8, name, RULES[name]) if name in RULES else node.raw
                for name, node in rules.value.items()]
    rule_raw += [tag(8, name, value) for name, value in RULES.items() if name not in rules.value]
    field_raw = []
    for name, node in fields.items():
        if name in FIELDS:
            kind, value = FIELDS[name]
            field_raw.append(tag(kind, name, value))
        elif name == "GameRules":
            field_raw.append(tag(10, name, rule_raw))
        else:
            field_raw.append(node.raw)
    result = gzip.compress(tag(10, root_name, [tag(10, "Data", field_raw) if name == "Data" else node.raw
                                             for name, node in root.items()]), mtime=0)
    _, reread = decode(result)
    for name, node in root.items():
        if name != "Data" and node.raw != reread[name].raw:
            raise ProfileError("Unrelated root tag changed")
    after = reread["Data"].value
    for name, node in fields.items():
        if name not in FIELDS and name != "GameRules" and node.raw != after[name].raw:
            raise ProfileError("Unrelated world metadata changed")
    digest = lambda b: hashlib.sha256(b).hexdigest()
    return result, {"profile": PROFILE_ID, "sourceLevelSHA256": digest(blob),
                    "derivedLevelSHA256": digest(result), "fields": changes,
                    "gameRules": rule_changes, "unchangedTagsBytePreserved": True,
                    "regionFilesChanged": False, "grantsOrPurchases": False,
                    "nativeGameplayQualified": False}
