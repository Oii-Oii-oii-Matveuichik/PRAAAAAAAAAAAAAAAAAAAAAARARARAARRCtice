#!/usr/bin/env python3
"""Pixel-art regression checks. Requires Pillow, like the texture generator."""
import unittest
from pathlib import Path
from PIL import Image

DIR = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/coal_armor/textures/models/armor'


class TextureTests(unittest.TestCase):
    def test_burning_preserves_silhouette_and_coal(self):
        for layer in [1, 2]:
            base = Image.open(DIR / f'coal_layer_{layer}.png').convert('RGBA')
            lit = Image.open(DIR / f'coal_burning_layer_{layer}.png').convert('RGBA')
            self.assertEqual(base.getchannel('A').tobytes(), lit.getchannel('A').tobytes())
            changes = sum(base.getpixel((x, y)) != lit.getpixel((x, y))
                          for y in range(base.height) for x in range(base.width))
            self.assertGreater(changes, 5)
            self.assertLess(changes, 70)  # Sparse ember seams, not an orange repaint.

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
        for path in DIR.glob('*.png'):
            img = Image.open(path).convert('RGBA')
            self.assertLessEqual(set(img.getchannel('A').tobytes()), {0, 255})


if __name__ == '__main__':
    unittest.main(verbosity=2)
