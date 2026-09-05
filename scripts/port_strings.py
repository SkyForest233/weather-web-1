#!/usr/bin/env python3
"""Ports drizz.li's inlang message files (messages/<locale>.json) into
Android string resources (values-*/strings.xml).

Conventions:
- `{name}` placeholders become Android positional args `%n$s` (same order as
  the source, which is also the order Kotlin code passes them in).
- Strings are marked `formatted=false` when they contain no placeholders, to
  keep apostrophes/quotes painless; otherwise they are escaped.
- Keys that make no sense on a phone (service-worker/PWA/URL plumbing) are
  skipped.
"""
import json, os, re, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/drizz/messages"
OUT = sys.argv[2] if len(sys.argv) > 2 else "android/app/src/main/res"

SKIP_KEYS = {
    "$schema",
    "install_prompt", "update_ready", "update_reload", "lang_note",
    "legal_nav", "footer_source", "footer_data", "footer_license",
    "footer_openmeteo", "footer_geonames", "footer_made", "footer_imprint",
    "error_404_title", "error_404_text", "error_500_title", "error_500_text",
    "maps_open_fullscreen", "maps_embed_hint",
}

def escape(s: str) -> str:
    out = s.replace("\\", "\\\\")
    out = out.replace("'", "\\'").replace('"', '\\"')
    out = re.sub(r"\n\s*", " ", out)
    return out

def convert_placeholders(s: str) -> tuple[str, list[str]]:
    """{foo} -> %1$s ... returns (converted, names in order of appearance)."""
    names = []
    def repl(m):
        names.append(m.group(1))
        return f"%{len(names)}$s"
    return re.sub(r"\{([a-zA-Z0-9_]+)\}", repl, s), names

def fmt_xml(name: str, value: str) -> str:
    value, names = convert_placeholders(value)
    if names:
        return f'    <string name="{name}">{escape(value)}</string>'
    return f'    <string name="{name}" formatted="false">{escape(value)}</string>'

locales = {}
for fn in sorted(os.listdir(SRC)):
    if fn.endswith(".json"):
        locales[fn[:-5]] = json.load(open(os.path.join(SRC, fn)))

base = locales["en"]
for locale, msgs in locales.items():
    dir_name = "values" if locale == "en" else f"values-{locale}"
    os.makedirs(os.path.join(OUT, dir_name), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- Auto-generated from drizz.li messages/%s.json – do not edit by hand. -->" % locale,
             "<resources>"]
    missing = 0
    for key in base:
        if key in SKIP_KEYS:
            continue
        val = msgs.get(key)
        if val is None:
            missing += 1
            val = base[key]
        lines.append(fmt_xml(key, val))
    lines.append("</resources>")
    path = os.path.join(OUT, dir_name, "strings.xml")
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{path}: {len(base)} keys ({missing} missing, fell back to en)")
