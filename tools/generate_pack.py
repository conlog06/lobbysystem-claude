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


# ---------------------------------------------------------------- Item-Icons (item_model, ab 1.21.4)
# Werden über die Komponente "item_model" = lobbysystem:<name> genutzt – nur bei geladenem Pack.

def outline(im, color=(25, 25, 30, 255)):
    """Dunkler 1-px-Rand um alle sichtbaren Pixel (Minecraft-Look)."""
    src = im.copy(); out = im.copy(); w, h = im.size
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] > 0:
                    out.putpixel((x, y), color)
                    break
    return out

def rocket():
    im = img(); d = ImageDraw.Draw(im)
    body = [(4, 11), (11, 4), (12, 3), (13, 3), (13, 4), (12, 5), (5, 12)]
    for i in range(7):  # diagonaler Körper
        d.line((3 + i, 10 + 0, 10 + i - 4, 3 + 0), fill=None)
    d.polygon([(4, 10), (10, 4), (12, 6), (6, 12)], fill=(200, 205, 215, 255))
    d.polygon([(5, 10), (10, 5), (11, 6), (6, 11)], fill=(235, 238, 245, 255))
    d.polygon([(10, 4), (13, 2), (12, 6)], fill=(220, 40, 40, 255))           # Spitze
    d.polygon([(4, 10), (2, 9), (4, 8)], fill=(200, 40, 40, 255))             # Flossen
    d.polygon([(6, 12), (7, 14), (8, 12)], fill=(200, 40, 40, 255))
    px(d, [(8, 7), (9, 7), (8, 8)], (70, 160, 230, 255))                       # Fenster
    d.polygon([(4, 12), (1, 15), (3, 12)], fill=(255, 150, 30, 255))          # Flamme
    px(d, [(3, 13), (2, 14)], (255, 230, 80, 255))
    return outline(im)

def magnifier():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((2, 2, 10, 10), fill=(110, 80, 50, 255))
    d.ellipse((3, 3, 9, 9), fill=(150, 210, 240, 255))
    px(d, [(4, 5), (5, 4), (4, 4)], (240, 250, 255, 255))
    d.line((9, 9, 13, 13), fill=(90, 60, 35, 255), width=2)
    px(d, [(14, 14)], (90, 60, 35, 255))
    # Augen im Glas (Verstecken)
    px(d, [(5, 6), (7, 6)], (30, 30, 40, 255))
    return outline(im)

def zombie():
    im = img(); d = ImageDraw.Draw(im)
    d.rectangle((2, 2, 13, 13), fill=(70, 140, 60, 255))
    d.rectangle((2, 2, 13, 4), fill=(50, 105, 45, 255))                       # Haare/Schatten
    d.rectangle((4, 6, 6, 8), fill=(20, 20, 20, 255))
    d.rectangle((9, 6, 11, 8), fill=(20, 20, 20, 255))
    px(d, [(5, 7), (10, 7)], (200, 40, 40, 255))                               # rote Pupillen
    d.rectangle((5, 10, 10, 11), fill=(40, 80, 35, 255))
    px(d, [(6, 11), (8, 11)], (230, 230, 210, 255))                            # Zähne
    px(d, [(3, 12), (12, 9), (11, 12)], (95, 165, 80, 255))
    return outline(im)

def globe():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=(40, 110, 210, 255))
    d.ellipse((2, 2, 13, 13), fill=(60, 140, 235, 255))
    d.polygon([(4, 4), (8, 3), (9, 6), (6, 8), (4, 7)], fill=(70, 170, 70, 255))
    d.polygon([(9, 9), (12, 8), (12, 11), (10, 12)], fill=(70, 170, 70, 255))
    px(d, [(3, 10), (4, 11)], (70, 170, 70, 255))
    px(d, [(5, 5), (6, 4)], (120, 210, 110, 255))
    # kleiner Spross oben (Evolution)
    px(d, [(8, 0), (8, 1)], (60, 150, 50, 255)); px(d, [(7, 0), (9, 1)], (110, 200, 90, 255))
    return outline(im)

def house():
    im = img(); d = ImageDraw.Draw(im)
    d.polygon([(1, 8), (8, 1), (15, 8)], fill=(190, 50, 50, 255))
    d.polygon([(3, 8), (8, 3), (13, 8)], fill=(225, 75, 65, 255))
    d.rectangle((3, 8, 12, 14), fill=(210, 180, 130, 255))
    d.rectangle((7, 10, 9, 14), fill=(120, 80, 45, 255))
    d.rectangle((4, 9, 5, 10), fill=(150, 210, 240, 255))
    d.rectangle((10, 9, 11, 10), fill=(150, 210, 240, 255))
    return outline(im)

