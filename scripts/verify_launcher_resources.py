#!/usr/bin/env python3
"""Validate Nanoce launcher icon resources and API-specific variants."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def parse(path: str) -> ET.Element:
    file_path = ROOT / path
    if not file_path.is_file():
        raise AssertionError(f"Missing launcher resource: {path}")
    try:
        return ET.parse(file_path).getroot()
    except ET.ParseError as error:
        raise AssertionError(f"Invalid XML in {path}: {error}") from error


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


manifest = parse("app/src/main/AndroidManifest.xml")
application = manifest.find("application")
require(application is not None, "AndroidManifest.xml has no application element")
require(application.get(ANDROID + "icon") == "@mipmap/ic_launcher",
        "Manifest must reference @mipmap/ic_launcher")
require(application.get(ANDROID + "roundIcon") == "@mipmap/ic_launcher_round",
        "Manifest must reference @mipmap/ic_launcher_round")

foreground_path = "app/src/main/res/drawable/ic_launcher_foreground.xml"
monochrome_path = "app/src/main/res/drawable/ic_launcher_monochrome.xml"
foreground = parse(foreground_path)
monochrome = parse(monochrome_path)
require(foreground.tag == "vector", "Colored foreground must be a vector drawable")
require(monochrome.tag == "vector", "Monochrome layer must be a vector drawable")

foreground_paths = foreground.findall("path")
monochrome_paths = monochrome.findall("path")
require(len(foreground_paths) == 1 and len(monochrome_paths) == 1,
        "Foreground and monochrome layers must each contain one symbol path")
colored_symbol = foreground_paths[0]
mono_symbol = monochrome_paths[0]
require(colored_symbol.get(ANDROID + "pathData") == mono_symbol.get(ANDROID + "pathData"),
        "Monochrome symbol geometry must match the colored foreground")
require(colored_symbol.get(ANDROID + "strokeColor") == "#FFFFFFFF",
        "Launcher foreground must use a white symbol")
require(colored_symbol.get(ANDROID + "pathData") ==
        "M40,34 L22,54 L40,74 M68,34 L86,54 L68,74",
        "Launcher foreground must be the centered angle-bracket mark")
require(mono_symbol.get(ANDROID + "strokeColor") == "#FFFFFFFF",
        "Monochrome symbol must be opaque white for launcher tinting")

for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    legacy = parse(f"app/src/main/res/mipmap-anydpi/{name}")
    require(legacy.tag == "vector", f"Pre-adaptive {name} must be a vector fallback")
    paths = legacy.findall("path")
    require(any(p.get(ANDROID + "fillColor") == "#000000" for p in paths),
            f"Legacy {name} must use a black background")
    require(any(p.get(ANDROID + "strokeColor") == "#FFFFFFFF" for p in paths),
            f"Legacy {name} must use a white symbol")

for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    adaptive = parse(f"app/src/main/res/mipmap-anydpi-v26/{name}")
    require(adaptive.tag == "adaptive-icon", f"API 26+ {name} must be adaptive")
    children = {child.tag: child for child in adaptive}
    require(children.get("background") is not None,
            f"{name} is missing its adaptive background")
    require(children["background"].get(ANDROID + "drawable") == "@color/launcher_background",
            f"{name} has an unexpected adaptive background")
    require(children.get("foreground") is not None,
            f"{name} is missing its adaptive foreground")
    require(children["foreground"].get(ANDROID + "drawable") == "@drawable/ic_launcher_foreground",
            f"{name} has an unexpected adaptive foreground")
    require(children.get("monochrome") is not None,
            f"{name} is missing Android 13+ monochrome artwork")
    require(children["monochrome"].get(ANDROID + "drawable") == "@drawable/ic_launcher_monochrome",
            f"{name} has an unexpected monochrome layer")

colors = parse("app/src/main/res/values/colors.xml")
background = next((item for item in colors.findall("color")
                   if item.get("name") == "launcher_background"), None)
require(background is not None and (background.text or "").strip().upper() == "#000000",
        "launcher_background must be pure black")

print("Launcher resource contract passed (legacy, adaptive, and monochrome variants).")
