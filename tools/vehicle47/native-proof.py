"""Independent read-only native047 evidence audit (stdlib only).

CLI: python native-proof.py EVIDENCE_DIRECTORY SOURCE_COMMIT [RUNTIME_MANIFEST_SHA256]
Import: verify_native(evidence_directory, source_commit, runtime_sha256=None) -> dict.
Reads original sealed journals, native SavedData and actual Anvil entity regions;
never imports entities, rewrites evidence, or treats a captured snapshot as a live entity.
"""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import uuid
import zlib

LIMIT = 64 * 1024 * 1024
NS = "catancraft_vehicle47"
PHASES = ["PREPARED", "DEBIT_ATTEMPTED", "PAID", "SPAWN_ATTEMPTED", "COMPLETE"]
PRICES = {
    "pickup": (4000, {"STEEL": 20, "MECHANICAL_PARTS": 10, "FUEL": 8}),
    "lav150": (8500, {"STEEL": 50, "MECHANICAL_PARTS": 25, "FUEL": 20, "ELECTRONICS": 5}),
}
TYPES = {"pickup": "superbwarfare:sodayo_pick_up", "lav150": "superbwarfare:lav_150"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(blob):
    return hashlib.sha256(blob).hexdigest()


def read(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), f"Missing/foreign evidence: {path}")
    require(path.stat().st_size <= LIMIT, f"Oversized evidence: {path}")
    return path.read_bytes()


def inflate(blob, wbits):
    inflater = zlib.decompressobj(wbits)
    out = inflater.decompress(blob, LIMIT + 1)
    require(len(out) <= LIMIT and inflater.eof and not inflater.unconsumed_tail and not inflater.unused_data,
            "Invalid, oversized or trailing compressed native NBT")
    return out


def mutf_encode(value):
    # Java DataOutput.writeUTF uses modified UTF-8 over UTF-16 code units.
    units = value.encode("utf-16-be", "surrogatepass")
    out = bytearray()
    for offset in range(0, len(units), 2):
        c = int.from_bytes(units[offset:offset + 2], "big")
        if 0 < c <= 0x7f:
            out.append(c)
        elif c <= 0x7ff:
            out.extend((0xc0 | c >> 6, 0x80 | c & 63))
        else:
            out.extend((0xe0 | c >> 12, 0x80 | c >> 6 & 63, 0x80 | c & 63))
    require(len(out) <= 65535, "Oversized Java UTF string")
    return struct.pack(">H", len(out)) + bytes(out)


class Reader:
    """Retains NBT types so the independent intent digest is type preserving."""
    def __init__(self, blob):
        self.blob, self.offset, self.nodes = blob, 0, 0

    def take(self, length):
        require(0 <= length <= LIMIT and self.offset + length <= len(self.blob), "Truncated NBT")
        out = self.blob[self.offset:self.offset + length]
        self.offset += length
        return out

    def number(self, format):
        return struct.unpack(">" + format, self.take(struct.calcsize(">" + format)))[0]

    def string(self):
        raw = self.take(self.number("H"))
        text = raw.replace(b"\xc0\x80", b"\x00").decode("utf-8", "surrogatepass")
        return text.encode("utf-16-be", "surrogatepass").decode("utf-16-be", "surrogatepass")

    def count(self):
        n = self.number("i")
        require(0 <= n <= 2_000_000, "Oversized or negative NBT collection")
        return n

    def payload(self, kind, depth=0):
        self.nodes += 1
        require(self.nodes <= 2_000_000 and depth <= 64, "NBT tree exceeds bounds")
        if kind in (1, 2, 3, 4, 5, 6):
            value = self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        elif kind == 7:
            value = self.take(self.count())
        elif kind == 8:
            value = self.string()
        elif kind == 9:
            child, count = self.number("B"), self.count()
            require(child in range(13) and (child != 0 or count == 0), "Invalid NBT list type")
            value = [self.payload(child, depth + 1) for _ in range(count)]
        elif kind == 10:
            value = {}
            while True:
                child = self.number("B")
                if child == 0:
                    break
                name = self.string()
                require(name not in value, "Duplicate NBT compound key")
                value[name] = self.payload(child, depth + 1)
        elif kind in (11, 12):
            value = [self.number("i" if kind == 11 else "q") for _ in range(self.count())]
        else:
            raise ValueError(f"Unknown NBT type {kind}")
        return kind, value

    def root(self):
        require(self.number("B") == 10, "Native NBT root must be compound")
        self.string()
        root = self.payload(10)
        require(self.offset == len(self.blob), "Trailing native NBT data")
        return root