def compass():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=(190, 140, 40, 255))
    d.ellipse((2, 2, 13, 13), fill=(240, 235, 220, 255))
    d.polygon([(7, 7), (8, 7), (11, 3)], fill=(220, 40, 40, 255))
    d.polygon([(7, 8), (8, 8), (4, 12)], fill=(70, 70, 80, 255))
    px(d, [(7, 7), (8, 8), (7, 8), (8, 7)], (40, 40, 40, 255))
    px(d, [(7, 2), (2, 7), (13, 7), (7, 13)], (120, 90, 30, 255))
    return outline(im)

def eye():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 4, 14, 11), fill=(240, 240, 245, 255))
    d.ellipse((5, 4, 10, 11), fill=(60, 160, 220, 255))
    d.ellipse((6, 6, 9, 9), fill=(20, 20, 30, 255))
    px(d, [(6, 6)], (255, 255, 255, 255))
    return outline(im)

def wand():
    im = img(); d = ImageDraw.Draw(im)
    d.line((2, 13, 10, 5), fill=(60, 40, 80, 255), width=2)
    px(d, [(3, 13), (2, 12)], (230, 230, 240, 255))
    for (x, y) in [(11, 2), (11, 6), (9, 4), (13, 4)]:
        d.line((x, y, 11, 4), fill=(255, 220, 60, 255))
    px(d, [(11, 4)], (255, 255, 255, 255))
    px(d, [(5, 3), (14, 9), (2, 6)], (255, 150, 230, 255))
    return outline(im)

def gear():
    im = img(); d = ImageDraw.Draw(im)
    for (x, y) in [(7, 1), (7, 12), (1, 7), (12, 7), (3, 3), (11, 3), (3, 11), (11, 11)]:
        d.rectangle((x, y, x + 2, y + 2) if x in (7,) or y in (7,) else (x, y, x + 1, y + 1), fill=(150, 155, 165, 255))
    d.ellipse((3, 3, 12, 12), fill=(150, 155, 165, 255))
    d.ellipse((4, 4, 11, 11), fill=(185, 190, 200, 255))
    d.ellipse((6, 6, 9, 9), fill=(0, 0, 0, 0))
    return outline(im)

def gift():
    im = img(); d = ImageDraw.Draw(im)
    d.rectangle((2, 6, 13, 14), fill=(200, 50, 70, 255))
    d.rectangle((1, 4, 14, 7), fill=(225, 70, 90, 255))
    d.rectangle((7, 4, 8, 14), fill=(255, 210, 60, 255))
    d.rectangle((1, 5, 14, 5), fill=(255, 210, 60, 255))
    d.polygon([(7, 4), (4, 1), (3, 3)], fill=(255, 210, 60, 255))
    d.polygon([(8, 4), (11, 1), (12, 3)], fill=(255, 210, 60, 255))
    return outline(im)

def portal():
    im = img(); d = ImageDraw.Draw(im)
    d.rectangle((2, 1, 13, 14), fill=(30, 20, 45, 255))
    d.rectangle((4, 3, 11, 12), fill=(140, 60, 220, 255))
    d.rectangle((5, 4, 10, 11), fill=(180, 100, 250, 255))
    px(d, [(6, 5), (8, 7), (7, 9), (9, 5), (6, 10)], (230, 190, 255, 255))
    return outline(im)

def arrow(right=True):
    im = img(); d = ImageDraw.Draw(im)
    pts = [(3, 6), (8, 6), (8, 2), (14, 8), (8, 14), (8, 10), (3, 10)]
    if not right:
        pts = [(15 - x, y) for x, y in pts]
    d.polygon(pts, fill=(80, 200, 90, 255))
    return outline(im)

def cross():
    im = img(); d = ImageDraw.Draw(im)
    d.line((3, 3, 12, 12), fill=(220, 50, 50, 255), width=3)
    d.line((12, 3, 3, 12), fill=(220, 50, 50, 255), width=3)
    return outline(im)

def scaled(fn):
    return outline(fn())

