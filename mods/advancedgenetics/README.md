# Advanced Genetics for Forge and Fabric 1.19.2

An unofficial port of [Advanced Genetics](https://github.com/TechnoVisionDev/Advanced-Genetics)
for **Minecraft 1.19.2** and **Java 17**, built for two loaders:

- **Forge 43.5.0**
- **Fabric Loader 0.16.14** with **Fabric API 0.77.0+1.19.2**

Upstream is a Fabric mod; this port is based on its 1.0.3 release, commit
`f1d8ea28ed3a404446074c8c967c468cd049a458`.

Status: **in progress**. All upstream 1.0.3 gameplay has been ported. Both loaders pass the
dedicated-server GameTests listed below, and the Forge build passed a single-player client check.
Neither loader has had a survival play-through or dedicated-server multiplayer testing, and the
Fabric client has not been checked by hand.

## Layout

Gameplay, registry definitions, recipes, assets and data live once in `src/main`. Each loader
adds a thin adapter, and neither has gameplay of its own:

- `src/forge`: the `@Mod` entrypoint, capabilities, `ForgeConfigSpec`, events, `SimpleChannel`
  networking, `mods.toml`, and the Forge mixin config.
- `src/fabric`: the Fabric entrypoints, Fabric API registration and events, Team Reborn Energy
  and Transfer API lookups, the player-genetics mixin, `fabric.mod.json`, and the Fabric mixin
  config.

Forge builds as the `:advancedgenetics` subproject of the repository. Fabric builds from the
separate Gradle root in `fabric/`, which keeps Loom isolated from ForgeGradle.

## Build and test

From the repository root:

```sh
./scripts/gradle.sh :advancedgenetics:build
./scripts/gradle.sh :advancedgenetics:runGameTestServer
./scripts/gradle.sh -p mods/advancedgenetics/fabric build
./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest
python3 mods/advancedgenetics/scripts/check_loader_parity.py
python3 scripts/emc.py check
```

Add `-PwithProjectE` to the Forge GameTest run or `-PwithItemAlchemy` to the Fabric one to
check the EMC values against the real mod, as CI does.

The JARs are written to:

- Forge: `build/libs/advancedgenetics-forge-1.19.2-<version>.jar`, with no dependencies beyond Forge.
- Fabric: `fabric/build/libs/advancedgenetics-fabric-1.19.2-<version>.jar`. It needs Fabric API,
  and bundles Team Reborn Energy 2.3.0 and night-config 3.6.4.

Install the same loader's JAR on both the client and the server. Cross-loader multiplayer is not
supported.

The development clients run with `./scripts/gradle.sh :advancedgenetics:runClient` and
`./scripts/gradle.sh -p mods/advancedgenetics/fabric runClient`.

The parity check confirms the following across the two JARs:

- Each JAR carries only its own loader's descriptor and loader references.
- The ID, version, description and license match.
- Every asset, data file and shared class is present in both, and every resource is byte-identical.
- Both mixin configs list the same shared mixins, and each refmap maps them to production names.
- No GameTest code or fixtures ship.

## Gameplay

- **Collection:** metal, diamond and netherite scalpels scrape organic matter from 39 mob types.
  Syringes draw your blood.
- **Machines:** the cell analyzer, DNA extractor, DNA decrypter, plasmid infuser, blood purifier
  and plasmid injector. They accept overclockers, can be picked up with the crowbar, and expose
  energy and items to other mods: Forge Energy and item handlers on Forge; Team Reborn Energy and
  Transfer API item storage on Fabric. Automation inserts only valid items into the input slot,
  and extracts only from the output slot. The plasmid infuser and injector work on that slot in
  place, so automation leaves an unfinished plasmid or a purified syringe that is waiting for a
  plasmid where it is.
- **Genes:** 38 configurable genes, plus the basic filler gene. They cover potion effects, flight, no hunger, wall climbing,
  web walking, teleport and dragon's breath keys, fireballs, Milky, Beelicious, Meaty, Wooly,
  Eat Grass, Lay Egg, Keep Inventory, Infinity, Scare Creepers and Scare Skeletons, Mob Sight,
  damage immunities, the dragon health crystal, and death effects.
- **Commands:** `/gene add <player> <gene>`, `/gene remove <player> <gene>` and
  `/gene clear <player>` (permission level 2), with gene-name suggestions.
- **Configuration:** `config/AdvancedGenetics.toml` uses the upstream settings and defaults. Both
  loaders write the same file and apply the same corrections:
  - values of the wrong type fall back to their defaults;
  - out-of-range numbers are clamped;
  - unknown keys are removed.

  Both loaders reload the file when it is modified in place, correct it, and apply the new values
  without a restart; Fabric applies them on the server thread. Neither syncs these settings to
  clients, as Forge does not sync COMMON configs. See [PARITY.md](PARITY.md#config).
- **EMC:** every item has a value for ProjectE and Item Alchemy, shipped as shared data in both
  JARs. See [`emc/README.md`](emc/README.md) and [docs/emc.md](../../docs/emc.md).

## Loader adaptation

| Upstream Fabric | Forge | Fabric port |
| --- | --- | --- |
| Cardinal Components player component | `advancedgenetics:player_genetics` capability | Player mixin, saved under the top-level `advancedgenetics:player_genetics` key; upstream's `cardinal_components` entry is read when that key is absent |
| Team Reborn Energy | Forge Energy capability | Team Reborn Energy (`EnergyStorage.SIDED`), transactional, on every side |
| Fabric Transfer API and `ImplementedInventory` | `WorldlyContainer` plus item-handler capabilities | `WorldlyContainer` exposed through `ItemStorage.SIDED` |
| Extended screen handlers | `IForgeMenuType` and `NetworkHooks.openScreen` | Extended screen handlers |
| Forge Config API Port | `ForgeConfigSpec` | night-config with ForgeConfigSpec's corrections |
| Fabric events and gene mixins | Forge events | Mixins and Fabric events (see below) |
| Fabric key bindings and networking | Key mappings and a `SimpleChannel` | Fabric key bindings and play-channel packets |

On Fabric, gene hooks run where Forge fires the matching event, so both loaders behave the same:

- **Damage genes:** `Player.hurt`, at the start, for server players.
- **Wither Hit and Venom:** `Player.attack`, at the start.
- **Milky, Beelicious, Meaty and Wooly:** `Player.interactOn`, except for spectators.
- **Death genes:** `ServerPlayer.die`, at the start.
- **Gene ticks:** the end of `Player.tick`, on the server.
- **Eat Grass:** `UseBlockCallback`.
- **Fire charges:** `UseItemCallback`, skipped for spectators and items on cooldown, as Forge's
  event is.
- **Scare goals:** `ServerEntityEvents.ENTITY_LOAD`.
- **Respawn copy:** `ServerPlayerEvents.COPY_FROM`.
- **Sync:** on join, respawn and dimension change.

Keep Inventory, Web Walking, Climb Walls, Infinity and Mob Sight (client) are shared mixins on
both loaders. Forge's patched bow asks `ArrowItem.isInfinite` whether to consume plain arrows, so
Forge alone adds a small mixin there for the Infinity gene.

Mixins are remapped to production names through generated refmaps: SRG on Forge, intermediary on
Fabric.

**Tags:** recipes use exact vanilla items and the `minecraft:` and `advancedgenetics:` tags, as
upstream's do; nothing references `forge:` tags, so there is nothing to map to Fabric's
conventional `c:` tags. Both loaders keep upstream's recipes byte for byte.

## Differences from upstream

These upstream defects are fixed in the port:

- Energy tooltips format correctly.
- Cell analyzer recipes keep their input and output when synced to clients.
- The plasmid size limit is read after config load.
- Automation cannot insert invalid items.
- Interacting with a player no longer cancels unrelated interactions.
- Milking a stack of buckets uses one bucket.
- Beelicious drops the honey bottle when the inventory is full.
- Machine menus close when the player moves away or the machine is removed.
- Unknown gene names in saves are skipped.
- Malformed syringe data no longer crashes injection.
- Command errors are reported to the sender instead of printed to the server console.
- Saved machine energy is clamped to capacity.
- Spectators cannot climb walls.
- Mob Sight runs only on the client.
- Eat Grass uses only the main hand.
- Attack and item genes run only on the server.
- A totem of undying now prevents death-triggered genes.
- Fire-charge fireballs are owned by the shooter, so kills by their impact or explosion are
  credited to the player.
- A fireball hit damages its target once, not twice.
- Players without the Flight gene keep spectator flight.
- Automation no longer pulls unfinished plasmids or waiting syringes out of the plasmid infuser
  and injector.

Retained upstream behavior:

- Fire charges shoot only when the Shoot Fireballs gene is absent or disabled.
- Wither Hit and a successful Venom roll replace the hit's damage.
- Gene cooldowns reset on respawn.
- Machines take one tick more than their configured duration.
- Loading a plasmid into a syringe makes the blood impure again, so it must be purified a second time.
- Poison and wither immunity cancel one damage event and remove the effect.
- The dragon health crystal absorbs the whole hit.
- The cloning machine has config entries but no block, as in 1.0.3.

## Save compatibility

- The Fabric build reads upstream 1.0.3 player genetics. Block, item and menu IDs and machine NBT
  are unchanged from upstream. Loading a whole upstream world has not been tested.
- Forge saves genetics in `ForgeCaps` and Fabric under its own key. Worlds are not migrated
  between loaders.

## API change for add-ons

Upstream's Cardinal Components key, `ComponentRegistry.PLAYER_GENETICS`, no longer exists, so a
Fabric add-on that looks up the upstream component finds nothing. Call
`com.technovision.advancedgenetics.api.AdvancedGeneticsApi.genetics(player)` instead; it returns
the player's `EntityGeneticsComponent` on both loaders. Old saves are unaffected (see above).

## Known gaps

- Machine progress and energy are synced through vanilla menu data, which is limited to 16-bit
  values. Very large configured capacities or durations display incorrectly.
- There is no JEI, REI or EMI integration (upstream 1.0.3 had none).
- Client behavior has no automated tests.
  - A Forge development-client check opened all six machine screens and ran the cell analyzer
    with energy and overclocking. It also checked the analyzer's slot filter, the creative tab,
    cell tints, `/gene` suggestions and the key bindings.
  - The Mob Sight glow, the teleport and dragon's breath keys, and flight have not been checked
    by hand.
  - The Fabric client has only been built, not run.
- On both loaders the config reloads only when the file is modified in place. An editor that saves
  by replacing the file may need a restart for the change to apply on Linux.

## GameTests

All GameTests are shared in `src/gametestCommon` and run on both loaders against real server
players, so the loader's actual hooks run. A small `TestPlatform` per loader
(`src/gametestForge` and `src/gametestFabric`) reads each loader's energy and item APIs, its
genetics save location and a damage probe. It also decodes the loader's sync packet, delivers key
packets through the loader's channel, fires the loader's login, respawn and dimension-change
events, and runs the loader's config corrections (`ForgeConfigSpec` itself on Forge,
`FabricConfig` on Fabric). Tests are merged into the mod only for the GameTest run and never ship
in a JAR.

The 39 shared tests cover:

- genetics persistence through player NBT, skipping unknown genes;
- the public `AdvancedGeneticsApi.genetics` accessor;
- respawn gene copy and Keep Inventory;
- the Keep Inventory, Web Walking, Climb Walls and Infinity mixins;
- the death genes, damage-cancelling genes and the dragon crystal;
- server-tick flight, effects and hunger, and spectator flight without the gene;
- fire-charge shooting (not for spectators or items on cooldown), fireball ownership, kill credit
  and single-hit damage;
- Wither Hit, Eat Grass, Milky, and the Scare Creepers and Scare Skeletons goals;
- gene-name argument parsing, and the `/gene` commands with their permission check;
- recipe loading and network round trips;
- the sync and key packets on the wire, including the key name's length limit;
- key presses arriving through the loader's channel, and one gene sync for each gene change,
  login, respawn and dimension change;
- config corrections: missing keys and comments are filled in, out-of-range numbers are clamped,
  values of the wrong type fall back to defaults, unknown keys are removed, and a corrected file
  stays unchanged when corrected again;
- the cell analyzer's configured time, energy, face rules and menu;
- every machine stage through syringe injection;
- automation leaving the infuser's and injector's in-place work alone;
- the EMC audit: every registered item has a decision in `emc/values.json` and both shipped EMC
  files load. With `-PwithProjectE` (Forge) or `-PwithItemAlchemy` (Fabric) the real EMC mod runs
  too, and every live value must match.

Fabric adds three tests:

- upstream Cardinal Components saves load, and the current key takes precedence over them;
- a config reload writes the corrected file back, hands the new values to the game thread, ignores
  its own rewrite and leaves a malformed file and the current values alone;
- editing the real config file reloads it through night-config's `FileWatcher`.

## Attribution and license

Advanced Genetics is by TechnoVision (developer), with art by KirboCabana and TheUnderTaker11. It
is MIT licensed; see [LICENSE](LICENSE), which is packaged in both JARs. Assets and data are
imported unchanged from the upstream commit above. `ImplementedContainer` adapts Juuz's CC0
`ImplementedInventory`.

The Fabric JAR bundles these unmodified libraries as separate nested JARs, as upstream's Fabric
build did:

- Team Reborn Energy 2.3.0, which is MIT licensed and carries its own license.
- night-config 3.6.4, which is LGPL-3.0. Its license and the GPL-3.0 it extends are in
  [`licenses/`](licenses) and in the JAR's `META-INF/licenses`.

This port is not an official upstream release.
