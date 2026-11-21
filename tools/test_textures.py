#!/usr/bin/env python3
"""Pixel-art regression checks. Requires Pillow, like the texture generator."""
import re
import unittest
from pathlib import Path
from PIL import Image

import generate_textures as art

DIR = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/coal_armor/textures/models/armor'


class TextureTests(unittest.TestCase):
    def test_all_animation_frames_preserve_silhouette(self):
        for layer in [1, 2]:
            base = Image.open(DIR / f'coal_layer_{layer}.png').convert('RGBA')
            unique = set()
            for frame in range(art.FRAME_COUNT):
                lit = Image.open(DIR / f'coal_burning/layer_{layer}_{frame:02d}.png').convert('RGBA')
                self.assertEqual(base.getchannel('A').tobytes(), lit.getchannel('A').tobytes())
                self.assertEqual(lit.size, base.size)
                unique.add(lit.tobytes())
                # Only the narrow vein network gets warm, not whole orange plates.
                warm = sum(1 for y in range(lit.height) for x in range(lit.width)
                           if lit.getpixel((x, y))[0] - lit.getpixel((x, y))[2] > 30)
                self.assertGreater(warm, 0)
                self.assertLess(warm, 55)
            self.assertEqual(len(unique), art.FRAME_COUNT)

    def test_embers_cover_every_exposed_side(self):
        required = {
            1: {'helmet_front', 'helmet_back', 'helmet_left', 'helmet_right',
                'chest_front', 'chest_back', 'chest_left', 'chest_right',
                'sleeve_front', 'sleeve_back', 'sleeve_left', 'sleeve_right',
                'boot_front', 'boot_back', 'boot_left', 'boot_right'},
            2: {'leg_front', 'leg_back', 'leg_left', 'leg_right',
                'waist_front', 'waist_back', 'waist_left', 'waist_right'},
        }
        for layer, faces in [(1, art.EMBER_FACES_1), (2, art.EMBER_FACES_2)]:
            self.assertLessEqual(required[layer], set(faces))
            base = Image.open(DIR / f'coal_layer_{layer}.png').convert('RGBA')
            frames = [Image.open(DIR / f'coal_burning/layer_{layer}_{i:02d}.png').convert('RGBA')
                      for i in range(art.FRAME_COUNT)]
            for name, ((ox, oy), paths) in faces.items():
                points = {(ox+x, oy+y) for path in paths for x, y in path}
                with self.subTest(layer=layer, face=name):
                    self.assertGreaterEqual(len(points), 3)
                    for point in points:
                        # No invisible veins in eye holes, bare arms or cut hems.
                        self.assertEqual(base.getpixel(point)[3], 255)
                        red = [frame.getpixel(point)[0] for frame in frames]
                        self.assertGreater(max(red) - min(red), 70)
                        self.assertTrue(any(frame.getpixel(point)[0] - frame.getpixel(point)[2] > 30
                                            for frame in frames))

    def test_animation_is_looping_and_reproducible(self):
        for layer, paths in [(1, art.CRACKS_1), (2, art.CRACKS_2)]:
            base = Image.open(DIR / f'coal_layer_{layer}.png').convert('RGBA')
            self.assertEqual(art.burning(base, paths, 0).tobytes(),
                             art.burning(base, paths, art.FRAME_COUNT).tobytes())
            changes = []
            for frame in range(art.FRAME_COUNT):
                current = art.burning(base, paths, frame)
                saved = Image.open(DIR / f'coal_burning/layer_{layer}_{frame:02d}.png').convert('RGBA')
                self.assertEqual(current.tobytes(), saved.tobytes())
                following = art.burning(base, paths, (frame + 1) % art.FRAME_COUNT)
                delta = sum(abs(a-b) for a, b in zip(current.tobytes(), following.tobytes()))
                changes.append(delta)
            # The wrap-around transition must not flash/reset at the end of the cycle.
            self.assertLess(changes[-1], sum(changes) / len(changes) * 1.6)

    def test_java_frame_contract_matches_assets(self):
        java = (Path(__file__).resolve().parents[1] / 'src/main/java/com/coalarmor/CoalArmorItem.java').read_text()
        self.assertEqual(int(re.search(r'BURNING_FRAME_COUNT = (\d+)', java)[1]), art.FRAME_COUNT)
        self.assertEqual(int(re.search(r'BURNING_FRAME_TICKS = (\d+)', java)[1]), art.FRAME_TICKS)
        self.assertIn('burningFrame(living.level().getGameTime())', java)
        self.assertIn('BURNING_FRAMES[innerModel ? 1 : 0]', java)
        self.assertEqual(len(list((DIR / 'coal_burning').glob('*.png'))), art.FRAME_COUNT * 2)
        self.assertFalse(list(DIR.glob('coal_burning_layer_*.png')))

    def test_uv_faces_have_distinct_fractures(self):
        img = Image.open(DIR / 'coal_layer_1.png').convert('RGBA')
        # Independent front and back fields, not the same diagonal tile shifted by UV.
        front = img.crop((20, 20, 28, 32)).tobytes()
        back = img.crop((32, 20, 40, 32)).tobytes()
        self.assertNotEqual(front, back)
        first = art.fracture_field(8, 12, 17)
        second = art.fracture_field(8, 12, 18)
        self.assertNotEqual(first, second)
        self.assertEqual(first, art.fracture_field(8, 12, 17))

    def test_preview_uses_same_frame_count_and_timing(self):
        path = Path(__file__).resolve().parents[1] / 'docs/coal_armor_preview.gif'
        with Image.open(path) as gif:
            self.assertEqual(gif.n_frames, art.FRAME_COUNT)
            self.assertEqual(gif.info['loop'], 0)
            total = 0
            for frame in range(gif.n_frames):
                gif.seek(frame)
                self.assertEqual(gif.info['duration'], art.FRAME_TICKS * 50)
                total += gif.info['duration']
            self.assertEqual(total, 4800)

    def test_closed_face_with_eye_slits(self):
        img = Image.open(DIR / 'coal_layer_1.png').convert('RGBA')
        holes = {(x, y) for y in range(8, 16) for x in range(8, 16)
                 if img.getpixel((x, y))[3] == 0}
        self.assertEqual(holes, {(9, 11), (10, 11), (13, 11), (14, 11)})
        for y in range(8, 16):
            self.assertEqual(img.getpixel((11, y))[3], 255)
            self.assertEqual(img.getpixel((12, y))[3], 255)

    def test_shoulders_and_sleeves_exist(self):
        img = Image.open(DIR / 'coal_layer_1.png').convert('RGBA')
        for point in [(44, 16), (47, 19), (40, 20), (44, 20), (48, 20), (52, 20)]:
            self.assertEqual(img.getpixel(point)[3], 255, point)
        # No floating cuffs at the hand or opaque lower arms.
        for y in range(26, 32):
            for x in range(40, 56):
                self.assertEqual(img.getpixel((x, y))[3], 0)
        self.assertIsNone(img.crop((48, 16, 52, 20)).getbbox())

    def test_leggings_do_not_contain_helmet_or_sleeves(self):
        img = Image.open(DIR / 'coal_layer_2.png').convert('RGBA')
        self.assertIsNone(img.crop((0, 0, 64, 16)).getbbox())
        self.assertIsNone(img.crop((40, 16, 64, 32)).getbbox())

    def test_no_semitransparent_pixels(self):
        for path in DIR.rglob('*.png'):
            img = Image.open(path).convert('RGBA')
            self.assertLessEqual(set(img.getchannel('A').tobytes()), {0, 255})


if __name__ == '__main__':
    unittest.main(verbosity=2)
