#!/usr/bin/env python3
"""Generates the bundled nearby-cities dataset for the Android app.

Source: GeoNames (CC BY 4.0) via the cities500 mirror used by drizz.li
(https://github.com/lmfmaier/cities-json). Same population cutoff and row
format as the website's tile builder (scripts/build-cities.mjs), but shipped
as one flat, population-sorted list in the APK assets instead of tiles — a
phone has the memory to just hold it all.
"""
import json, sys, os

SOURCE = sys.argv[1] if len(sys.argv) > 1 else "/tmp/cities-json/cities500.json"
OUT = sys.argv[2] if len(sys.argv) > 2 else "android/app/src/main/assets/data/cities.json"
MIN_POPULATION = 20_000

with open(SOURCE) as f:
    raw = json.load(f)

cities = []
for c in raw:
    try:
        lat, lon, pop = float(c["lat"]), float(c["lon"]), float(c.get("pop") or 0)
    except (KeyError, TypeError, ValueError):
        continue
    if lat != lat or lon != lon or pop < MIN_POPULATION:
        continue
    cities.append([int(c["id"]), c["name"], c.get("country") or "", round(lat, 3), round(lon, 3), int(pop // 1000)])

cities.sort(key=lambda r: -r[5])
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w") as f:
    json.dump(cities, f, ensure_ascii=False, separators=(",", ":"))
print(f"wrote {len(cities)} cities, {os.path.getsize(OUT)/1024/1024:.1f} MB -> {OUT}")
