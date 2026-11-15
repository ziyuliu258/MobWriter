#!/usr/bin/env python3
"""Build the bundled article serif from Source Serif 4 and Noto Serif SC.

Latin, digits and Latin punctuation come from Source Serif 4. Simplified
Chinese and fullwidth punctuation come from Noto Serif SC (Source Han Serif).
Both are OFL. Titles use the regular instance; emphasis uses a real semibold
so the system does not paint a fake bold.

The CJK face is limited to GB2312 plus the general-standard characters when a
list can be downloaded. Glyphs outside that set fall back to the system font.
"""

from __future__ import annotations

import urllib.request
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.merge import Merger
from fontTools.subset import Options, Subsetter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "res" / "font"
LICENSE = ROOT / "third_party" / "fonts"
CACHE = Path(__file__).resolve().parent / ".font-cache"

SOURCE_SERIF = "https://github.com/google/fonts/raw/main/ofl/sourceserif4/SourceSerif4%5Bopsz%2Cwght%5D.ttf"
SOURCE_OFL = "https://github.com/google/fonts/raw/main/ofl/sourceserif4/OFL.txt"
NOTO = "https://github.com/google/fonts/raw/main/ofl/notoserifsc/NotoSerifSC%5Bwght%5D.ttf"
NOTO_OFL = "https://github.com/google/fonts/raw/main/ofl/notoserifsc/OFL.txt"
CHARS = "https://raw.githubusercontent.com/caryang/common-chinese-characters/master/8105.txt"

LATIN = set(range(0x0000, 0x0250)) | set(range(0x2000, 0x2070)) | set(range(0x20A0, 0x20D0))
CJK_PUNCT = set(range(0x3000, 0x3040)) | set(range(0xFF00, 0xFF66))


def fetch(url: str, dest: Path) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    request = urllib.request.Request(url, headers={"User-Agent": "mobwriter-font-build"})
    with urllib.request.urlopen(request, timeout=180) as response:
        dest.write_bytes(response.read())
    return dest


def gb2312() -> set[str]:
    found = set()
    for lead in range(0xB0, 0xF8):
        for trail in range(0xA1, 0xFF):
            try:
                found.add(bytes((lead, trail)).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return found


def characters() -> set[int]:
    text = set(gb2312())
    try:
        raw = fetch(CHARS, CACHE / "8105.txt").read_text(encoding="utf-8")
        text.update(ch for ch in raw if "\u4e00" <= ch <= "\u9fff")
    except Exception as error:
        print(f"common-character list unavailable ({error}); using GB2312")
    return {ord(ch) for ch in text} | CJK_PUNCT


def source_serif_regular() -> TTFont:
    fetch(SOURCE_OFL, LICENSE / "SourceSerif4-OFL.txt")
    variable = TTFont(fetch(SOURCE_SERIF, CACHE / "SourceSerif4[opsz,wght].ttf"))
    axes = {axis.axisTag for axis in variable["fvar"].axes}
    location = {"wght": 400}
    if "opsz" in axes:
        location["opsz"] = 16
    font = instantiateVariableFont(variable, location, inplace=False, overlap=True)
    options = Options()
    options.layout_features = ["kern", "liga", "calt"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.hinting = False
    subset = Subsetter(options=options)
    subset.populate(unicodes=LATIN)
    subset.subset(font)
    return staticize(font)


def noto(weight: int, unicodes: set[int]) -> TTFont:
    fetch(NOTO_OFL, LICENSE / "NotoSerifSC-OFL.txt")
    variable = TTFont(fetch(NOTO, CACHE / "NotoSerifSC[wght].ttf"))
    font = instantiateVariableFont(variable, {"wght": weight}, inplace=False, overlap=True)
    options = Options()
    options.layout_features = ["kern"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.hinting = False
    options.drop_tables += ["DSIG"]
    subset = Subsetter(options=options)
    subset.populate(unicodes=unicodes)
    subset.subset(font)
    return staticize(font)


def staticize(font: TTFont) -> TTFont:
    for tag in ("fvar", "gvar", "HVAR", "VVAR", "MVAR", "STAT", "avar", "cvar", "DSIG", "GDEF", "GPOS", "GSUB", "JSTF", "BASE"):
        if tag in font:
            del font[tag]
    return font


def save(font: TTFont, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    font.save(path)
    print(f"{path.name}  {path.stat().st_size} bytes")


KEEP = {"cmap", "glyf", "head", "hhea", "hmtx", "loca", "maxp", "name", "post", "OS/2"}


def slim(path: Path) -> None:
    font = TTFont(path)
    for tag in list(font.keys()):
        if tag not in KEEP:
            del font[tag]
    font.save(path)


def merged(latin: TTFont, cjk: TTFont, dest: Path) -> None:
    scratch = CACHE / "merge"
    scratch.mkdir(parents=True, exist_ok=True)
    latin_path = scratch / f"latin-{dest.stem}.ttf"
    cjk_path = scratch / f"cjk-{dest.stem}.ttf"
    latin.save(latin_path)
    cjk.save(cjk_path)
    slim(latin_path)
    slim(cjk_path)
    font = Merger().merge([str(latin_path), str(cjk_path)])
    save(font, dest)
    cmap = {code for table in font["cmap"].tables for code in table.cmap}
    missing = [ch for ch in "晚上好，JadeKing258" if ord(ch) not in cmap]
    if missing:
        raise SystemExit(f"{dest.name} is missing {missing}")


def main() -> None:
    unicodes = characters()
    print(f"cjk code points {len(unicodes)}")
    latin = source_serif_regular()
    merged(latin, noto(400, unicodes), OUT / "book_serif_regular.ttf")
    merged(source_serif_regular(), noto(600, unicodes), OUT / "book_serif_semibold.ttf")


if __name__ == "__main__":
    main()
