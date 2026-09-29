"""Check canonical Expedition PNG manifests; --write refreshes metadata only."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zlib

ASSETS = Path(__file__).resolve().parents[1] / "code/app/src/main/assets"
BANKS = {
    "fruit_templates": (
        "small_lime small_kumquat small_cherry small_strawberry small_blueberry "
        "large_green_apple large_lemon large_orange large_apple large_peach large_plum "
        "giant_melon giant_muscat_grapes giant_grapefruit giant_watermelon "
        "giant_grapes giant_special_fruit"
    ).split(),
    "seedling_templates": (
        "seedling_red seedling_yellow seedling_blue seedling_huge "
        "seedling_purple seedling_white seedling_pink seedling_black"
    ).split(),
}


def png_metadata(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Invalid PNG signature: {path}")
    offset, size, ended = 8, None, False
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError(f"Truncated PNG: {path}")
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:end - 4]
        crc = struct.unpack_from(">I", data, end - 4)[0]
        if zlib.crc32(kind + payload) != crc:
            raise ValueError(f"PNG CRC mismatch: {path}")
        if offset == 8:
            if kind != b"IHDR" or length != 13:
                raise ValueError(f"Missing PNG header: {path}")
            size = list(struct.unpack_from(">II", payload))
            if min(size) <= 0:
                raise ValueError(f"Empty PNG: {path}")
        offset = end
        if kind == b"IEND":
            ended = length == 0 and end == len(data)
            break
    if not ended:
        raise ValueError(f"Missing or invalid PNG end: {path}")
    return {"size": size, "sha256": hashlib.sha256(data).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--inspect", action="store_true",
                      help="Validate PNGs and show current metadata without trusting old manifests")
    args = parser.parse_args()
    manifests = {}
    for bank, names in BANKS.items():
        directory = ASSETS / bank
        expected = {name + ".png" for name in names}
        actual = {p.name for p in directory.glob("*.png")}
        if actual != expected:
            raise ValueError(f"{bank}: missing={expected - actual}, extra={actual - expected}")
        manifests[directory / "manifest.json"] = {
            "count": len(expected),
            "files": {name: png_metadata(directory / name) for name in sorted(expected)},
        }
    # Validate both banks before writing either manifest. Never modify PNG bytes.
    for path, manifest in manifests.items():
        if args.inspect:
            print(json.dumps({path.parent.name: manifest}, indent=2))
            continue
        elif args.write:
            path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        elif json.loads(path.read_text(encoding="utf-8-sig")) != manifest:
            raise ValueError(f"Stale manifest: {path}; use --write only after asset approval")
        print(f"PASS {path.parent.name}: {manifest['count']} PNGs, dimensions/CRC/SHA-256")


if __name__ == "__main__":
    main()
