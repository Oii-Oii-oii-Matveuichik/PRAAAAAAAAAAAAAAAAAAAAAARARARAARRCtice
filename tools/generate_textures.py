#!/usr/bin/env python3
"""Fractured coal pixel art and a seamless graphite/ember animation.

Original deterministic cluster art, at vanilla 64x32 / 16x16 resolution. No Mojang
images bundled or required. Each face has a distinct coal field, not a repeated tile.
Follows HumanoidModel UVs. No interpolated noise, translucent edges, or blurred glow.
"""
from functools import lru_cache
import math
import random
from pathlib import Path
from PIL import Image

FRAME_COUNT = 48
FRAME_TICKS = 2

ROOT = Path(__file__).resolve().parent.parent
ARMOR_DIR = ROOT / 'src/main/resources/assets/coal_armor/textures/models/armor'
ITEM_DIR = ROOT / 'src/main/resources/assets/coal_armor/textures/item'

# Each UV face has its own deterministic, angular fracture field. No repeated tile.
# Compact pixel clusters and quantised plane lighting, not smooth noise / photo detail.
@lru_cache(maxsize=None)
def fracture_field(w, h, seed):
    rng = random.Random(seed * 7919 + w * 113 + h * 37)
    sites = []
    for y in range(-2, h + 3, 3):
        for x in range(-2, w + 3, 3):
            sites.append((x + rng.uniform(-1.2, 1.2), y + rng.uniform(-1.0, 1.0),
                          rng.choice([20, 24, 28, 32, 36, 40]),
                          rng.choice([-1, 1]), rng.uniform(0.8, 1.3)))
    cells = []
    for y in range(h):
        row = []
        for x in range(w):
            distances = [(abs(x-sx) * weight + abs(y-sy) * 0.85, i)
                         for i, (sx, sy, _, _, weight) in enumerate(sites)]
            distances.sort()
            row.append((distances[0][1], distances[1][0] - distances[0][0]))
        cells.append(row)
    pixels = []
    for y in range(h):
        row = []
        for x in range(w):
            idx, gap = cells[y][x]
            sx, sy, value, direction, _ = sites[idx]
            # Flat graphite facets broken by short, directional cleavage edges.
            plane = (x - sx) * direction - (y - sy) * 0.6
            value += 7 if plane < -0.6 else (-4 if plane > 0.9 else 0)
            if gap < 0.32:
                value -= 9
            elif y > 0 and cells[y-1][x][0] != idx and (x + idx) % 3 != 0:
                value += 15
            if x > 0 and cells[y][x-1][0] != idx and (y + idx) % 4 == 0:
                value += 9
            row.append((value, value + 2, value + 3))
        pixels.append(row)
    return pixels


def coal(x, y, shade=0):
    c = fracture_field(32, 32, 17)[y % 32][x % 32]
    return tuple(max(8, min(96, v + shade)) for v in c) + (255,)


def face(img, x, y, w, h, shade=0, cut=None, salt=0):
    """Separate fractured coal plates, softly bevelled only at exposed corners."""
    field = fracture_field(w, h, salt * 71 + x * 13 + y * 101)
    for v in range(h):
        for u in range(w):
            if cut and cut(u, v):
                continue
            light = shade
            if v == 0:
                light += 10 if u % 3 != 1 else 3
            if u == 0:
                light += 5
            if u == w - 1:
                light -= 4
            if v == h - 1:
                light -= 6
            color = tuple(max(8, min(96, c + light)) for c in field[v][u])
            img.putpixel((x + u, y + v), color + (255,))