# ---- MissileWars-Menüs
def check_badge(color=(70, 200, 80, 255), dark=(35, 130, 45, 255), cross_=False):
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((1, 1, 14, 14), fill=dark)
    d.ellipse((2, 2, 13, 13), fill=color)
    if cross_:
        d.line((5, 5, 10, 10), fill=(255, 255, 255, 255), width=2)
        d.line((10, 5, 5, 10), fill=(255, 255, 255, 255), width=2)
    else:
        d.line((4, 8, 7, 11), fill=(255, 255, 255, 255), width=2)
        d.line((7, 11, 12, 4), fill=(255, 255, 255, 255), width=2)
    return outline(im)

def door():
    im = img(); d = ImageDraw.Draw(im)
    d.rectangle((3, 1, 12, 14), fill=(120, 70, 35, 255))
    d.rectangle((4, 2, 11, 14), fill=(160, 100, 50, 255))
    d.rectangle((5, 3, 10, 7), fill=(130, 80, 40, 255))
    d.rectangle((5, 9, 10, 13), fill=(130, 80, 40, 255))
    px(d, [(10, 8)], (255, 220, 80, 255))
    return outline(im)

def bag():
    im = img(); d = ImageDraw.Draw(im)
    d.ellipse((2, 5, 13, 15), fill=(70, 170, 80, 255))
    d.rectangle((5, 3, 10, 6), fill=(70, 170, 80, 255))
    d.line((5, 6, 10, 6), fill=(255, 210, 60, 255))
    d.polygon([(7, 8), (9, 10), (7, 13), (5, 10)], fill=(120, 255, 170, 255))   # Smaragd
    px(d, [(7, 9)], (220, 255, 230, 255))
    return outline(im)

def play():
    im = img(); d = ImageDraw.Draw(im)
    d.rounded_rectangle((1, 1, 14, 14), radius=3, fill=(40, 150, 70, 255))
    d.rounded_rectangle((2, 2, 13, 13), radius=2, fill=(80, 210, 110, 255))
    d.polygon([(6, 4), (11, 8), (6, 12)], fill=(255, 255, 255, 255))
    return outline(im)

def mapicon():
    im = img(); d = ImageDraw.Draw(im)
    d.polygon([(1, 3), (5, 1), (10, 3), (14, 1), (14, 13), (10, 15), (5, 13), (1, 15)], fill=(225, 210, 160, 255))
    d.line((5, 1, 5, 13), fill=(180, 160, 110, 255)); d.line((10, 3, 10, 15), fill=(180, 160, 110, 255))
    d.ellipse((6, 5, 9, 8), fill=(220, 50, 50, 255))
    d.line((7, 8, 7, 11), fill=(220, 50, 50, 255))
    return outline(im)

def sword():
    im = img(); d = ImageDraw.Draw(im)
    d.line((4, 11, 12, 3), fill=(210, 215, 225, 255), width=2)
    d.line((12, 3, 13, 2), fill=(240, 240, 250, 255))
    d.line((3, 9, 6, 12), fill=(140, 100, 40, 255), width=2)
    d.line((2, 13, 4, 11), fill=(90, 60, 30, 255), width=2)
    return outline(im)

def chart():
    im = img(); d = ImageDraw.Draw(im)
    d.rectangle((1, 1, 14, 14), fill=(240, 240, 245, 255))
    d.rectangle((3, 9, 5, 12), fill=(70, 160, 230, 255))
    d.rectangle((7, 6, 9, 12), fill=(80, 200, 100, 255))
    d.rectangle((11, 3, 13, 12), fill=(240, 170, 40, 255))
    d.line((2, 13, 13, 13), fill=(90, 90, 100, 255))
    return outline(im)

def trophy():
    im = img(); d = ImageDraw.Draw(im)
    d.rectangle((4, 2, 11, 7), fill=(255, 200, 40, 255))
    d.ellipse((4, 4, 11, 10), fill=(255, 200, 40, 255))
    d.arc((1, 2, 6, 7), 90, 270, fill=(220, 160, 20, 255))
    d.arc((9, 2, 14, 7), 270, 90, fill=(220, 160, 20, 255))
    d.rectangle((7, 10, 8, 12), fill=(220, 160, 20, 255))
    d.rectangle((4, 12, 11, 14), fill=(140, 90, 40, 255))
    px(d, [(6, 4), (6, 5)], (255, 245, 180, 255))
    return outline(im)

def starmenu():
    im = img(); d = ImageDraw.Draw(im)
    pts = []
    import math
    for i in range(10):
        r = 7 if i % 2 == 0 else 3
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((7.5 + r * math.cos(a), 8 + r * math.sin(a)))
    d.polygon(pts, fill=(150, 210, 255, 255))
    d.polygon([(7.5, 4), (9, 8), (7.5, 11), (6, 8)], fill=(230, 245, 255, 255))
    return outline(im)

