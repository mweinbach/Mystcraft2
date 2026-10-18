# Minecraft 1.19.2 / Fabric migration

`mods/icbm_port` is an independent Fabric build inside the Mystcraft collection. It does not compile legacy Forge sources or include Mystcraft code. The user-selected target is Minecraft 1.19.2, Fabric, Java 17.

Reference: [BuiltBrokenModding/ICBM-Classic](https://github.com/BuiltBrokenModding/ICBM-Classic), `prod/1.12` at `9c1d985a375adc2868ff47707b3a636452d0b63a`.

## Implementation map

| Package | Responsibility |
| --- | --- |
| `core` | Direct-flight normalization and bounded Minecraft target animation |
| `block`, `entity` | Explosive blocks, primed payloads, missiles and persisted flight |
| `payload` | 27-definition catalog, 23 content variants, effect dispatch, bounded persistent work queues, configuration and reload command |
| `launcher` | Inventory, tier rules, target/energy/frequency persistence, screens, packet validation and local component networks |
| `delivery` | Grenades, carts, handheld launchers, bomblets, mixed cluster crafting, held-item actions and item cargo |
| `defense` | Radar, guided SAM interceptors, persistent EMP zones, utilities and protective blocks |
| `content` | Recipe components and sulfur mob-loot integration |
| `client` | Entity rendering and client initialization |
| `tools` | Resource generation, validation and source recipe mappings |

The [README](README.md) describes current gameplay. [PARITY.md](docs/PARITY.md) maps upstream systems and acceptance criteria; [upstream-inventory.json](docs/upstream-inventory.json) inventories 660 production Java files, 467 resource paths, 130 recipe/factory paths and 42 configuration sources. Inventory counts do not establish feature equivalence.

## Deliberate adaptations and outstanding work

The new code replaces Forge registration, capability/event and network systems with Fabric APIs. Effects run on the server thread with bounded terrain work, saved cursors and no background world access. Large payloads use simplified geometry and entity effects. Gas does not reproduce the original diffusion/toxicity capability; radiation and full redmatter behavior remain outstanding.

Launcher tiers currently use redstone-fed internal energy buffers and local adjacency rules. They do not implement an external energy API or the complete original network topology. Targeted missiles follow bounded arcs in loaded chunks; the upstream virtual distant-flight/chunkloading system is absent. Renderers use vanilla item geometry, with original JSON blocks. Interactive two-client testing, custom animation, sounds and comprehensive survival balancing remain pending.

The module uses its own `icbm_port` namespace. Legacy Forge worlds, items, entities and configuration files are not automatically converted. Keep this alpha separate from production worlds until the missing systems and migration policy are resolved.

## Licensing

Upstream code and team-created data, including recipes, are MIT licensed. Their notice is preserved in `LICENSE-ICBM-CODE.md`. Artwork has separate terms which prohibit Fabric ports, so no upstream textures, models, sounds or language files are imported. Source-mapped recipes are documented separately from new artwork in [RECIPES.md](docs/RECIPES.md). Vanilla textures are referenced, not redistributed.
