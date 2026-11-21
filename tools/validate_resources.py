#!/usr/bin/env python3
"""Static resource checks only; does not compile Java or simulate Minecraft."""
import json
from pathlib import Path
import struct
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
PARTS = {'helmet': (5, 77), 'chestplate': (8, 112), 'leggings': (7, 105), 'boots': (4, 91)}
PATTERNS = {
    'helmet': ['XXX', 'X X'],
    'chestplate': ['X X', 'XXX', 'XXX'],
    'leggings': ['XXX', 'X X', 'X X'],
    'boots': ['X X', 'X X'],
}


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def png_size(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    assert data[12:16] == b'IHDR', path
    return struct.unpack('>II', data[16:24])


class ResourceTests(unittest.TestCase):
    def test_all_json_parse(self):
        for path in RES.rglob('*.json'):
            with self.subTest(path=path.relative_to(RES)):
                self.assertIsInstance(load(path), dict)

    def test_recipes(self):
        for part, (coal, _) in PARTS.items():
            with self.subTest(part=part):
                recipe = load(RES / f'data/coal_armor/recipe/coal_{part}.json')
                self.assertEqual(recipe['type'], 'minecraft:crafting_shaped')
                self.assertEqual(recipe['pattern'], PATTERNS[part])
                self.assertEqual(sum(row.count('X') for row in recipe['pattern']), coal)
                self.assertEqual(recipe['key']['X'], {'item': 'minecraft:coal'})
                self.assertEqual(recipe['result'], {'id': f'coal_armor:coal_{part}', 'count': 1})

    def test_item_models_and_textures(self):
        for part in PARTS:
            with self.subTest(part=part):
                model = load(RES / f'assets/coal_armor/models/item/coal_{part}.json')
                self.assertEqual(model['parent'], 'minecraft:item/generated')
                ns, texture = model['textures']['layer0'].split(':')
                self.assertEqual(png_size(RES / f'assets/{ns}/textures/{texture}.png'), (16, 16))

    def test_armor_layers(self):
        armor_dir = RES / 'assets/coal_armor/textures/models/armor'
        for layer in [1, 2]:
            self.assertEqual(png_size(armor_dir / f'coal_layer_{layer}.png'), (64, 32))
            frames = sorted((armor_dir / 'coal_burning').glob(f'layer_{layer}_*.png'))
            self.assertEqual(len(frames), 48)
            for index, path in enumerate(frames):
                self.assertEqual(path.name, f'layer_{layer}_{index:02d}.png')
                self.assertEqual(png_size(path), (64, 32))

    def test_translations(self):
        translations = [load(RES / f'assets/coal_armor/lang/{locale}.json') for locale in ['en_us', 'ru_ru']]
        keys = {f'item.coal_armor.coal_{part}' for part in PARTS}
        for mapping in translations:
            self.assertEqual(set(mapping), keys)
            self.assertTrue(all(isinstance(v, str) and v.strip() for v in mapping.values()))

    def test_enchantment_tags(self):
        for tag, part in [('head_armor', 'helmet'), ('chest_armor', 'chestplate'), ('leg_armor', 'leggings'), ('foot_armor', 'boots')]:
            data = load(RES / f'data/minecraft/tags/item/{tag}.json')
            self.assertFalse(data.get('replace', False))
            self.assertEqual(data['values'], [f'coal_armor:coal_{part}'])

    def test_mixin_configuration(self):
        config = load(RES / 'coal_armor.mixins.json')
        self.assertTrue(config['required'])
        self.assertEqual(config['compatibilityLevel'], 'JAVA_21')
        for name in config['mixins']:
            source = ROOT / 'src/main/java' / Path(config['package'].replace('.', '/')) / f'{name}.java'
            self.assertTrue(source.is_file(), source)
        self.assertIn('config="coal_armor.mixins.json"', (RES / 'META-INF/neoforge.mods.toml').read_text())

    def test_wrapper(self):
        wrapper = ROOT / 'gradle/wrapper/gradle-wrapper.jar'
        with zipfile.ZipFile(wrapper) as archive:
            self.assertIsNone(archive.testzip())
            self.assertIn('org/gradle/wrapper/GradleWrapperMain.class', archive.namelist())
        props = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertIn('gradle-8.14.2-bin.zip', props)
        self.assertTrue((ROOT / 'gradlew').stat().st_mode & 0o111)
        self.assertTrue((ROOT / 'gradlew.bat').is_file())


if __name__ == '__main__':
    unittest.main(verbosity=2)
