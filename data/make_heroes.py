"""Generates app/src/main/assets/heroes.json (starter data) and validates names."""
import json, sys

# name: (lanes, damage P/M, tags, strong_vs, synergy)
H = {
 # ---------- Roam / Tank / Support ----------
 "Khufra":    ("Roam", "P", "tank cc antidash setup", "Fanny Ling Lancelot Wanwan Hayabusa Gusion Benedetta Chou Joy", "Pharsa Lunox"),
 "Minsitthar":("Roam Exp", "P", "cc antidash", "Fanny Ling Lancelot Gusion Hayabusa Benedetta Wanwan Joy", "Layla Miya"),
 "Kaja":      ("Roam", "M", "cc antidive", "Fanny Ling Hayabusa Lancelot Karrie Wanwan Beatrix", "Saber Gusion"),
 "Tigreal":   ("Roam", "P", "tank cc setup", "Layla Miya Lylia Pharsa Kagura", "Odette Pharsa Lunox Yve"),
 "Atlas":     ("Roam", "P", "tank cc setup", "Layla Miya Lunox Pharsa Yve", "Odette Pharsa Kagura Valir"),
 "Franco":    ("Roam", "P", "tank cc pick", "Layla Miya Pharsa Yve Lylia Beatrix", "Saber Gusion"),
 "Grock":     ("Roam Exp", "P", "tank cc", "Fanny Ling Lancelot", "Pharsa Yve"),
 "Hylos":     ("Roam", "P", "tank cc sustain", "Layla Miya Lancelot", "Layla Claude"),
 "Johnson":   ("Roam", "P", "tank cc setup", "Layla Miya Pharsa", "Odette Vale Yve"),
 "Lolita":    ("Roam", "P", "tank cc antipoke", "Pharsa Layla Miya Clint Hanabi Moskov", "Kagura Lunox"),
 "Belerick":  ("Roam", "P", "tank cc", "Miya Moskov Bruno Claude", "Pharsa"),
 "Gatotkaca": ("Roam Exp", "M", "tank cc setup", "Fanny Hayabusa Lancelot", "Pharsa Yve"),
 "Mathilda":  ("Roam", "M", "cc mobility", "Layla Miya", "Gusion Lancelot"),
 "Angela":    ("Roam", "M", "heal support", "", "Ling Lancelot Fanny Granger"),
 "Estes":     ("Roam", "M", "heal support", "", "Layla Miya Irithel"),
 "Rafaela":   ("Roam", "M", "heal support", "", "Layla Hanabi"),
 "Floryn":    ("Roam", "M", "heal support", "", "Layla Moskov Esmeralda"),
 "Diggie":    ("Roam", "M", "purify support", "Tigreal Atlas Franco Khufra Kaja Grock Lolita", "Layla Claude Moskov"),
 "Chip":      ("Roam", "P", "tank support mobility", "", "Ling Lancelot Fanny"),
 "Carmilla":  ("Roam", "M", "tank cc setup", "Gusion Lancelot", "Cecilion Pharsa"),
 "Edith":     ("Roam Exp", "M", "tank cc", "Fanny Ling Lancelot", "Pharsa Yve"),
 "Gloo":      ("Roam Exp", "M", "tank cc sustain", "Hayabusa Gusion", "Pharsa"),
 # ---------- Exp lane ----------
 "Yu Zhong":  ("Exp", "P", "sustain heal dive", "Chou Paquito Dyrroth", "Pharsa"),
 "Esmeralda": ("Exp Roam", "M", "sustain shield", "X.Borg Uranus Khaleed Barats Yu Zhong", "Floryn Estes"),
 "Uranus":    ("Exp", "M", "tank sustain heal", "Martis Freya Alucard", "Pharsa"),
 "Thamuz":    ("Exp", "P", "sustain dive", "Alucard Freya Badang", "Atlas"),
 "Dyrroth":   ("Exp", "P", "burst dive antitank", "Esmeralda Uranus Thamuz Alucard", "Atlas"),
 "Paquito":   ("Exp", "P", "burst dive dash", "Layla Miya Lunox", "Atlas Tigreal"),
 "Terizla":   ("Exp", "P", "tank cc setup", "Chou Paquito", "Pharsa Yve"),
 "Badang":    ("Exp", "P", "cc burst", "Fanny Ling", "Atlas"),
 "Chou":      ("Exp Roam", "P", "cc dash antidash pick", "Fanny Lancelot Ling Gusion Hayabusa Wanwan", "Pharsa"),
 "Arlott":    ("Exp", "P", "cc dash dive", "Layla Miya Pharsa", "Atlas"),
 "Cici":      ("Exp", "P", "sustain dash", "Chou Paquito", "Atlas"),
 "X.Borg":    ("Exp", "P", "antitank sustain", "Uranus Hylos Esmeralda", "Atlas Tigreal"),
 "Khaleed":   ("Exp", "P", "sustain dive", "Layla Miya", "Atlas"),
 "Phoveus":   ("Exp", "M", "antidash burst", "Fanny Ling Lancelot Wanwan Benedetta Joy Hayabusa Gusion Chou", "Atlas"),
 "Masha":     ("Exp", "P", "antitank split", "Hylos Uranus Tigreal", ""),
 "Freya":     ("Exp", "P", "dive dash", "Layla Miya Lunox", "Estes"),
 "Silvanna":  ("Exp", "M", "cc dive", "Lunox Kagura Pharsa", "Atlas"),
 "Benedetta": ("Exp Jungle", "P", "dash dive burst", "Layla Miya Lunox Clint", "Atlas"),
 "Lapu-Lapu": ("Exp", "P", "dive cc", "Layla Miya", "Atlas"),
 "Aldous":    ("Exp", "P", "burst pick late", "Layla Miya Pharsa Lunox", "Chip"),
 "Guinevere": ("Exp Jungle", "M", "burst dive cc", "Layla Miya Pharsa Lunox", "Atlas"),
 # ---------- Mid lane ----------
 "Kagura":    ("Mid", "M", "burst poke mobility", "Layla Miya Pharsa", "Lolita Atlas"),
 "Lunox":     ("Mid", "M", "burst sustain late", "Tigreal Hylos Uranus Esmeralda", "Atlas Khufra Lolita"),
 "Valentina": ("Mid", "M", "burst sustain", "Lunox Esmeralda Uranus", "Atlas"),
 "Yve":       ("Mid", "M", "poke aoe immobile", "Thamuz Uranus Hylos", "Tigreal Atlas Johnson"),
 "Pharsa":    ("Mid", "M", "poke aoe immobile", "Tigreal Hylos Johnson", "Atlas Tigreal Khufra"),
 "Xavier":    ("Mid", "M", "poke aoe", "Layla Miya Pharsa", "Atlas"),
 "Valir":     ("Mid", "M", "antidive poke cc", "Fanny Ling Lancelot Gusion Saber Hayabusa Chou Lapu-Lapu", "Atlas"),
 "Kadita":    ("Mid Roam", "M", "burst cc aoe", "Fanny Lancelot Gusion", "Atlas Tigreal"),
 "Cecilion":  ("Mid", "M", "poke aoe late immobile", "Tigreal Hylos Uranus", "Carmilla"),
 "Vexana":    ("Mid", "M", "cc aoe setup immobile", "Layla Miya", "Atlas"),
 "Lylia":     ("Mid", "M", "poke aoe", "Hylos Tigreal", "Atlas"),
 "Luo Yi":    ("Mid", "M", "cc aoe setup", "Layla Miya", "Atlas"),
 "Novaria":   ("Mid", "M", "poke longrange", "Layla Miya Pharsa", "Atlas"),
 "Zhask":     ("Mid", "M", "poke antitank immobile", "Tigreal Hylos", "Atlas"),
 "Eudora":    ("Mid", "M", "burst cc immobile", "Layla Miya Fanny Ling", "Atlas"),
 "Aurora":    ("Mid", "M", "burst cc aoe immobile", "Ling Fanny Lancelot", "Atlas"),
 "Harith":    ("Mid Gold", "M", "dash sustain", "Layla Miya Lunox", "Atlas"),
 "Nana":      ("Mid Roam", "M", "cc poke", "Lancelot Gusion", "Atlas"),
 "Chang'e":   ("Mid", "M", "poke late", "Tigreal Hylos", "Atlas"),
 "Faramis":   ("Mid Roam", "M", "support aoe", "", "Atlas Tigreal"),
 "Zhuxin":    ("Mid", "M", "cc aoe", "Layla Miya", "Atlas"),
 # ---------- Jungle ----------
 "Fanny":     ("Jungle", "P", "dash dive burst", "Layla Miya Lunox Pharsa Eudora Yve Kagura", "Angela Chip"),
 "Ling":      ("Jungle", "P", "dash dive burst", "Layla Miya Pharsa Lylia Eudora", "Angela Chip"),
 "Lancelot":  ("Jungle", "P", "dash dive burst", "Layla Miya Pharsa Lylia Kagura Clint", "Angela Mathilda"),
 "Hayabusa":  ("Jungle", "P", "dash dive burst", "Layla Miya Pharsa Lylia Lunox", "Angela"),
 "Gusion":    ("Jungle Mid", "M", "dash dive burst", "Layla Miya Pharsa Eudora Lunox Clint", "Mathilda Franco Kaja"),
 "Joy":       ("Jungle", "M", "dash dive", "Layla Miya Pharsa", "Atlas"),
 "Fredrinn":  ("Jungle Exp", "P", "tank sustain heal cc", "Gusion Lancelot", "Pharsa"),
 "Baxia":     ("Jungle Roam", "M", "tank antiheal", "Estes Esmeralda Alucard Uranus Rafaela Floryn Fredrinn Yu Zhong", "Pharsa"),
 "Barats":    ("Jungle Exp", "P", "tank cc", "Layla Miya", "Pharsa"),
 "Akai":      ("Jungle Roam", "P", "tank cc setup", "Gusion Lancelot Ling", "Pharsa Yve"),
 "Martis":    ("Jungle Exp", "P", "dive burst", "Layla Miya Lunox Eudora", "Atlas"),
 "Yin":       ("Jungle Exp", "P", "burst pick", "Layla Miya Lunox", "Atlas"),
 "Nolan":     ("Jungle", "P", "dash dive burst", "Layla Miya Pharsa", "Atlas"),
 "Julian":    ("Jungle Exp", "M", "burst cc dive", "Layla Miya", "Atlas"),
 "Aamon":     ("Jungle", "P", "burst dive", "Layla Miya Pharsa", "Atlas"),
 "Alucard":   ("Jungle Exp", "P", "sustain heal dive", "Layla Miya", "Angela"),
 "Balmond":   ("Jungle Exp", "P", "sustain", "Layla Miya", "Atlas"),
 "Karina":    ("Jungle", "M", "burst dive", "Layla Miya Pharsa", "Atlas"),
 "Helcurt":   ("Jungle", "P", "burst pick dive", "Layla Miya Lunox Kagura Yve Pharsa", "Atlas"),
 "Saber":     ("Jungle Roam", "P", "burst pick dive", "Layla Miya Pharsa Eudora Clint Ling", "Kaja Franco"),
 "Natalia":   ("Jungle Roam", "P", "burst pick dive", "Layla Miya Clint Pharsa Eudora", "Atlas"),
 "Leomord":   ("Jungle Exp", "P", "dive", "Layla Miya", "Atlas"),
 "Roger":     ("Jungle Gold", "P", "burst dive", "Layla Miya", "Atlas"),
 "Alpha":     ("Jungle Exp", "P", "dive cc sustain", "Layla Miya", "Atlas"),
 "Odette":    ("Mid", "M", "aoe burst immobile", "Layla Miya", "Atlas Tigreal Johnson"),
 "Vale":      ("Mid", "M", "aoe burst cc", "Layla Miya", "Johnson Atlas"),
 # ---------- Gold lane ----------
 "Layla":     ("Gold", "P", "late longrange immobile", "Tigreal Hylos", "Estes Floryn Diggie"),
 "Miya":      ("Gold", "P", "late immobile", "Tigreal Uranus Hylos", "Estes Diggie Minsitthar"),
 "Karrie":    ("Gold", "P", "antitank late", "Tigreal Atlas Uranus Grock Hylos Belerick Esmeralda Fredrinn Baxia Johnson", "Atlas"),
 "Claude":    ("Gold", "P", "dash late", "Grock Hylos Layla", "Diggie Hylos"),
 "Wanwan":    ("Gold", "P", "dash late", "Claude Layla Atlas", "Angela Mathilda"),
 "Beatrix":   ("Gold", "P", "burst antitank", "Esmeralda Hylos Uranus Tigreal", "Atlas"),
 "Moskov":    ("Gold", "P", "late longrange", "Hylos Tigreal Atlas", "Diggie Lolita"),
 "Granger":   ("Gold Jungle", "P", "burst", "Layla Miya Lunox", "Angela"),
 "Brody":     ("Gold", "P", "burst early", "Layla Miya Wanwan", "Atlas"),
 "Bruno":     ("Gold", "P", "burst", "Layla Miya", "Atlas"),
 "Clint":     ("Gold", "P", "burst early", "Layla Miya", "Atlas"),
 "Irithel":   ("Gold", "P", "late", "Tigreal Hylos", "Estes"),
 "Hanabi":    ("Gold", "P", "late aoe immobile", "Tigreal Atlas Franco", "Rafaela"),
 "Melissa":   ("Gold", "P", "antidive late", "Lancelot Fanny Ling Gusion", "Atlas"),
 "Natan":     ("Gold", "M", "antitank late", "Esmeralda Uranus", "Atlas"),
 "Popol and Kupa": ("Gold", "P", "cc antidash", "Ling Fanny Lancelot", "Atlas"),
 "Ixia":      ("Gold", "P", "sustain late immobile", "Tigreal Hylos", "Atlas Tigreal"),
 "Lesley":    ("Gold", "P", "burst longrange", "Layla Miya Pharsa", "Atlas"),
}

names = set(H)
out, bad = [], []
for n, (lanes, d, tags, strong, syn) in H.items():
    s = [x.strip() for x in strong.replace("Popol and Kupa", "Popol_and_Kupa").replace("Yu Zhong", "Yu_Zhong").replace("Luo Yi", "Luo_Yi").split()]
    y = [x.strip() for x in syn.replace("Popol and Kupa", "Popol_and_Kupa").replace("Yu Zhong", "Yu_Zhong").replace("Luo Yi", "Luo_Yi").split()]
    s = [x.replace("_", " ") for x in s]; y = [x.replace("_", " ") for x in y]
    for x in s + y:
        if x not in names: bad.append((n, x))
    out.append({"name": n, "lanes": lanes.split(), "dmg": d, "tags": tags.split(), "strong": s, "syn": y})
if bad:
    print("Unknown names:", bad); sys.exit(1)
path = sys.argv[1]
with open(path, "w") as f:
    f.write("[\n" + ",\n".join(json.dumps(o, ensure_ascii=False) for o in out) + "\n]\n")
print(len(out), "heroes written")
