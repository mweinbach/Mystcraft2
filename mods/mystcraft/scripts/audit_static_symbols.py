#!/usr/bin/env python3
"""Audit the fixed 145 classic symbols against explicit compiler/runtime source paths.
This is source coverage, not a substitute for executing the game or checking pixel/chunk parity.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = 'src/main/java/net/mystcraft/'
GROUPS = {
 'colors': ('colorcloud colorcloudnat colorfog colorfognat colorfoliage colorfoliagenat colorgrass colorgrassnat colorsky colorskynat colorskynight colorwater colorwaternat', ['minecraft/age/visual/AgeVisualCompiler.java', 'minecraft/age/visual/AgeVisualMath.java', 'minecraft/client/age/AgeVisualClient.java']),
 'celestials': ('rainbow nohorizon moondark moonnormal starsdark starsendsky starsnormal starstwinkle sundark sunnormal colorhorizon', ['minecraft/age/visual/AgeVisualCompiler.java', 'minecraft/age/visual/AgeCelestialCompiler.java', 'minecraft/client/age/AgeSkyRenderer.java']),
 'biome_controllers': ('biocongrid bioconnative bioconsingle biocontiled bioconhuge bioconlarge bioconmedium bioconsmall biocontiny', ['minecraft/age/generation/ClassicGenerationCompiler.java', 'minecraft/age/generation/ClassicBiomeSource.java', 'minecraft/age/generation/ClassicBiomeLayers.java']),
 'weather': ('weatheron weathercloudy weatherfast weathernorm weatheroff weatherrain weatherslow weathersnow weatherstorm', ['minecraft/age/visual/AgeVisualCompiler.java', 'minecraft/age/AgeLevelData.java', 'minecraft/age/AgeServerLevel.java', 'minecraft/age/AgeWeatherRules.java', 'minecraft/age/AgeWeatherCycle.java']),
 'terrain': ('terrainamplified terrainend terrainflat terrainnether terrainnormal terrainvoid', ['minecraft/age/generation/ClassicGenerationCompiler.java', 'minecraft/age/generation/ClassicChunkGenerator.java', 'minecraft/age/generation/ClassicDensity.java', 'minecraft/age/generation/ClassicNormalDensity.java']),
 'generation_features': ('caves dungeons floatislands hugetrees lakesdeep lakessurface mineshafts netherfort obelisks ravines termodspheres genspikes strongholds tendrils villages cryform skylands denseores', ['minecraft/age/generation/ClassicGenerationCompiler.java', 'minecraft/age/generation/ClassicChunkGenerator.java', 'minecraft/age/generation/ClassicPopulation.java', 'minecraft/age/generation/ClassicStructures.java']),
 'environment_effects': ('envaccel envexplosions envlightning envmeteor envscorch', ['minecraft/age/AgeSymbolCompiler.java', 'minecraft/age/AgeAllocation.java', 'minecraft/instability/InstabilityRuntime.java', 'minecraft/instability/InstabilityEffects.java', 'integration/registry/MeteorRegistry.java', 'integration/registry/LightningRegistry.java']),
 'source_dummy_costs': ('featurelargedummy featuremediumdummy featuresmalldummy', ['minecraft/age/AgeSymbolCompiler.java', 'minecraft/age/AgeAllocation.java']),
 'visual_modifiers': ('lightingbright lightingdark lightingnormal modnorth modeast modsouth modwest modclear modgradient modzero modhalf modfull moddouble modend modrising modnoon modsetting', ['minecraft/age/visual/AgeVisualCompiler.java', 'minecraft/age/visual/AgeVisualMath.java', 'minecraft/age/visual/CelestialMath.java']),
 'pvp': ('pvpoff', ['minecraft/age/AgeAllocation.java', 'minecraft/age/AgeServerLevel.java']),
 'no_sea': ('nosea', ['minecraft/age/generation/ClassicGenerationCompiler.java', 'minecraft/age/generation/ClassicChunkGenerator.java']),
 'star_fissure': ('starfissure', ['minecraft/age/AgeSymbolCompiler.java', 'minecraft/age/AgeManager.java', 'minecraft/fissure/StarFissureBlock.java']),
}
COLORS = 'maroon red olive yellow darkgreen green teal cyan navy blue purple magenta black grey silver white'.split()
MATERIALS = ['dirt_0', 'stone_0', 'stone_1', 'stone_3', 'stone_5', 'stone_2', 'stone_4', 'stone_6', 'sandstone_0', 'netherrack_0', 'end_stone_0', 'nether_brick_0']
MATERIALS += [f'log_{i}' for i in range(4)] + [f'log2_{i}' for i in range(2)]
MATERIALS += [f'{name}_0' for name in 'diamond_ore gold_ore iron_ore coal_ore redstone_ore lapis_ore emerald_ore ice packed_ice glass snow obsidian glowstone quartz_ore blockcrystal water lava'.split()]
GROUPS['color_modifiers'] = (' '.join('modcolor' + c for c in COLORS), ['minecraft/age/visual/AgeVisualCompiler.java'])
GROUPS['materials'] = (' '.join('modmat_' + m for m in MATERIALS), ['minecraft/age/generation/ClassicMaterials.java', 'minecraft/age/generation/ClassicGenerationCompiler.java', 'minecraft/age/generation/ClassicChunkGenerator.java'])


def audit():
    source_catalog = ROOT / 'src/main/resources/assets/mystcraft/classic_symbols.json'
    documentation_catalog = ROOT / 'docs/classic-symbols.json'
    assert source_catalog.read_bytes() == documentation_catalog.read_bytes(), 'Catalog copies differ'
    data = json.loads(source_catalog.read_text())
    declared = {entry['id'] for entry in data['symbols']}
    mapped = {}
    groups = []
    for name, (names, references) in GROUPS.items():
        ids = ['mystcraft:' + word for word in names.split()]
        for symbol in ids:
            assert symbol not in mapped, 'Duplicate audit classification: ' + symbol
            mapped[symbol] = name
        paths = [JAVA + path for path in references]
        for path in paths:
            assert (ROOT / path).is_file(), 'Missing runtime source: ' + path
        groups.append({'group': name, 'count': len(ids), 'symbols': ids, 'source_paths': paths})
    assert len(declared) == 145 and len(MATERIALS) == 35
    assert declared == mapped.keys(), f'Unclassified: {declared - mapped.keys()}; unknown: {mapped.keys() - declared}'
    compiler = (ROOT / (JAVA + 'minecraft/age/AgeSymbolCompiler.java')).read_text()
    assert '"envlightning", "envmeteor", "envscorch"' in compiler, 'Meteor is not accepted by the core dispatcher'
    for loader in ('forge', 'fabric'):
        path = ROOT / f'src/{loader}/java/net/mystcraft/{loader}/mixin/AgePvpMixin.java'
        assert path.is_file(), 'Missing loader PvP hook'
    return data, mapped, {'scope': 'Static registry source-path audit; not runtime or exact legacy parity proof',
                         'reference_commit': '9bc8ddc061845df0cd5ea47f4fdef773cff786ae',
                         'static_symbols': len(declared), 'classified': len(mapped),
                         'modern_flags_true': sum(bool(s.get('modern_implemented')) for s in data['symbols']),
                         'groups': groups}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-report', action='store_true')
    args = parser.parse_args()
    _, _, report = audit()
    if args.write_report:
        (ROOT / 'docs/static-symbol-implementation-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f"PASS: {report['classified']}/145 static symbols classified against compiler/runtime source paths; {report['modern_flags_true']} implementation flags true (source coverage only).")
