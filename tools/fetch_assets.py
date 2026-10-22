#!/usr/bin/env python3
"""Download Outfit, the Gradle wrapper jar, and build assets/egm96-15.bin.

The files are already in a normal checkout. Run this if a copy is missing them
(for example a source-only export). Safe to re-run: existing files are left
alone unless --force is passed.
"""

from __future__ import annotations

import argparse
import io
import struct
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GEOID = ROOT / "app/src/main/assets/egm96-15.bin"
FONT_DIR = ROOT / "app/src/main/res/font"
WRAPPER = ROOT / "gradle/wrapper/gradle-wrapper.jar"

PGM_URL = "https://downloads.sourceforge.net/project/geographiclib/geoids-distrib/egm96-15.tar.bz2"
WRAPPER_URL = "https://raw.githubusercontent.com/gradle/gradle/v9.8.2/gradle/wrapper/gradle-wrapper.jar"
FONT_CSS = "https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&display=swap"
FONT_FILES = {
    400: FONT_DIR / "outfit_regular.ttf",
    500: FONT_DIR / "outfit_medium.ttf",
    600: FONT_DIR / "outfit_semibold.ttf",
    700: FONT_DIR / "outfit_bold.ttf",
    800: FONT_DIR / "outfit_extrabold.ttf",
}


def fetch(url: str, headers: dict[str, str] | None = None) -> bytes:
    request = urllib.request.Request(url, headers=headers or {"User-Agent": "AltitudeAssetFetch"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def write_if_needed(path: Path, data: bytes, force: bool) -> None:
    if path.exists() and not force:
        print(f"keep {path.relative_to(ROOT)}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    print(f"wrote {path.relative_to(ROOT)} ({len(data)} bytes)")


def build_geoid(force: bool) -> None:
    if GEOID.exists() and not force:
        print(f"keep {GEOID.relative_to(ROOT)}")
        return
    print("downloading EGM96 15′ grid")
    archive = fetch(PGM_URL)
    import tarfile

    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:bz2") as tar:
        member = next(item for item in tar.getmembers() if item.name.endswith("egm96-15.pgm"))
        extracted = tar.extractfile(member)
        if extracted is None:
            raise SystemExit("egm96-15.pgm missing from archive")
        pgm = extracted.read()
    header_end = 0
    offset = -108.0
    scale = 0.003
    seen_size = False
    while header_end < len(pgm):
        line_end = pgm.index(b"\n", header_end)
        line = pgm[header_end:line_end].decode("ascii", "replace").strip()
        header_end = line_end + 1
        if line.startswith("# Offset"):
            offset = float(line.split()[-1])
        elif line.startswith("# Scale"):
            scale = float(line.split()[-1])
        elif line.startswith("#") or line == "P5" or line == "":
            continue
        elif not seen_size:
            seen_size = True
        else:
            break
    pixels = pgm[header_end:]
    count = 721 * 1440
    if len(pixels) < count * 2:
        raise SystemExit(f"PGM payload is {len(pixels)} bytes, expected at least {count * 2}")
    centimeters = []
    for index in range(count):
        raw = struct.unpack_from(">H", pixels, index * 2)[0]
        meters = offset + scale * raw
        centimeters.append(int(round(meters * 100.0)))
    out = bytearray()
    out += b"EG96"
    out += struct.pack("<HHHHiiiii", 1, 0, 721, 1440, 900_000, 0, -2500, 2500, 0)
    for value in centimeters:
        out += struct.pack("<h", value)
    if len(out) != 32 + count * 2:
        raise SystemExit(f"unexpected asset length {len(out)}")
    write_if_needed(GEOID, bytes(out), force=True)


def fetch_fonts(force: bool) -> None:
    missing = [path for path in FONT_FILES.values() if force or not path.exists()]
    if not missing:
        for path in FONT_FILES.values():
            print(f"keep {path.relative_to(ROOT)}")
        return
    css = fetch(FONT_CSS, {"User-Agent": "Mozilla/5.0"}).decode("utf-8")
    urls: dict[int, str] = {}
    weight = None
    for line in css.splitlines():
        stripped = line.strip()
        if stripped.startswith("font-weight:"):
            weight = int(stripped.split(":")[1].strip().rstrip(";"))
        elif "url(" in stripped and weight in FONT_FILES:
            start = stripped.index("url(") + 4
            end = stripped.index(")", start)
            urls[weight] = stripped[start:end].strip("'\"")
    for weight, path in FONT_FILES.items():
        if path.exists() and not force:
            print(f"keep {path.relative_to(ROOT)}")
            continue
        url = urls.get(weight)
        if not url:
            raise SystemExit(f"no font URL for weight {weight}")
        write_if_needed(path, fetch(url), force=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    write_if_needed(WRAPPER, fetch(WRAPPER_URL) if args.force or not WRAPPER.exists() else WRAPPER.read_bytes(), args.force)
    fetch_fonts(args.force)
    build_geoid(args.force)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"fetch_assets failed: {error}", file=sys.stderr)
        raise
