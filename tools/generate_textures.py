#!/usr/bin/env python3
"""Hand-pixelled Minecraft-style coal armor, at vanilla 64x32 / 16x16 resolution.

Original pixel art: no Mojang image files are bundled or required. Faces follow the
vanilla HumanoidModel UV layout, including the arms missing in the first revision.
No interpolated noise, translucent edges, random holes, or blurred glow.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ARMOR_DIR = ROOT / 'src/main/resources/assets/coal_armor/textures/models/armor'
ITEM_DIR = ROOT / 'src/main/resources/assets/coal_armor/textures/item'

# Small, connected diagonal coal strata; deliberately limited, neutral palette.
COAL = [
    '1111000011100000', '1100001111000001', '1000011110000111', '0001111000001110',
    '0011110000111100', '0111000011110000', '1110000111000011', '1100011110000110',
    '1000111100001100', '0001110000111000', '0011100011110000', '0110000111100001',
    '1100001110000011', '1000111000011110', '0001110000111100', '0011100011110000',
]
PALETTE = [(16, 17, 18), (28, 29, 30), (39, 40, 41), (51, 52, 53), (67, 68, 69)]


def coal(x, y, shade=0):
    index = int(COAL[y % 16][x % 16])
    # Occasional single-pixel fractures, not independent per-pixel noise.
    if (x * 3 + y * 7) % 37 == 0:
        index += 1
    c = PALETTE[min(index, 4)]
    return tuple(max(8, min(92, v + shade)) for v in c) + (255,)


def face(img, x, y, w, h, shade=0, cut=None, salt=0):
    """One UV face, with a one-pixel bevel and intentional optional hem cuts."""
    for v in range(h):
        for u in range(w):
            if cut and cut(u, v):
                continue
            light = shade
            if v == 0:
                light += 17
            if u == 0:
                light += 9
            if u == w - 1:
                light -= 5
            if v == h - 1:
                light -= 7
            img.putpixel((x + u, y + v), coal(u + salt, v + salt // 2, light))


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


# Deliberate 1px ember seams. Same silhouettes and coal pixels as the unlit armor.
CRACKS_1 = [
    [(10, 2), (10, 3), (11, 3), (11, 4), (12, 4), (12, 5)],
    [(26, 10), (27, 10), (27, 11), (28, 11), (28, 12)],
    [(22, 22), (22, 23), (23, 23), (23, 24), (24, 24), (24, 25), (24, 26)],
    [(35, 25), (36, 25), (36, 26), (37, 26), (37, 27)],
    [(45, 21), (45, 22), (46, 22), (46, 23)],
    [(5, 27), (5, 28), (6, 28), (6, 29)],
]
CRACKS_2 = [
    [(5, 22), (5, 23), (6, 23), (6, 24), (6, 25), (5, 25), (5, 26)],
    [(13, 27), (13, 28), (14, 28), (14, 29)],
    [(22, 28), (23, 28), (23, 29), (24, 29), (24, 30)],
    [(34, 29), (35, 29), (35, 30)],
]


def burning(base, cracks):
    result = base.copy()
    for path in cracks:
        for i, (x, y) in enumerate(path):
            if base.getpixel((x, y))[3]:
                result.putpixel((x, y), [(133, 45, 19, 255), (205, 76, 23, 255),
                                       (241, 133, 39, 255)][i % 3])
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
                if value == '3' and COAL[y][x] == '0':
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
        burning(base, cracks).save(ARMOR_DIR / f'coal_burning_layer_{i}.png')
    for part in ICONS:
        make_icon(part).save(ITEM_DIR / f'coal_{part}.png')
    print('Wrote 4 armor layers and 4 item icons.')


if __name__ == '__main__':
    main()
