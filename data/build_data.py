"""Merge official counter/stat data with lane/role/tag info into heroes.json."""
import json, sys, re

src = open("data/make_heroes.py").read()
ns = {}
exec(src.split("names = set(H)")[0], ns)
H = ns["H"]

# Heroes in the official list that the starter data lacked: (lanes, dmg, tags)
NEW = {
 "Marcel":     ("Roam", "M", "tank cc support"),
 "Argus":      ("Exp Jungle", "P", "dive sustain late"),
 "Minotaur":   ("Roam", "P", "tank cc setup heal"),
 "Obsidia":    ("Gold", "P", "late burst"),
 "Aulus":      ("Exp Jungle", "P", "sustain late"),
 "Sun":        ("Exp Jungle", "P", "split sustain"),
 "Alice":      ("Exp Roam", "M", "sustain dive"),
 "Cyclops":    ("Mid Jungle", "M", "burst cc"),
 "Lukas":      ("Exp", "P", "sustain dive cc"),
 "Bane":       ("Exp Jungle", "P", "poke"),
 "Hilda":      ("Roam Exp", "P", "tank sustain dive"),
 "Zetian":     ("Mid", "M", "poke aoe"),
 "Hanzo":      ("Jungle", "P", "burst"),
 "Suyou":      ("Jungle Exp", "P", "dash dive burst"),
 "Sora":       ("Exp Jungle", "P", "dash dive"),
 "Ruby":       ("Exp Roam", "P", "cc sustain"),
 "Kimmy":      ("Gold Mid", "M", "poke"),
 "Yi Sun-shin":("Jungle Gold", "P", "burst late"),
 "Harley":     ("Jungle Mid", "M", "burst dive dash"),
 "Jawhead":    ("Exp Roam", "P", "cc pick dive"),
 "Hirara":     ("Jungle", "P", "dash dive burst"),
 "Selena":     ("Roam Mid", "M", "cc pick burst"),
 "Zilong":     ("Exp Jungle", "P", "dive split"),
 "Gord":       ("Mid", "M", "poke immobile"),
 "Kalea":      ("Roam", "P", "cc support sustain"),
}
# Old hand-made tags still used for play-style rules (antidash, antiheal, ...)
EXTRA_TAGS = {"Baxia": "antiheal", "Dyrroth": "antiheal", "Karrie": "antitank"}

rows = {}
for line in open("data/official_30d.txt"):
    line = line.strip()
    if not line or line.startswith("#"): continue
    n, p, w, b, c = line.split("|")
    rows[n] = dict(pick=float(p), win=float(w), ban=float(b), counteredBy=c.split(";"))

out, missing = [], []
for n in sorted(rows):
    r = rows[n]
    if n in H:
        lanes, d, tags, _strong, syn = H[n]
        for mw in ("Popol and Kupa", "Yu Zhong", "Luo Yi", "Yi Sun-shin"): syn = syn.replace(mw, mw.replace(" ", "_"))
        syn = [s.replace("_", " ") for s in syn.split()]
    elif n in NEW:
        lanes, d, tags = NEW[n]; syn = []
    else:
        missing.append(n); continue
    for c in r["counteredBy"]:
        if c not in rows: print("unknown counter", n, c)
    out.append({"name": n, "lanes": lanes.split(), "dmg": d, "tags": tags.split() + EXTRA_TAGS.get(n, "").split(),
                "syn": [s for s in syn if s in rows], **r})
if missing: print("MISSING lanes for:", missing); sys.exit(1)
not_official = sorted(set(H) - set(rows)); print("starter-only (dropped):", not_official)
with open(sys.argv[1], "w") as f:
    f.write("[\n" + ",\n".join(json.dumps(o, ensure_ascii=False) for o in out) + "\n]\n")
print(len(out), "heroes")
