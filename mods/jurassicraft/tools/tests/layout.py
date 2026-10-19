"""Source layout for resource tests: one shared gameplay tree plus thin loader overlays."""

import re
from pathlib import Path

MOD = Path(__file__).resolve().parents[2]
SHARED = MOD / "src/main/resources"
OVERLAYS = {"forge": MOD / "src/forge/resources", "fabric": MOD / "src/fabric/resources"}
PLACEMENTS = MOD / "src/main/java/ports/jurassicraft/worldgen/WorldgenPlacements.java"

_STEPS = {"ores": "underground_ores", "plants": "vegetal_decoration"}
_ADD = re.compile(r'add\(list, (species\.id\(\) \+ )?"([a-z_]+)", ([A-Za-z_.]+|tag\("[a-z_/]+"\)), ([A-Za-z_.]+)\);')


def packaged(relative, loader):
    """The file `loader` packages at `relative`: its overlay copy, else the shared one."""
    overlay = OVERLAYS[loader] / relative
    return overlay if overlay.is_file() else SHARED / relative


def packaged_everywhere(relative):
    return all(packaged(relative, loader).is_file() for loader in OVERLAYS)


def placements():
    """Feature name -> (biome tag, generation step) as both loaders add them to biomes.

    Per-species fossil ores are keyed as ``{species}_fossil_block``.
    """
    result = {}
    for species, name, biomes, step in _ADD.findall(PLACEMENTS.read_text(encoding="utf-8")):
        if biomes.startswith("BiomeTags."):
            biomes = "#minecraft:" + biomes.removeprefix("BiomeTags.").lower()
        else:
            biomes = "#jurassicraft:" + biomes[len('tag("'):-len('")')]
        step = _STEPS.get(step) or step.removeprefix("GenerationStep.Decoration.").lower()
        result[("{species}" if species else "") + name] = (biomes, step)
    return result
