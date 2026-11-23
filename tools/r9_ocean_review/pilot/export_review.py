"""Export a verified creative review save and compare every archived file with its source."""
from argparse import ArgumentParser
import hashlib
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED


def digest(stream):
    return hashlib.file_digest(stream, "sha256").hexdigest()


def export(world, notes, extras, output):
    if not (world / "level.dat").is_file() or not (world / "region").is_dir():
        raise ValueError("Expected the pilot's saved world")
    entries = {"START_HERE.txt": notes}
    for doc in extras:
        if doc.name in entries:
            raise ValueError(f"Duplicate document name {doc.name}")
        entries[doc.name] = doc
    for path in sorted(world.rglob("*")):
        if path.is_symlink():
            raise ValueError("Unexpected symlink in review save")
        if path.is_file():
            relative = path.relative_to(world)
            if str(relative) != "level.dat" and relative.parts[0] != "region":
                raise ValueError(f"Unexpected file in unplayed review save: {relative}")
            entries[f"{world.name}/{relative.as_posix()}"] = path
    with ZipFile(output, "x", ZIP_DEFLATED, compresslevel=6) as z:
        for name,path in entries.items():
            z.write(path, name)
    with ZipFile(output) as z:
        if z.namelist() != list(entries):
            raise ValueError("Archive inventory differs")
        for name,path in entries.items():
            with z.open(name) as extracted, path.open("rb") as original:
                if digest(extracted) != digest(original):
                    raise ValueError(f"Archived bytes differ: {name}")
    with output.open("rb") as f:
        print(digest(f), output.name)


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("notes", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("extras", nargs="*", type=Path)
    args = parser.parse_args()
    export(args.world, args.notes, args.extras, args.output)
