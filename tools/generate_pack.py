#!/usr/bin/env python3
"""Erzeugt das LobbySystem-Ressourcenpaket (Icons als Font-Glyphen) – deterministisch.
Ausgabe: resourcepack/ (Quellordner) und src/main/resources/pack/LobbyPack.zip
Aufruf:  python3 tools/generate_pack.py
"""
import json, os, zipfile, io
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepack")
ZIP = os.path.join(ROOT, "src", "main", "resources", "pack", "LobbyPack.zip")
S = 16  # Pixel pro Icon

def img():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))

def px(d, pts, c):
    for x, y in pts:
        d.point((x, y), fill=c)

def coin():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=(181, 120, 18, 255))
    d.ellipse((2, 2, 13, 13), fill=(255, 196, 40, 255))
    d.ellipse((4, 4, 11, 11), fill=(240, 170, 25, 255))
    d.rectangle((7, 5, 8, 10), fill=(255, 230, 120, 255))
    px(d, [(4, 3), (3, 4), (5, 3)], (255, 245, 190, 255))
    return im

def clock():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=(60, 90, 140, 255))
    d.ellipse((2, 2, 13, 13), fill=(235, 240, 250, 255))
    d.line((7, 7, 7, 3), fill=(40, 40, 60, 255))
    d.line((7, 7, 10, 9), fill=(40, 40, 60, 255))
    for x, y in [(7, 2), (12, 7), (7, 12), (2, 7)]:
        d.point((x, y), fill=(60, 90, 140, 255))
    d.point((7, 7), fill=(220, 50, 50, 255))
    return im

def star(color=(190, 110, 255, 255), dark=(120, 50, 190, 255)):
    im = img(); d = ImageDraw.Draw(im)
    pts = [(7.5, 0.5), (9.6, 5.3), (14.8, 5.6), (10.8, 9.0), (12.1, 14.2), (7.5, 11.4), (2.9, 14.2), (4.2, 9.0), (0.2, 5.6), (5.4, 5.3)]
    d.polygon(pts, fill=dark)
    inner = [((x - 7.5) * 0.72 + 7.5, (y - 8) * 0.72 + 8) for x, y in pts]
    d.polygon(inner, fill=color)
    px(d, [(7, 3), (6, 5)], (240, 220, 255, 255))
    return im

def shield():
    im = img(); d = ImageDraw.Draw(im)
    outline = [(1, 1), (14, 1), (14, 8), (7.5, 15), (1, 8)]
    d.polygon(outline, fill=(120, 70, 10, 255))
    d.polygon([(2.5, 2.5), (12.5, 2.5), (12.5, 8), (7.5, 13.2), (2.5, 8)], fill=(230, 160, 30, 255))
    d.rectangle((7, 3, 8, 11), fill=(200, 40, 40, 255))
    d.rectangle((3, 6, 12, 7), fill=(200, 40, 40, 255))
    return im

def friends():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 3, 6, 8), fill=(80, 200, 220, 255))
    d.pieslice((0, 8, 7, 17), 180, 360, fill=(40, 150, 180, 255))
    d.ellipse((8, 1, 14, 7), fill=(255, 255, 255, 255))
    d.pieslice((7, 7, 15, 17), 180, 360, fill=(200, 200, 210, 255))
    return im

def party():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((2, 0, 9, 8), fill=(230, 70, 160, 255))
    d.ellipse((7, 2, 14, 10), fill=(120, 200, 255, 255))
    px(d, [(4, 2), (9, 4)], (255, 255, 255, 220))
    d.line((5, 8, 7, 15), fill=(220, 220, 220, 255))
    d.line((10, 10, 8, 15), fill=(220, 220, 220, 255))
    return im

def heart():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 2, 8, 9), fill=(220, 30, 50, 255))
    d.ellipse((7, 2, 14, 9), fill=(220, 30, 50, 255))
    d.polygon([(1.5, 7), (13.5, 7), (7.5, 14)], fill=(220, 30, 50, 255))
    px(d, [(4, 4), (3, 5)], (255, 170, 180, 255))
    return im

def ticket():
    im = img(); d = ImageDraw.Draw(im)
    d.rectangle((1, 4, 14, 11), fill=(255, 210, 60, 255))
    d.rectangle((1, 4, 14, 4), fill=(200, 140, 20, 255))
    d.rectangle((1, 11, 14, 11), fill=(200, 140, 20, 255))
    for y in range(5, 11, 2):
        d.point((10, y), fill=(200, 140, 20, 255))
    d.ellipse((0, 6, 2, 9), fill=(0, 0, 0, 0))
    d.ellipse((13, 6, 15, 9), fill=(0, 0, 0, 0))
    d.rectangle((4, 7, 7, 8), fill=(200, 60, 40, 255))
    return im

def dot(c, dark):
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((3, 3, 12, 12), fill=dark)
    d.ellipse((4, 4, 11, 11), fill=c)
    px(d, [(6, 5), (5, 6)], (255, 255, 255, 200))
    return im

def gem():
    im = img(); d = ImageDraw.Draw(im)
    d.polygon([(4, 2), (11, 2), (14, 6), (7.5, 14), (1, 6)], fill=(30, 160, 200, 255))
    d.polygon([(5, 3), (10, 3), (12, 6), (7.5, 12), (3, 6)], fill=(90, 220, 255, 255))
    d.line((3, 6, 12, 6), fill=(30, 160, 200, 255))
    return im

