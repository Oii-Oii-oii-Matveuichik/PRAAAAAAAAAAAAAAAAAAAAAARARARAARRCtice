#!/usr/bin/env python3
"""Generates coal armor textures (item icons + armor layers, normal and burning).

Coal look: dark gray base with chunky, jagged lighter/shadow patches (like a coal block),
slightly ragged region borders. Burning variant adds glowing orange cracks.
Helmet has only eye holes plus the characteristic nose guard.
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ARMOR_DIR = ROOT / "src/main/resources/assets/coal_armor/textures/models/armor"
ITEM_DIR = ROOT / "src/main/resources/assets/coal_armor/textures/item"

random.seed(20241121)

# ---------------------------------------------------------------------------
# Value noise (chunky coal patches)
# ---------------------------------------------------------------------------

def make_noise_grid(w, h, cell):
    gw = max(2, w // cell + 2)
    gh = max(2, h // cell + 2)
    return [[random.random() for _ in range(gw)] for _ in range(gh)]


def sample_noise(grid, x, y, cell):
    gx = x / cell
    gy = y / cell
    x0 = int(math.floor(gx))
    y0 = int(math.floor(gy))
    fx = gx - x0
    fy = gy - y0
    # smoothstep interpolation
    sx = fx * fx * (3 - 2 * fx)
    sy = fy * fy * (3 - 2 * fy)
    top = grid[y0][x0] * (1 - sx) + grid[y0][x0 + 1] * sx
    bot = grid[y0 + 1][x0] * (1 - sx) + grid[y0 + 1][x0 + 1] * sx
    return top * (1 - sy) + bot * sy


def coal_color(n, grain):
    """Map noise value to a coal RGB color (dark base, chunky light/shadow patches)."""
    # base dark coal
    base = 30
    # chunky patches raise brightness
    v = base + n * 55
    # deep shadows in low areas
    if n < 0.25:
        v = base - (0.25 - n) * 40
    v += grain * 14 - 7
    v = max(8, min(110, v))
    # slight warm/gray variation
    return (int(v), int(v), int(v + 2))


def generate_coal(w, h, cell=4, seed=None):
    """Returns a list of rows of (r,g,b) coal colors."""
    if seed is not None:
        random.seed(seed)
    grid = make_noise_grid(w, h, cell)
    grid2 = make_noise_grid(w, h, 2)
    rows = []
    for y in range(h):
        row = []
        for x in range(w):
            n = sample_noise(grid, x, y, cell) * 0.75 + sample_noise(grid2, x, y, 2) * 0.25
            grain = random.random()
            row.append(coal_color(n, grain))
        rows.append(row)
    return rows


def add_glow_cracks(img, count=14):
    """Draws glowing orange cracks (random walks) onto an RGBA image in place."""
    px = img.load()
    w, h = img.size
    for _ in range(count):
        x = random.randint(2, w - 3)
        y = random.randint(2, h - 3)
        steps = random.randint(6, 16)
        for _ in range(steps):
            # bright core + softer halo
            for dx, dy, col in ((0, 0, (255, 210, 63)), (1, 0, (255, 140, 0)), (-1, 0, (255, 140, 0)),
                                (0, 1, (255, 140, 0)), (0, -1, (255, 140, 0)), (1, 1, (230, 90, 0)),
                                (-1, -1, (230, 90, 0))):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h:
                    r, g, b, a = px[nx, ny]
                    if a > 0:
                        px[nx, ny] = (col[0], col[1], col[2], 255)
            x += random.randint(-1, 1)
            y += random.randint(-1, 1)
            x = max(0, min(w - 1, x))
            y = max(0, min(h - 1, y))


def ragged_border(img, regions, rng):
    """Makes region borders slightly ragged: noisy alpha on the outermost pixel ring."""
    px = img.load()
    w, h = img.size
    for (x0, y0, x1, y1) in regions:
        for x in range(x0, x1):
            for y in (y0, y1 - 1):
                if rng.random() < 0.35:
                    r, g, b, a = px[x, y]
                    px[x, y] = (r, g, b, 0)
        for y in range(y0, y1):
            for x in (x0, x1 - 1):
                if rng.random() < 0.35:
                    r, g, b, a = px[x, y]
                    px[x, y] = (r, g, b, 0)


# Armor layer regions (64x32) from HumanoidModel texOffs:
#   head (helmet): texOffs(0,0)  -> box UV region (0,0)-(32,16)
#   body (chest):  texOffs(16,16)-> box UV region (16,16)-(40,32)
#   leg (boots):   texOffs(0,16) -> box UV region (0,16)-(16,32)
HEAD_REGION = (0, 0, 32, 16)
BODY_REGION = (16, 16, 40, 32)
LEG_REGION = (0, 16, 16, 32)

# Head box front face (south): texOffs(0,0) + (d, d) = (8, 8), size (w=8, h=8) -> (8,8)-(16,16)
FACE_X0, FACE_Y0 = 8, 8


def punch_helmet_face(img):
    """Helmet: only eye holes (transparent) + nose guard strip (lighter coal)."""
    px = img.load()
    # Nose guard: vertical strip in the center of the face (characteristic Minecraft helmet nose cross).
    for y in range(FACE_Y0, FACE_Y0 + 8):
        for x in (FACE_X0 + 3, FACE_X0 + 4):
            r, g, b, a = px[x, y]
            px[x, y] = (min(255, r + 45), min(255, g + 45), min(255, b + 45), 255)
    # Eye holes: two transparent slits flanking the nose guard.
    for y in (FACE_Y0 + 3, FACE_Y0 + 4):
        for x in (FACE_X0 + 1, FACE_X0 + 2, FACE_X0 + 5, FACE_X0 + 6):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 0)


def make_layer(burning, seed):
    w, h = 64, 32
    rows = generate_coal(w, h, cell=4, seed=seed)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            r, g, b = rows[y][x]
            px[x, y] = (r, g, b, 255)
    # Keep only the armor regions opaque (rest transparent), with ragged borders.
    mask = Image.new("L", (w, h), 0)
    mpx = mask.load()
    for (x0, y0, x1, y1) in (HEAD_REGION, BODY_REGION, LEG_REGION):
        for y in range(y0, y1):
            for x in range(x0, x1):
                mpx[x, y] = 255
    img.putalpha(mask)
    rng = random.Random(seed + 7)
    ragged_border(img, (HEAD_REGION, BODY_REGION, LEG_REGION), rng)
    punch_helmet_face(img)
    if burning:
        add_glow_cracks(img, count=16)
    return img


# ---------------------------------------------------------------------------
# Item icons (16x16)
# ---------------------------------------------------------------------------

# X = coal, . = transparent, E = eye hole (transparent), N = nose guard (lighter)
ICONS = {
    "coal_helmet": [
        "................",
        ".....XXXXXX.....",
        "...XXXXXXXXXX...",
        "..XXXXXXXXXXXX..",
        "..XXEEXXXEEXX...",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "coal_chestplate": [
        "................",
        "..XX........XX..",
        ".XXXX......XXXX.",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXXXXXXXX.",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "...XXXXXXXXXX...",
        "...XXXXXXXXXX...",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    "coal_leggings": [
        "................",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "..XXX..XX..XXX..",
        "................",
        "................",
    ],
    "coal_boots": [
        "................",
        "................",
        "................",
        "..XXX....XXX....",
        "..XXX....XXX....",
        "..XXX....XXX....",
        "..XXX....XXX....",
        "..XXX....XXX....",
        "..XXX....XXX....",
        "..XXXXX..XXXXX..",
        "..XXXXXX.XXXXXX.",
        "..XXXXXXX.XXXXX.",
        "..XXXXXXXX.XXXXX",
        "..XXXXXXXX.XXXXX",
        "................",
        "................",
    ],
}


def make_icon(name, pattern, seed):
    rows = generate_coal(16, 16, cell=3, seed=seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, line in enumerate(pattern):
        for x, ch in enumerate(line):
            if ch == "X":
                r, g, b = rows[y][x]
                px[x, y] = (r, g, b, 255)
            elif ch == "N":
                r, g, b = rows[y][x]
                px[x, y] = (min(255, r + 45), min(255, g + 45), min(255, b + 45), 255)
    return img


def main():
    ARMOR_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)

    make_layer(burning=False, seed=101).save(ARMOR_DIR / "coal_layer_1.png")
    make_layer(burning=False, seed=202).save(ARMOR_DIR / "coal_layer_2.png")
    make_layer(burning=True, seed=303).save(ARMOR_DIR / "coal_burning_layer_1.png")
    make_layer(burning=True, seed=404).save(ARMOR_DIR / "coal_burning_layer_2.png")

    for i, (name, pattern) in enumerate(ICONS.items()):
        make_icon(name, pattern, seed=500 + i).save(ITEM_DIR / f"{name}.png")

    print("Textures written to:")
    print(" ", ARMOR_DIR)
    print(" ", ITEM_DIR)


if __name__ == "__main__":
    main()
