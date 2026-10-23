# Minecraft 1.19.2 / Fabric migration

`mods/icbm_port` is an independent Fabric build inside the Mystcraft collection, targeting Minecraft 1.19.2 and Java 17.

Reference: [BuiltBrokenModding/ICBM-Classic](https://github.com/BuiltBrokenModding/ICBM-Classic), `prod/1.12` at `9c1d985a375adc2868ff47707b3a636452d0b63a`.

## Implementation map

| Package | Responsibility |
| --- | --- |
| `block`, `entity` | Explosive blocks, primed payloads, missiles, source attribution |
| `payload`, `simulation` | Catalog, effect dispatch, persisted resistance rays and gas diffusion, moving redmatter, conserved flying blocks |
| `exposure` | Persistent chemical/viral/radiation doses, protection, infection, fallout and decay |
| `launcher`, `flight`, `core` | Inventories, target/energy networks, screens, staged flight, saved virtual travel and temporary reentry tickets |
| `energy` | Transactional Fabric energy, rechargeable batteries and configuration |
| `delivery` | Grenades, carts, handheld launchers, mixed clusters, held-item actions and carrier passengers |
| `defense`, `radio` | Radar, guided SAMs, EMP fields/extenders, machine screens, channel/relay messaging and protective blocks |
| `api`, `command`, `integration` | Executable extension registry/events, operator commands and optional EMI/JEI/REI viewers |
| `content`, `client` | Recipe components, sulfur loot, original model rendering and client initialization |
| `tools` | Resource generation/validation and source recipe mappings |

The [README](README.md) describes gameplay. [PARITY.md](docs/PARITY.md) records source mappings and differences; [upstream-inventory.json](docs/upstream-inventory.json) inventories 660 production Java files, 467 resource paths, 130 recipe/factory paths and 42 configuration sources. Counts do not establish feature equivalence.

## Adaptation boundaries

Forge registration, capabilities, events and networking are replaced by Fabric APIs. Terrain effects execute on the server thread with bounded work and saved cursors. Large effects can take multiple ticks; ray resolution, gas cell limits and modern block resistance change exact shapes. Exposure is native persistent world state. Missile virtual travel uses generation records and bounded temporary tickets, favoring at-most-once transfer across interrupted saves.

Power uses the bundled Team Reborn Energy API. The port preserves numeric source costs where implemented, not Forge capability compatibility. Configurations and addon interfaces are modern replacements. Original seat/riding controls, frame animation/support accuracy, complete radio command vocabulary, ownership/faction behavior and Forge-only integrations remain outside this implementation. Source artwork is replaced by original geometry and vanilla texture references. Two-client networking and complete survival balance still need broader playtesting.

The independent `icbm_port` namespace does not convert legacy Forge saves, items or settings. Subsystem documents describe concrete behavior and limits.

## Licensing

Upstream code and team-created recipe data are MIT licensed; `LICENSE-ICBM-CODE.md` is included in both JARs. Artwork has separate terms prohibiting Fabric ports, so upstream textures, models, sounds and language files are excluded. Vanilla textures are referenced, not redistributed. The embedded Team Reborn Energy API retains its own MIT license inside its nested JAR.
