# Advanced Genetics for Forge 1.19.2

An unofficial Forge port of [Advanced Genetics](https://github.com/TechnoVisionDev/Advanced-Genetics)
for **Minecraft 1.19.2**, **Forge 43.5.0** and **Java 17**. Upstream is a Fabric mod; this port is
based on its 1.0.3 release, commit `f1d8ea28ed3a404446074c8c967c468cd049a458`.

Status: **in progress**. All upstream 1.0.3 gameplay has been ported. The port passes the
dedicated-server GameTests listed below and a single-player client check. It still needs a
survival play-through and dedicated-server multiplayer testing before it is called playable.

## Build and test

From the repository root:

```sh
./scripts/gradle.sh :advancedgenetics:build
./scripts/gradle.sh :advancedgenetics:runGameTestServer
./scripts/gradle.sh :advancedgenetics:runClient
```

The JAR is written to `build/libs/advancedgenetics-1.19.2-<version>.jar`. Install it on both
the client and the server. It has no dependencies beyond Forge. GameTests live in
`src/gametest`, are merged into the mod only for `runGameTestServer`, and never ship in the JAR.

## Gameplay

- **Collection:** metal, diamond and netherite scalpels scrape organic matter from 39 mob types.
  Syringes draw your blood.
- **Machines:** the cell analyzer, DNA extractor, DNA decrypter, plasmid infuser, blood purifier
  and plasmid injector. They use Forge Energy (`ForgeCapabilities.ENERGY`), accept overclockers,
  can be picked up with the crowbar, and expose item handlers for automation. Automation inserts
  only valid items into the input slot, and extracts only from the output slot.
- **Genes:** 38 configurable genes, plus the basic filler gene. They cover potion effects, flight, no hunger, wall climbing,
  web walking, teleport and dragon's breath keys, fireballs, Milky, Beelicious, Meaty, Wooly,
  Eat Grass, Lay Egg, Keep Inventory, Infinity, Scare Creepers and Scare Skeletons, Mob Sight,
  damage immunities, the dragon health crystal, and death effects.
- **Commands:** `/gene add <player> <gene>`, `/gene remove <player> <gene>` and
  `/gene clear <player>` (permission level 2), with gene-name suggestions.
- **Configuration:** `config/AdvancedGenetics.toml` uses the upstream settings and defaults.

## Forge adaptation

| Upstream Fabric API | Forge port |
| --- | --- |
| Cardinal Components player component | `advancedgenetics:player_genetics` capability, copied on respawn and synced on login, respawn, dimension change and gene changes |
| Team Reborn Energy | Forge `EnergyStorage` with upstream's capacity and transfer limits |
| Fabric transfer API and `ImplementedInventory` | `WorldlyContainer` plus Forge item-handler capabilities |
| Extended screen handlers | `IForgeMenuType` and `NetworkHooks.openScreen` |
| Fabric events and mixins with Forge equivalents | Forge events: interactions, attacks, deaths, entity join, living attack, player tick |
| Mixins without a Forge event | Keep Inventory, Web Walking, Climb Walls, Infinity, and Mob Sight (client) |
| Fabric key bindings and networking | Forge key mappings and a `SimpleChannel` |

Mixins are remapped to SRG through a generated refmap, so the reobfuscated JAR works outside
the development environment.

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

Retained upstream behavior:

- Fire charges shoot only when the Shoot Fireballs gene is absent or disabled.
- Wither Hit and a successful Venom roll replace the hit's damage.
- Gene cooldowns reset on respawn.
- Machines take one tick more than their configured duration.
- Loading a plasmid into a syringe makes the blood impure again, so it must be purified a second time.
- Poison and wither immunity cancel one damage event and remove the effect.
- The dragon health crystal absorbs the whole hit.
- The cloning machine has config entries but no block, as in 1.0.3.

## Known gaps

- Fabric saves and Cardinal Components data are not migrated. Cross-loader multiplayer is unsupported.
- Machine progress and energy are synced through vanilla menu data, which is limited to 16-bit
  values. Very large configured capacities or durations display incorrectly.
- There is no JEI integration (upstream 1.0.3 had none).
- Client behavior has no automated tests. A development-client check opened all six machine
  screens. It also ran the cell analyzer with energy and overclocking, and checked its slot
  filter, the creative tab, cell tints, `/gene` suggestions and the key bindings. The Mob Sight
  glow, the teleport and dragon's breath keys, and flight have not been checked by hand.

## GameTests

`runGameTestServer` runs 21 server tests:

- capability persistence through player NBT;
- respawn gene copy and Keep Inventory;
- the Keep Inventory, Web Walking, Climb Walls and Infinity mixins;
- damage-cancelling genes and the dragon crystal;
- server-tick flight, effects and hunger;
- Eat Grass, Milky, and the Scare Creepers and Scare Skeletons goals;
- gene-name argument parsing, and the `/gene` commands with their permission check;
- recipe loading and network round trips;
- the cell analyzer's configured time, energy, face rules and menu;
- every machine stage through syringe injection.

## Attribution and license

Advanced Genetics is by TechnoVision (developer), with art by KirboCabana and TheUnderTaker11. It
is MIT licensed; see [LICENSE](LICENSE), which is packaged in the JAR. Assets and data are
imported unchanged from the upstream commit above. `ImplementedContainer` adapts Juuz's CC0
`ImplementedInventory`. This port is not an official upstream release.