def plain(tag):
    kind, value = tag
    if kind == 10:
        return {k: plain(v) for k, v in value.items()}
    if kind == 9:
        return [plain(v) for v in value]
    return value


def sealed(path):
    blob = read(path)
    require(len(blob) >= 40, "Truncated sealed journal")
    magic, length = struct.unpack(">II", blob[:8])
    require(magic == 0x43505231 and length == len(blob) - 40 and 0 < length <= 8 * 1024 * 1024,
            f"Invalid sealed journal envelope: {path}")
    require(blob[8:40].hex() == digest(blob[40:]), f"Sealed checksum mismatch: {path}")
    return Reader(inflate(blob[40:], 31)).root()


def native_nbt(path):
    return plain(Reader(inflate(read(path), 31)).root())


def canonical(tag):
    kind, value = tag
    out = bytes([kind])
    if kind == 10:
        out += struct.pack(">i", len(value))
        for name in sorted(value):
            out += mutf_encode(name) + canonical(value[name])
    elif kind == 9:
        out += struct.pack(">i", len(value)) + b"".join(canonical(child) for child in value)
    elif kind == 11:
        out += struct.pack(">i", len(value)) + b"".join(struct.pack(">i", n) for n in value)
    elif kind in (3, 4, 6):
        out += struct.pack({3: ">i", 4: ">q", 6: ">d"}[kind], value)
    elif kind == 8:
        out += mutf_encode(value)
    else:
        raise ValueError("Unexpected type in canonical vehicle intent")
    return out


def fingerprint(record):
    original = copy.deepcopy(record)
    original[1]["phase"] = (8, "PREPARED")
    return digest(canonical(original))


def uid(ints):
    require(isinstance(ints, list) and len(ints) == 4 and all(isinstance(v, int) for v in ints), "Malformed UUID")
    return str(uuid.UUID(bytes=b"".join(struct.pack(">I", v & 0xffffffff) for v in ints)))