ICONS = [
    ("coin", "", coin()), ("clock", "", clock()), ("rank", "", star()),
    ("clan", "", shield()), ("friends", "", friends()), ("party", "", party()),
    ("heart", "", heart()), ("ticket", "", ticket()),
    ("online", "", dot((80, 230, 90, 255), (30, 140, 40, 255))),
    ("offline", "", dot((235, 70, 70, 255), (150, 30, 30, 255))),
    ("gem", "", gem()),
]

# ---------------------------------------------------------------- Rang-Badges
# 5x7-Pixelschrift (doppelte Auflösung: Badge 16 px hoch → im Spiel 8 px, gestochen scharf)
FONT = {
 "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
 "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
 "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
 "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
 "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
 "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
 "G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"],
 "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
 "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
 "J": ["..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##.."],
 "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
 "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
 "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
 "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
 "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
 "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
 "Q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
 "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
 "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
 "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
 "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
 "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
 "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."],
 "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
 "Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
 "Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
 "+": [".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."],
 " ": ["..", "..", "..", "..", "..", "..", ".."],
}

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

def badge(text, color):
    """Abgerundetes Badge: Farbverlauf, dunkler Rand, weiße Schrift mit Schatten."""
    widths = [len(FONT[ch][0]) for ch in text]
    tw = sum(widths) + len(text) - 1
    W, H = tw + 10, 16
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    border = shade(color, 0.55)
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=3, fill=border)
    for y in range(1, H - 1):
        f = 1.25 - 0.45 * (y / (H - 2))          # oben heller, unten dunkler
        d.line((1, y, W - 2, y), fill=shade(color, f))
    # Ecken innen sauber halten
    d.rounded_rectangle((1, 1, W - 2, H - 2), radius=2, outline=None)
    px(d, [(1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)], border)
    x, y0 = 5, 4
    for ch, w in zip(text, widths):
        rows = FONT[ch]
        for ry, row in enumerate(rows):
            for rx, v in enumerate(row):
                if v == "#":
                    d.point((x + rx + 1, y0 + ry + 1), fill=shade(color, 0.35))   # Schatten
        for ry, row in enumerate(rows):
            for rx, v in enumerate(row):
                if v == "#":
                    d.point((x + rx, y0 + ry), fill=(255, 255, 255, 255))
        x += w + 1
    return im

# key, Zeichen, Text, Farbe  (Keys passen automatisch zu den Rangnamen in ranks.json)
BADGES = [
    ("owner",       "", "OWNER",    (170, 0, 0)),
    ("leitung",     "", "LEITUNG",  (170, 0, 0)),
    ("admin",       "", "ADMIN",    (220, 40, 40)),
    ("dev",         "", "DEV",      (0, 150, 170)),
    ("mod",         "", "MOD",      (0, 140, 40)),
    ("sup",         "", "SUP",      (40, 170, 70)),
    ("builder",     "", "BUILDER",  (200, 160, 0)),
    ("content",     "", "CONTENT",  (150, 60, 200)),
    ("youtuber",    "", "YOUTUBER", (200, 30, 60)),
    ("premiumplus", "", "PREMIUM+", (230, 140, 0)),
    ("premium",     "", "PREMIUM",  (220, 160, 20)),
    ("vip",         "", "VIP",      (60, 120, 230)),
    ("spieler",     "", "SPIELER",  (110, 110, 120)),
    ("team",        "", "TEAM",     (90, 60, 200)),
]

def pack_png():
    im = Image.new("RGBA", (128, 128))
    d = ImageDraw.Draw(im)
    for y in range(128):
        t = y / 127
        d.line((0, y, 127, y), fill=(int(0 + 58 * t), int(210 - 87 * t), int(255 - 42 * t), 255))
    big = coin().resize((96, 96), Image.NEAREST)
    im.alpha_composite(big, (16, 16))
    return im

def main():
    files = {}
    # Kompatibel mit allen Versionen:
    #  <1.20.2 liest pack_format | 1.20.2–1.21.8 liest supported_formats | ab 1.21.9/26.x min_format/max_format
    mcmeta = {
        "pack": {
            "description": "§bLobbySystem §7– Icons & UI",
            "pack_format": 15,
            "supported_formats": {"min_inclusive": 15, "max_inclusive": 9999},
            "min_format": 15,
            "max_format": 9999
        }
    }
    files["pack.mcmeta"] = json.dumps(mcmeta, indent=2, ensure_ascii=False).encode()
    providers = []
    for name, ch, im in ICONS:
        buf = io.BytesIO(); im.save(buf, "PNG", optimize=True)
        files[f"assets/lobbysystem/textures/font/{name}.png"] = buf.getvalue()
        providers.append({"type": "bitmap", "file": f"lobbysystem:font/{name}.png", "ascent": 8, "height": 9, "chars": [ch]})
    for key, ch, text, color in BADGES:
        buf = io.BytesIO(); badge(text, color).save(buf, "PNG", optimize=True)
        files[f"assets/lobbysystem/textures/font/rank/{key}.png"] = buf.getvalue()
        providers.append({"type": "bitmap", "file": f"lobbysystem:font/rank/{key}.png", "ascent": 7, "height": 8, "chars": [ch]})
    files["assets/minecraft/font/default.json"] = json.dumps({"providers": providers}, indent=2).encode()
    buf = io.BytesIO(); pack_png().save(buf, "PNG", optimize=True)
    files["pack.png"] = buf.getvalue()

    for path, data in files.items():
        out = os.path.join(PACK, path)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "wb") as f:
            f.write(data)
    os.makedirs(os.path.dirname(ZIP), exist_ok=True)
    with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(files):
            info = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, files[path])
    print("Pack erstellt:", ZIP, os.path.getsize(ZIP), "Bytes")

if __name__ == "__main__":
    main()