ITEMS = [
    # Spielmodi
    ("mode_missilewars", rocket()), ("mode_hideandseek", magnifier()), ("mode_zombies", zombie()),
    ("mode_worldevolution", globe()), ("mode_lobby", house()),
    # Lobby-Menüs & Hotbar
    ("navigator", compass()), ("visibility", eye()), ("cosmetics", wand()), ("settings", gear()),
    ("daily", gift()), ("lottery", outline(ticket())), ("switcher", portal()), ("coins", outline(coin())),
    ("friends", outline(friends())), ("party", outline(party())), ("clan", outline(shield())),
    ("back", arrow(False)), ("next", arrow(True)), ("close", cross()),
    # MissileWars
    ("mw_ready", check_badge()), ("mw_notready", check_badge((150, 150, 160, 255), (90, 90, 100, 255), True)),
    ("mw_menu", starmenu()), ("mw_leave", door()), ("mw_shop", bag()), ("mw_start", play()), ("mw_vote", mapicon()),
    ("mw_kit", sword()), ("mw_stats", chart()), ("mw_achievements", trophy()),
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
    ("elite",       "", "ELITE",    (170, 60, 220)),
    ("legende",     "", "LEGENDE",  (235, 120, 20)),
]

def logo():
    """CRAFTERTEAM-Schriftzug: Verlauf Cyan → Blau, doppelte Größe eines Badges."""
    base = badge("CRAFTERTEAM", (0, 170, 230))
    w, h = base.size
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        t = x / max(1, w - 1)
        tint = (int(0 + 58 * t), int(210 - 87 * t), int(255 - 42 * t))
        for y in range(h):
            r, g, b, al = base.getpixel((x, y))
            if al == 0 or (r, g, b) == (255, 255, 255) or r < 60 and g < 60:
                im.putpixel((x, y), (r, g, b, al))
            else:
                f = (r + g + b) / (3 * 200)
                im.putpixel((x, y), (min(255, int(tint[0] * f)), min(255, int(tint[1] * f)), min(255, int(tint[2] * f)), al))
    return im

LOGOS = [
    ("crafterteam", "\ue200", None, 16, 12),
    ("ct", "\ue201", None, 8, 7),
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

def _logos():
    global LOGOS
    LOGOS = [(n, c, logo() if n == "crafterteam" else badge("CT", (0, 170, 230)), h, asc) for n, c, _, h, asc in LOGOS]

def main():
    _logos()
    files = {}
    # Kompatibel mit allen Versionen:
    #  <1.20.2 liest pack_format | 1.20.2–1.21.8 liest supported_formats | ab 1.21.9/26.x min_format/max_format
    mcmeta = {
        "pack": {
            "description": "§bCrafterTeam §7– Icons, Ränge & Menüs",
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
    # CrafterTeam-Logo (groß, für Tab-Header) & kleiner CT-Tag (Chat/Scoreboard)
    for name, ch, im, height, ascent in LOGOS:
        buf = io.BytesIO(); im.save(buf, "PNG", optimize=True)
        files[f"assets/lobbysystem/textures/font/{name}.png"] = buf.getvalue()
        providers.append({"type": "bitmap", "file": f"lobbysystem:font/{name}.png", "ascent": ascent, "height": height, "chars": [ch]})
    for key, ch, text, color in BADGES:
        buf = io.BytesIO(); badge(text, color).save(buf, "PNG", optimize=True)
        files[f"assets/lobbysystem/textures/font/rank/{key}.png"] = buf.getvalue()
        providers.append({"type": "bitmap", "file": f"lobbysystem:font/rank/{key}.png", "ascent": 7, "height": 8, "chars": [ch]})
    files["assets/minecraft/font/default.json"] = json.dumps({"providers": providers}, indent=2).encode()
    # Item-Icons: textures/item + models/item + items/ (item_model-Definition ab 1.21.4)
    for name, im in ITEMS:
        buf = io.BytesIO(); im.save(buf, "PNG", optimize=True)
        files[f"assets/lobbysystem/textures/item/{name}.png"] = buf.getvalue()
        files[f"assets/lobbysystem/models/item/{name}.json"] = json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"lobbysystem:item/{name}"}}, indent=2).encode()
        files[f"assets/lobbysystem/items/{name}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"lobbysystem:item/{name}"}}, indent=2).encode()
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