def regions(directory):
    entities, files = {}, []
    for path in sorted(Path(directory).glob("r.*.*.mca")):
        match = re.fullmatch(r"r\.(-?\d+)\.(-?\d+)\.mca", path.name)
        require(match is not None, "Foreign entity-region filename")
        rx, rz = map(int, match.groups())
        blob = read(path)
        require(len(blob) >= 8192 and len(blob) % 4096 == 0, "Malformed native region length")
        occupied = {0, 1}
        for index in range(1024):
            location = struct.unpack(">I", blob[index * 4:index * 4 + 4])[0]
            if not location:
                continue
            start, count = location >> 8, location & 255
            require(start >= 2 and count > 0 and (start + count) * 4096 <= len(blob), "Invalid native region extent")
            allocation = set(range(start, start + count))
            require(not occupied.intersection(allocation), "Overlapping region chunks")
            occupied.update(allocation)
            offset = start * 4096
            length = struct.unpack(">I", blob[offset:offset + 4])[0]
            require(1 < length <= count * 4096 - 4, "Invalid native region payload length")
            mode, payload = blob[offset + 4], blob[offset + 5:offset + 4 + length]
            require(mode in (1, 2, 3), "External or unknown native entity compression")
            raw = payload if mode == 3 else inflate(payload, 31 if mode == 1 else 15)
            typed = Reader(raw).root()
            chunk = plain(typed)
            require(chunk["Position"] == [rx * 32 + index % 32, rz * 32 + index // 32], "Wrong entity chunk position")
            require(typed[1]["Entities"][0] == 9 and all(t[0] == 10 for t in typed[1]["Entities"][1]), "Invalid native entity list")
            for entity in chunk["Entities"]:
                identity = uid(entity["UUID"])
                require(identity not in entities and isinstance(entity.get("id"), str), "Duplicate or malformed native entity identity")
                entities[identity] = entity
        files.append({"name": path.name, "sha256": digest(blob), "bytes": len(blob)})
    return entities, files


def journal(directory):
    grouped = {}
    for path in sorted(Path(directory).glob("*.dat")):
        typed = sealed(path)
        record = plain(typed)
        identity = uid(record["id"])
        phase = PHASES.index(record["phase"])
        require(path.name == f"{identity}.{phase}.dat", "Phase file name differs from original record")
        grouped.setdefault(identity, []).append((phase, record, fingerprint(typed)))
    result = {}
    for identity, rows in grouped.items():
        rows.sort()
        require([row[0] for row in rows] == list(range(len(rows))) and len({r[2] for r in rows}) == 1,
                "Gapped or changed immutable native vehicle journal")
        _, record, fp = rows[-1]
        offer = record["offer"]
        require(offer in PRICES and record["catalog"] == "native_vehicle47_v1", "Changed original vehicle catalog")
        cash, materials = PRICES[offer]
        require(record["price"] == {"money": cash, "resources": materials}, "Changed original vehicle price")
        require(record["bay"]["vehicle"] == TYPES[offer], "Offer/native type mismatch")
        result[identity] = (record, fp)
    require(bool(result), "Missing retained native vehicle journal")
    return result


def expected_provenance(record, fp):
    bay, facility = record["bay"], record["bay"]["facility"]
    anchor, spawn = facility["anchor"], bay["spawn"]
    sign = 1 if facility["rotation"] == 0 else -1
    return dict(schema=1, operation=record["id"], actor=record["actor"], nation=record["nation"], entity=record["entity"],
                world=record["world"], offer=record["offer"], type=bay["vehicle"], fingerprint=fp,
                x=anchor["x"] + .5 + sign * (spawn["x"] - .5), y=anchor["y"] + spawn["y"],
                z=anchor["z"] + .5 + sign * (spawn["z"] - .5), yaw=(bay["heading"] + facility["rotation"]) % 360)


def entity_proof(entity, record, fp, wrong_nation=False):
    require(uid(entity["UUID"]) == uid(record["entity"]) and entity["id"] == TYPES[record["offer"]], "Native UUID/type differs from immutable intent")
    forge = entity["ForgeData"]
    require(forge["CatanCraftPurchasedVehicle"] == 1, "Native purchased/crowbar protection tag missing")
    same_nation = forge["CatanCraftVehicleNation"] == uid(record["nation"])
    require(same_nation != wrong_nation, "Native nation provenance differs from expected fixture state")
    require(forge["CatanCraftVehicleDelivery47"] == expected_provenance(record, fp), "Native full transaction provenance differs")


def hashes(directory, expected, exact=False):
    current = {p.name: digest(read(p)) for p in Path(directory).glob("*.dat")}
    require(bool(expected), "Missing immutable phase hashes")
    require(all(current.get(k) == v for k, v in expected.items()), "Retained original phase bytes changed")
    require(not exact or current == expected, "Unexpected native journal phase inventory")


def metadata(proof, namespace, source, runtime):
    require(proof["schema"] == 1 and proof["namespace"] == namespace and proof["source"] == source and
            proof["runtimeManifestSHA256"] == runtime, "Native proof source/runtime/namespace mismatch")
    require(re.fullmatch(r"\d+:\d+", proof["process"]) is not None, "Missing actual JVM process identity")
    require(proof["world"].endswith("/controlled-" + namespace + "-run/world"), "Foreign controlled native world provenance")


def parser_fixtures(directory):
    """Inspect retained malformed parser inputs without accepting them as native entities."""
    directory = Path(directory)
    require({p.name for p in directory.glob("*.mca")} == {"valid.mca", *(f"malformed_{n}.mca" for n in range(6))},
            "Missing or unexpected raw parser fixture files")
    rows = []
    for name in ["valid", *(f"malformed_{n}" for n in range(6))]:
        blob = read(directory / (name + ".mca"))
        require(len(blob) >= 12288 and struct.unpack(">I", blob[:4])[0] >> 8 == 2 and blob[8196] == 2,
                "Malformed parser fixture envelope differs from intended corruption")
        length = struct.unpack(">I", blob[8192:8196])[0]
        rows.append(Reader(inflate(blob[8197:8196 + length], 15)).root())
    valid, *bad = rows
    require(plain(valid)["Position"] == [0, 0] and len(plain(valid)["Entities"]) == 1, "Raw parser valid control missing")
    uid(plain(valid)["Entities"][0]["UUID"])
    require(bad[0][1]["Entities"][1][0][0] == 8, "Wrong-list native parser corruption missing")
    require("UUID" not in plain(bad[1])["Entities"][0], "Missing-UUID native parser corruption missing")
    require(bad[2][1]["Entities"][1][0][1]["id"][0] == 1, "Wrong-ID-type native parser corruption missing")
    duplicate = plain(bad[3])["Entities"]
    require(len(duplicate) == 2 and duplicate[0]["UUID"] == duplicate[1]["UUID"], "Duplicate-UUID native parser corruption missing")
    require(plain(bad[4])["Position"] == [1, 0], "Foreign-chunk native parser corruption missing")
    require(plain(bad[5])["Entities"][0]["UUID"] == [1, 2, 3], "Malformed-UUID native parser corruption missing")


def charge(ledger, nation_before, records):
    expected = copy.deepcopy(nation_before)
    for record, fp in records.values():
        require(record["nation"] == expected["id"] and record["actor"] == expected["leaderId"], "Native intent changed actor/nation")
        cash, resources = PRICES[record["offer"]]
        expected["treasury"] -= cash
        for key, count in resources.items():
            expected["stockpile"][key.lower()] -= count
        require(ledger["placedMapAssets"]["vehicle47_paid:" + uid(record["id"])] == fp, "Original native debit receipt missing or changed")
    nations = [n for n in ledger["nations"] if n["id"] == expected["id"]]
    require(nations == [expected], "Actual native nation ledger is not exactly one original charge per intent")
    return expected


def verify_native(evidence_directory, source_commit, runtime_sha256=None):
    ev = Path(evidence_directory)
    require(re.fullmatch(r"[0-9a-f]{40}", source_commit) is not None, "Exact source SHA required")
    runtime_sha256 = runtime_sha256 or digest(read(ev / "runtime-artifacts.json"))
    require(re.fullmatch(r"[0-9a-f]{64}", runtime_sha256) is not None, "Pinned runtime SHA required")
    base = ev / "native" / NS
    ledger = native_nbt(base / "data/catancraft.dat")["data"]
    live, base_regions = regions(base / "entities")
    expected_live, operations, snapshots = set(), set(), 0
    state = {"prices": (["COMPLETE"] * 2, set()), "debit": (["COMPLETE", "SPAWN_ATTEMPTED", "COMPLETE"], {0, 2}),
             "save": (["COMPLETE"] * 3, {0, 1, 2}), "holds": (["SPAWN_ATTEMPTED"] * 3, {0, 1})}
    for key, (phases, resident_slots) in state.items():
        proof = plain(sealed(base / f"data/catancraft_vehicle47_{key}_verified.dat"))
        metadata(proof, NS, source_commit, runtime_sha256)
        require(proof["result"] == "PASS", "Native base proof is not successful")
        directory = base / f"data/catancraft_vehicle47_journal_{key}"
        records = journal(directory)
        require(len(records) == len(phases), "Unexpected original native intent count")
        hashes(directory, proof["journalFiles"], True)
        require(proof["nationBefore"]["treasury"] == 100000 and set(proof["nationBefore"]["stockpile"].values()) == {1000}, "Changed native fixture starting economy")
        expected = charge(ledger, proof["nationBefore"], records)
        require(expected == proof["nationAfter"] == charge(proof["nativeLedger"], proof["nationBefore"], records), "Native proof ledger snapshot disagrees with final original accounting")
        for identity, (record, fp) in records.items():
            slot = int(record["bay"]["facility"]["plot"].rsplit("_", 1)[1])
            require(record["phase"] == phases[slot] and identity not in operations, "Unexpected final native phase or duplicate operation")
            operations.add(identity)
            entity_id = uid(record["entity"])
            if slot in resident_slots:
                require(entity_id in live, "Original native entity missing from actual retained regions")
                expected_live.add(entity_id)
                entity_proof(live[entity_id], record, fp, key == "holds" and slot == 0)
                if key == "holds" and slot == 1:
                    require(abs(live[entity_id]["Pos"][0] - expected_provenance(record, fp)["x"]) >= 7.9, "Moved held entity was relocated to original bay")
            else:
                require(entity_id not in live, "Missing/completed-loss entity was replaced")
        for row in proof["nativeEntitySnapshots"]:
            record, fp = records[uid(row["record"]["id"])]
            entity_proof(row["nativeEntity"], record, fp)
            snapshots += 1
    actual = {k for k, e in live.items() if e["id"] in TYPES.values()}
    require(actual == expected_live and len(actual) == 7 and len(operations) == 11 and snapshots == 10, "Base native UUID/operation/snapshot count mismatch")
    require({k.removeprefix("vehicle47_paid:") for k in ledger["placedMapAssets"] if k.startswith("vehicle47_paid:")} == operations, "Unexpected base vehicle debit receipt")
    parser_fixtures(base / "data/catancraft_vehicle47_parser_holds")

    restart = ev / "native" / (NS + "_restart")
    seed_path = restart / "data/catancraft_vehicle47_restart_seed.dat"
    seed = plain(sealed(seed_path))
    verified = plain(sealed(restart / "data/catancraft_vehicle47_restart_verified.dat"))
    for proof in (seed, verified):
        metadata(proof, NS + "_restart", source_commit, runtime_sha256)
    require(seed["result"] == "SEEDED_PENDING_DISTINCT_JVM_VERIFICATION" and verified["result"] == "PASS", "Restart proof lacks both successful phases")
    require(seed["world"] == verified["world"] and seed["process"] != verified["process"] and
            verified["seedProcess"] == seed["process"] and verified["seedSHA256"] == digest(read(seed_path)), "Separate-JVM original seed binding failed")
    require({k: verified[k] for k in ("restoredNativeUUIDs", "firstSpawnFromOriginalPaid", "movedPendingHeld", "completedLossReplacements")} ==
            dict(restoredNativeUUIDs=2, firstSpawnFromOriginalPaid=1, movedPendingHeld=1, completedLossReplacements=0), "Restart result counts differ")
    directory = restart / "data/catancraft_vehicle47_journal_restart"
    records = journal(directory)
    require(len(records) == 3, "Restart lost original intent inventory")
    hashes(directory, seed["journalFiles"])
    hashes(directory, verified["journalFiles"], True)
    native_ledger = native_nbt(restart / "data/catancraft.dat")["data"]
    require(charge(native_ledger, seed["nationBefore"], records) == seed["nationAfter"], "Restart changed original charges")
    charge(seed["nativeLedger"], seed["nationBefore"], records)
    charge(verified["nativeLedger"], seed["nationBefore"], records)
    require(len(seed["orders"]) == 3 and len(verified["nativeEntitiesBeforeCompletedLoss"]) == 3, "Restart native NBT evidence incomplete")
    final_entities, restart_regions = regions(restart / "entities")
    expected_restart = set()
    for slot, row in enumerate(seed["orders"]):
        record, fp = records[uid(row["record"]["id"])]
        require(row["record"]["phase"] == ["COMPLETE", "PAID", "SPAWN_ATTEMPTED"][slot], "Seed phase boundary differs")
        entity_id = uid(record["entity"])
        if slot != 1:
            entity_proof(row["nativeEntity"], record, fp)
        else:
            require("nativeEntity" not in row, "PAID seed already had a native entity")
        expected_phase = "SPAWN_ATTEMPTED" if slot == 2 else "COMPLETE"
        require(record["phase"] == expected_phase, "Final restart phase changed")
        captured = verified["nativeEntitiesBeforeCompletedLoss"][slot]
        entity_proof(captured["nativeEntity"], record, fp)
        if slot != 1:
            require(captured["nativeEntity"]["Pos"] == row["nativeEntity"]["Pos"], "Seed/restored original native pose differs")
        if slot == 0:
            require(entity_id not in final_entities, "Completed loss recreated original native UUID")
        else:
            expected_restart.add(entity_id)
            require(entity_id in final_entities, "Original pending/first-delivered UUID missing")
            entity_proof(final_entities[entity_id], record, fp)
            if slot == 2:
                require(abs(final_entities[entity_id]["Pos"][0] - expected_provenance(record, fp)["x"]) >= 7.9, "Held pending entity was relocated/replaced")
    require({k for k, e in final_entities.items() if e["id"] in TYPES.values()} == expected_restart, "Unexpected native restart replacement entity")
    require({k.removeprefix("vehicle47_paid:") for k in native_ledger["placedMapAssets"] if k.startswith("vehicle47_paid:")} == set(records), "Unexpected restart vehicle debit receipt")
    return dict(result="PASS", source=source_commit, runtime_manifest_sha256=runtime_sha256,
                base_original_intents=11, base_native_entities_remaining=7, retained_native_snapshots=10,
                independent_canonical_fingerprints=14, malformed_region_fixtures=6,
                restart_original_intents=3, restored_original_uuids=2, original_paid_first_spawn=1,
                moved_pending_held=1, completed_loss_replacements=0, separate_jvms=True,
                seed_process=seed["process"], verify_process=verified["process"],
                actual_entity_region_files=base_regions + restart_regions,
                scope="Native transaction/region/accounting fixtures; not authored factory exit or installed-pack acceptance")


if __name__ == "__main__":
    require(len(sys.argv) in (3, 4), __doc__)
    print(json.dumps(verify_native(*sys.argv[1:]), indent=2))
