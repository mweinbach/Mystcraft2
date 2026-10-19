#!/usr/bin/env python3
"""Check the generated abandoned pieces without loading Minecraft."""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from structure_nbt import read, plain


class ZombieVillageResources(unittest.TestCase):
    def test_all_biomes_have_zombie_occupants_and_street_connectors(self):
        for biome in ('plains', 'desert', 'savanna', 'snowy', 'taiga'):
            for name in ('geneticist_house', 'fossildigsite_1', 'fossildigsite_2', 'fossildigsite_3'):
                with self.subTest(biome=biome, name=name):
                    root = ROOT / 'src/main/resources/data/jurassicraft/structures/village' / biome
                    normal = plain(read(root / (name + '.nbt'))[1])
                    abandoned = plain(read(root / 'zombie' / (name + '.nbt'))[1])
                    self.assertEqual(normal['size'], abandoned['size'])
                    normal_entity, = normal['entities']
                    zombie_entity, = abandoned['entities']
                    normal_nbt, zombie_nbt = normal_entity['nbt'], zombie_entity['nbt']
                    profession = 'geneticist' if name == 'geneticist_house' else 'paleontologist'
                    self.assertEqual(normal_nbt['id'], 'minecraft:villager')
                    self.assertEqual(zombie_nbt['id'], 'minecraft:zombie_villager')
                    self.assertEqual(zombie_nbt['VillagerData'], {'profession': 'jurassicraft:' + profession, 'level': 1, 'type': 'minecraft:' + biome})
                    self.assertEqual(zombie_nbt['Xp'], 1)
                    self.assertEqual(zombie_nbt['PersistenceRequired'], 1)
                    self.assertEqual(zombie_entity['pos'], normal_entity['pos'])
                    connectors = [block['nbt'] for block in abandoned['blocks'] if block.get('nbt', {}).get('id') == 'minecraft:jigsaw']
                    self.assertEqual(len(connectors), 1)
                    self.assertEqual(connectors[0]['name'], 'minecraft:building_entrance')
                    self.assertEqual(connectors[0]['pool'], 'minecraft:village/' + biome + '/zombie/streets')
                    placed = [abandoned['palette'][block['state']]['Name'] for block in abandoned['blocks']]
                    self.assertNotIn('minecraft:torch', placed)
                    self.assertNotIn('minecraft:wall_torch', placed)


if __name__ == '__main__':
    unittest.main()
