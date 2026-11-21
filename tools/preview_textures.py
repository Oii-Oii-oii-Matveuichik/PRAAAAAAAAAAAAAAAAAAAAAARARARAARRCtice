#!/usr/bin/env python3
"""Render the actual armor UVs on simple block mannequins (Pillow only).

A documentation preview, not an in-game screenshot. Uses nearest-neighbor sampling.
Run after generate_textures.py.
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from generate_textures import FRAME_COUNT, FRAME_TICKS

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / 'src/main/resources/assets/coal_armor/textures'


def render(burning=False, back=False, frame=0, yaw_degrees=None):
    canvas = Image.new('RGBA', (340, 430), (0, 0, 0, 0))
    faces = []
    yaw = math.radians(yaw_degrees if yaw_degrees is not None else (155 if back else 25))
    pitch = math.radians(12)

    def project(p):
        x, y, z = p
        xx = x * math.cos(yaw) + z * math.sin(yaw)
        zz = z * math.cos(yaw) - x * math.sin(yaw)
        return (170 + xx * 10, 379 - y * math.cos(pitch) * 10 + zz * math.sin(pitch) * 10, zz)

    def cube(cx, bottom, cz, w, h, d, texture, uv=(0, 0), inflate=0):
        x0, x1 = cx - w / 2 - inflate, cx + w / 2 + inflate
        z0, z1 = cz - d / 2 - inflate, cz + d / 2 + inflate
        y0, y1 = bottom - inflate, bottom + h + inflate
        tx, ty = uv
        surfaces = [
            ((x0, y1, z0), (x1, y1, z0), (x0, y0, z0), (tx + d, ty + d, w, h)),
            ((x1, y1, z1), (x0, y1, z1), (x1, y0, z1), (tx + 2*d + w, ty + d, w, h)),
            ((x0, y1, z1), (x0, y1, z0), (x0, y0, z1), (tx, ty + d, d, h)),
            ((x1, y1, z0), (x1, y1, z1), (x1, y0, z0), (tx + d + w, ty + d, d, h)),
            ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), (tx + d, ty, w, d)),
            ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0), (tx + d + w, ty, w, d)),
        ]
        for p0, p1, p2, region in surfaces:
            a, b, c = project(p0), project(p1), project(p2)
            rx, ry, rw, rh = region
            tile = texture.crop((int(rx), int(ry), int(rx+rw), int(ry+rh)))
            faces.append(((a[2] + b[2] + c[2]) / 3, a, b, c, tile))

    skin = Image.new('RGBA', (64, 32), (157, 114, 85, 255))
    # Neutral mannequin face behind the eye holes.
    d = ImageDraw.Draw(skin)
    d.rectangle((8, 8, 15, 10), fill=(57, 41, 32, 255))
    d.point((10, 11), fill=(220, 212, 183, 255))
    d.point((13, 11), fill=(220, 212, 183, 255))
    clothes = Image.new('RGBA', (64, 32), (54, 60, 65, 255))
    cube(0, 24, 0, 8, 8, 8, skin)
    cube(0, 12, 0, 8, 12, 4, clothes, (16, 16))
    for x in [-2, 2]:
        cube(x, 0, 0, 4, 12, 4, clothes, (0, 16))
    for x in [-6, 6]:
        cube(x, 12, 0, 4, 12, 4, skin, (40, 16))

    armor = TEXTURES / 'models/armor'
    def layer_path(layer):
        if burning:
            return armor / f'coal_burning/layer_{layer}_{frame % FRAME_COUNT:02d}.png'
        return armor / f'coal_layer_{layer}.png'
    outer = Image.open(layer_path(1)).convert('RGBA')
    inner = Image.open(layer_path(2)).convert('RGBA')
    cube(0, 24, 0, 8, 8, 8, outer, inflate=0.55)
    cube(0, 12, 0, 8, 12, 4, outer, (16, 16), inflate=0.55)
    cube(0, 12, 0, 8, 12, 4, inner, (16, 16), inflate=0.25)
    for x in [-2, 2]:
        cube(x, 0, 0, 4, 12, 4, outer, (0, 16), inflate=0.55)
        cube(x, 0, 0, 4, 12, 4, inner, (0, 16), inflate=0.25)
    for x in [-6, 6]:
        cube(x, 12, 0, 4, 12, 4, outer, (40, 16), inflate=0.55)

    for _, a, b, c, tile in sorted(faces, key=lambda f: f[0], reverse=True):
        ax, ay = b[0] - a[0], b[1] - a[1]
        bx, by = c[0] - a[0], c[1] - a[1]
        det = ax * by - ay * bx
        if det < 0.01:  # Back-face culling; don't show hidden faces through eye slits.
            continue
        tw, th = tile.size
        m0, m1 = by * tw / det, -bx * tw / det
        m3, m4 = -ay * th / det, ax * th / det
        coeff = (m0, m1, -m0*a[0] - m1*a[1], m3, m4, -m3*a[0] - m4*a[1])
        warped = tile.transform(canvas.size, Image.Transform.AFFINE, coeff, Image.Resampling.NEAREST)
        canvas.alpha_composite(warped)
    return canvas


def main():
    out = Image.new('RGB', (1120, 650), '#20242a')
    draw = ImageDraw.Draw(out)
    try:
        title = ImageFont.truetype('DejaVuSans.ttf', 27)
        text = ImageFont.truetype('DejaVuSans.ttf', 15)
    except OSError:
        title = text = ImageFont.load_default()
    draw.text((36, 22), 'COAL ARMOR / FRACTURED GRAPHITE', font=title, fill='#e6e4df')
    views = [(25, 'BURNING / FRONT'), (155, 'BURNING / BACK'), (-65, 'BURNING / SIDE')]
    for i, (angle, label) in enumerate(views):
        x = 35 + i * 355
        draw.rounded_rectangle((x, 78, x+340, 514), radius=10, fill='#2b3037')
        image = render(burning=True, yaw_degrees=angle)
        out.paste(image, (x, 80), image)
        draw.text((x+15, 95), label, font=text, fill='#c7cbd0')
    for i, part in enumerate(['helmet', 'chestplate', 'leggings', 'boots']):
        x = 400 + i * 85
        icon = Image.open(TEXTURES / f'item/coal_{part}.png').resize((64, 64), Image.Resampling.NEAREST)
        out.paste(icon, (x, 538), icon)
    draw.text((48, 552), '16 x 16 ITEM ICONS', font=text, fill='#c7cbd0')
    draw.text((48, 620), 'UV preview on a block mannequin. Not an in-game screenshot. No shaders or emissive effects.', font=text, fill='#969da7')
    path = ROOT / 'docs/coal_armor_preview.png'
    path.parent.mkdir(exist_ok=True)
    out.save(path)
    print(path)

    # Same PNG frames and 2-tick timing as CoalArmorItem, not a separate mock animation.
    frames = []
    for frame in range(FRAME_COUNT):
        page = out.copy()
        for i, (angle, label) in enumerate(views):
            x = 35 + i * 355
            ImageDraw.Draw(page).rounded_rectangle((x, 78, x+340, 514), radius=10, fill='#2b3037')
            mannequin = render(burning=True, frame=frame, yaw_degrees=angle)
            page.paste(mannequin, (x, 80), mannequin)
            ImageDraw.Draw(page).text((x+15, 95), label, font=text, fill='#c7cbd0')
        frames.append(page)
    # One shared palette prevents GIF colour pumping between successive frames.
    palette_samples = Image.new('RGB', (280 * 8, 163 * 6))
    for i, page in enumerate(frames):
        palette_samples.paste(page.resize((280, 163), Image.Resampling.NEAREST),
                              ((i % 8) * 280, (i // 8) * 163))
    palette = palette_samples.quantize(colors=256, dither=Image.Dither.NONE)
    indexed = [page.quantize(palette=palette, dither=Image.Dither.NONE) for page in frames]
    gif = path.with_suffix('.gif')
    indexed[0].save(gif, save_all=True, append_images=indexed[1:],
                    duration=FRAME_TICKS * 50, loop=0, disposal=2, optimize=False)
    print(gif)


if __name__ == '__main__':
    main()
