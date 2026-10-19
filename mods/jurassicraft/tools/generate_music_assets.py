#!/usr/bin/env python3
"""Copy the original record art and audio; emit separate sound and language fragments."""
import json
import shutil
from pathlib import Path

root = Path(__file__).resolve().parents[1]
legacy = root / 'upstream/src/main/resources/assets/jurassicraft'
assets = root / 'src/main/resources/assets/jurassicraft'
tracks = {
    'jurassicraft_theme': 'PigeonAce - JurassiCraft Theme',
    'troodons_and_raptors': 'PigeonAce - Troodons And Raptors',
    'dont_move_a_muscle': "PigeonAce - Don't Move A Muscle",
}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)

sounds = {}
lang = {}
for track, description in tracks.items():
    copy(legacy / f'sounds/music/{track}.ogg', assets / f'sounds/music/{track}.ogg')
    copy(legacy / f'textures/items/{track}.png', assets / f'textures/item/{track}.png')
    write(assets / f'models/item/disc_{track}.json', {'parent':'minecraft:item/generated', 'textures':{'layer0':f'jurassicraft:item/{track}'}})
    sounds[track] = {'sounds':[{'name':f'jurassicraft:music/{track}', 'stream':True}]}
    lang[f'item.jurassicraft.disc_{track}'] = 'JurassiCraft Record'
    lang[f'item.jurassicraft.disc_{track}.desc'] = description
write(root / 'tools/sounds/music.json', sounds)
write(root / 'tools/lang/music.json', lang)