def helmet(img):
    face(img, 8, 0, 8, 8, 13, salt=2)  # crown
    face(img, 16, 0, 8, 8, -5, salt=6)  # underside
    for x, shade in [(0, -2), (8, 4), (16, -5), (24, 0)]:
        face(img, x, 8, 8, 8, shade, salt=x // 2)
    # Full faceplate: ONLY eye slits are open. The two-pixel nasal bridge is intact.
    for x in [9, 10, 13, 14]:
        img.putpixel((x, 11), (0, 0, 0, 0))
    for y in range(9, 14):
        for x in [11, 12]:
            img.putpixel((x, y), coal(x, y, 18 if x == 11 else 8))
    # Brow crossbar, not a broad shiny stripe down the whole face.
    for x in range(9, 15):
        img.putpixel((x, 10), coal(x, 2, 13))
    # Two tiny chips only at the free lower edge; never at UV seams.
    for x in [1, 22]:
        img.putpixel((x, 15), (0, 0, 0, 0))


def chest(img):
    face(img, 20, 16, 8, 4, 9, salt=3)
    face(img, 28, 16, 8, 4, -6, salt=5)
    for x, w, shade in [(16, 4, -3), (20, 8, 5), (28, 4, -6), (32, 8, 0)]:
        def cut(u, v, w=w):
            # Neck opening and a subtly chipped waist, not holes in the torso.
            return (w == 8 and v == 0 and u in [3, 4]) or (v == 11 and u in [0, w - 1])
        face(img, x, 20, w, 12, shade, cut, salt=x)
    # Shoulder caps and all four sleeve faces (both arms share these UVs).
    face(img, 44, 16, 4, 4, 14, salt=4)
    # Arm bottom UV (48,16) stays transparent: the sleeve ends above the elbow.
    for x, shade in [(40, -3), (44, 5), (48, -6), (52, 0)]:
        face(img, x, 20, 4, 6, shade,
             cut=lambda u, v: v == 5 and u == 3, salt=x)


def boots(img):
    # Lower six pixels only: no opaque full-length leggings on the outer layer.
    face(img, 8, 16, 4, 4, -8, salt=3)
    for x, shade in [(0, -2), (4, 6), (8, -6), (12, 0)]:
        face(img, x, 26, 4, 6, shade, salt=x + 7)
        for u in range(4):
            img.putpixel((x + u, 31), (14, 15, 16, 255))


def leggings(img):
    face(img, 4, 16, 4, 4, 9, salt=2)
    for x, shade in [(0, -3), (4, 4), (8, -6), (12, 0)]:
        face(img, x, 20, 4, 12, shade, salt=x + 4)
        # A small knee plane reads as a plate rather than uniform black cloth.
        if x == 4:
            for u in [1, 2]:
                img.putpixel((x + u, 25), coal(u, 4, 14))
    # HumanoidModel's body UV is (16,16), so the waist occupies y=27..31.
    for x, w, shade in [(16, 4, -3), (20, 8, 4), (28, 4, -6), (32, 8, 0)]:
        face(img, x, 27, w, 5, shade, salt=x)


# Separate veins on EVERY exposed face, not a front-only texture overlay.
# Coordinates are local to each UV face. Keep the paths narrow and asymmetric.
# Paths share a staggered global cycle but do not all reach their peak together.
EMBER_FACES_1 = {
    'helmet_crown': ((8, 0), [[(2, 2), (2, 3), (3, 3), (3, 4), (4, 4)]]),
    'helmet_front': ((8, 8), [[(2, 5), (2, 6), (3, 6), (3, 7), (4, 7)]]),
    'helmet_left': ((0, 8), [[(2, 1), (2, 2), (3, 2), (3, 3), (4, 3), (4, 4)]]),
    'helmet_right': ((16, 8), [[(4, 2), (4, 3), (3, 3), (3, 4), (3, 5)]]),
    'helmet_back': ((24, 8), [[(2, 2), (3, 2), (3, 3), (4, 3), (4, 4), (4, 5)]]),
    'chest_front': ((20, 20), [
        [(2, 2), (2, 3), (3, 3), (3, 4), (4, 4), (4, 5)],
        [(5, 8), (5, 9), (4, 9), (4, 10)]]),
    'chest_back': ((32, 20), [
        [(2, 1), (2, 2), (3, 2), (3, 3), (3, 4), (4, 4)],
        [(5, 7), (4, 7), (4, 8), (3, 8), (3, 9), (3, 10)]]),
    'chest_left': ((16, 20), [[(1, 3), (1, 4), (2, 4), (2, 5), (2, 6), (1, 6)]]),
    'chest_right': ((28, 20), [[(2, 5), (2, 6), (1, 6), (1, 7), (1, 8)]]),
    'shoulder_top': ((44, 16), [[(1, 1), (1, 2), (2, 2)]]),
    'sleeve_front': ((44, 20), [[(1, 1), (1, 2), (2, 2), (2, 3)]]),
    'sleeve_back': ((52, 20), [[(2, 1), (2, 2), (1, 2), (1, 3)]]),
    'sleeve_left': ((40, 20), [[(1, 2), (1, 3), (2, 3), (2, 4)]]),
    'sleeve_right': ((48, 20), [[(2, 1), (1, 1), (1, 2), (1, 3)]]),
    'boot_front': ((4, 26), [[(1, 1), (1, 2), (2, 2), (2, 3)]]),
    'boot_back': ((12, 26), [[(2, 1), (2, 2), (1, 2), (1, 3)]]),
    'boot_left': ((0, 26), [[(1, 2), (2, 2), (2, 3), (2, 4)]]),
    'boot_right': ((8, 26), [[(2, 1), (1, 1), (1, 2), (1, 3)]]),
}
EMBER_FACES_2 = {
    # Keep the main leg veins above y=26, where the outer boots would hide them.
    'leg_front': ((4, 20), [[(1, 1), (1, 2), (2, 2), (2, 3), (2, 4)]]),
    'leg_back': ((12, 20), [[(2, 1), (2, 2), (1, 2), (1, 3), (1, 4)]]),
    'leg_left': ((0, 20), [[(1, 2), (2, 2), (2, 3), (2, 4)]]),
    'leg_right': ((8, 20), [[(2, 1), (1, 1), (1, 2), (1, 3), (2, 3)]]),
    'waist_front': ((20, 27), [[(2, 1), (3, 1), (3, 2), (4, 2)]]),
    'waist_back': ((32, 27), [[(5, 1), (4, 1), (4, 2), (3, 2)]]),
    'waist_left': ((16, 27), [[(1, 1), (1, 2), (2, 2)]]),
    'waist_right': ((28, 27), [[(2, 1), (2, 2), (1, 2)]]),
}


def ember_paths(faces):
    return [[(ox + x, oy + y) for x, y in path]
            for (ox, oy), paths in faces.values() for path in paths]


CRACKS_1 = ember_paths(EMBER_FACES_1)
CRACKS_2 = ember_paths(EMBER_FACES_2)


def burning(base, cracks, frame):
    """Looping graphite sheen plus travelling warm pulses along fine ember veins.

    Original coal facets remain in place. No texture scrolling, blur, or alpha fade.
    All time-dependent terms are periodic: frame 48 is exactly frame 0.
    """
    result = base.copy()
    t = (frame % FRAME_COUNT) / FRAME_COUNT
    for y in range(base.height):
        for x in range(base.width):
            r, g, b, a = base.getpixel((x, y))
            if not a:
                continue
            # A broad, subdued graphite reflection travels over existing bright facets.
            wave = (0.5 + 0.5 * math.cos(math.tau * (t - y / 40 - x / 73))) ** 7
            ridge = max(0, min(1, (r - 24) / 38))
            shine = round(22 * wave * ridge)
            result.putpixel((x, y), (r + shine, g + shine, b + shine, a))
    for path_index, path in enumerate(cracks):
        for i, (x, y) in enumerate(path):
            if not base.getpixel((x, y))[3]:
                continue
            phase = t - i / (len(path) + 3) + path_index * 0.237
            pulse = (0.5 + 0.5 * math.cos(math.tau * phase)) ** 5
            breath = 0.5 + 0.5 * math.sin(math.tau * (t + path_index * 0.19))
            r, g, b, a = base.getpixel((x, y))
            # Dark red -> copper -> small golden hot point, never a solid orange patch.
            heat = 0.08 + 0.12 * breath + 0.80 * pulse
            target = (int(68 + 165 * heat), int(28 + 123 * heat), int(21 + 51 * heat))
            mix = 0.4 + 0.6 * pulse
            color = tuple(round(v * (1-mix) + c * mix) for v, c in zip((r, g, b), target))
            result.putpixel((x, y), color + (a,))
    return result


# Original 16px silhouettes, vanilla-like scale and lighting from upper left.
# . transparent, 0 outline, 1 shadow, 2 dark coal, 3 coal, 4 bevel, 5 highlight.
ICONS = {
    'helmet': [
        '................', '................', '................', '.....000000.....',
        '....04544440....', '...0443333240...', '...0433333220...', '...0434444220...',
        '...0420030020...', '...0422232220...', '...0422232220...', '....02222220....',
        '.....000000.....', '................', '................', '................',
    ],
    'chestplate': [
        '................', '................', '.00000....00000.', '.04540....04440.',
        '.043340..043320.', '.04333400433220.', '.02333343333220.', '.00233333332200.',
        '...0433333220...', '...0433333220...', '...0333333220...', '...0333332220...',
        '...0232222220...', '....02222220....', '.....000000.....', '................',
    ],
    'leggings': [
        '................', '................', '....00000000....', '...0454444320...',
        '...0433333220...', '...0432223220...', '...0432002320...', '...0430..0320...',
        '...0430..0320...', '...0430..0320...', '...0320..0220...', '...0320..0220...',
        '...0220..0220...', '...0000..0000...', '................', '................',
    ],
    'boots': [
        '................', '................', '................', '....000..000....',
        '...0450..0440...', '...0430..0430...', '...0430..0330...', '...0330..0330...',
        '...0320..0330...', '..04320..02320..', '.043220..022320.', '.032210..012220.',
        '.00000....00000.', '................', '................', '................',
    ],
}
ICON_PALETTE = [(9, 10, 11, 255), (19, 20, 21, 255), (30, 31, 32, 255),
                (43, 44, 45, 255), (67, 68, 69, 255), (91, 92, 93, 255)]


def make_icon(part):
    img = Image.new('RGBA', (16, 16))
    for y, row in enumerate(ICONS[part]):
        assert len(row) == 16, (part, y)
        for x, value in enumerate(row):
            if value != '.':
                color = ICON_PALETTE[int(value)]
                # Sparse connected coal marks within the midtones; preserve the bevels.
                if value == '3' and fracture_field(16, 16, len(part) * 101)[y][x][0] < 27:
                    color = ICON_PALETTE[2]
                img.putpixel((x, y), color)
    return img


def main():
    ARMOR_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    outer = Image.new('RGBA', (64, 32))
    helmet(outer)
    chest(outer)
    boots(outer)
    inner = Image.new('RGBA', (64, 32))
    leggings(inner)
    for i, base, cracks in [(1, outer, CRACKS_1), (2, inner, CRACKS_2)]:
        base.save(ARMOR_DIR / f'coal_layer_{i}.png')
        animated = ARMOR_DIR / 'coal_burning'
        animated.mkdir(exist_ok=True)
        for frame in range(FRAME_COUNT):
            burning(base, cracks, frame).save(animated / f'layer_{i}_{frame:02d}.png')
        # Remove the obsolete static replacement: armor now selects an actual frame.
        (ARMOR_DIR / f'coal_burning_layer_{i}.png').unlink(missing_ok=True)
    for part in ICONS:
        make_icon(part).save(ITEM_DIR / f'coal_{part}.png')
    print(f'Wrote 2 base layers, {FRAME_COUNT * 2} animation frames and 4 item icons.')


if __name__ == '__main__':
    main()
