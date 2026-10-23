# Loader parity and known gaps

Advanced Genetics 1.19.2 ships as one gameplay implementation with two thin loader
adapters. This file records how each loader service is provided on Forge and on Fabric,
and the differences that remain. Shared code reaches loader services only through
`platform.Platform`; every Forge event and capability the port uses has a Fabric
counterpart, listed below.

## Layout

| Path | Contents |
| --- | --- |
| `src/main` | All gameplay, registries, recipes, assets, data and the shared mixins. |
| `src/main/java/com/technovision/advancedgenetics/platform/Platform.java` | The `Platform` service interface. Each loader installs its backend (`forge.ForgePlatform`, `fabric.FabricPlatform`) before any registry class loads. |
| `src/forge` | `AdvancedGeneticsForge`, the client entrypoint, `ForgeConfig` (a `ForgeConfigSpec`), `ForgeCapabilityEvents` (player genetics, machine energy and item handlers, respawn copy, ticking, sync), `ForgeGeneticsEvents` (gameplay events), `ForgeNetwork`, `mods.toml`, and the Forge-only `ArrowItemMixin`. |
| `src/fabric` | `AdvancedGeneticsFabric`, the client entrypoint, `FabricConfig`, `FabricTransfer` (Team Reborn Energy and Transfer API), `FabricEvents` (Fabric API callbacks), `fabric.mod.json`, and two Fabric-only mixins: `PlayerFabricMixin` stores player genetics and adds the player hooks Fabric API lacks; `ServerPlayerFabricMixin` is the death hook. |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | Development-only GameTest mods and fixtures; never packaged. |

## Loader services

| Service | Forge | Fabric | Notes |
| --- | --- | --- | --- |
| Player genetics storage | Capability attached to every player by `ForgeCapabilityEvents`, saved in `ForgeCaps` under `advancedgenetics:player_genetics`. | Every player implements `GeneticsHolder` through `PlayerFabricMixin`, saved under the top-level `advancedgenetics:player_genetics` key. Upstream's `cardinal_components` entry is read when that key is absent. | Same data. Saves are not migrated between loaders. |
| Genetics API for other mods | `AdvancedGeneticsApi.genetics(Player)` | Same shared method. | Replaces upstream's Cardinal Components key; see "API changes from upstream". |
| Machine energy | Forge Energy capability on every side (`ForgeCapabilityEvents.MachineProvider`). | Team Reborn Energy 2.3.0 `EnergyStorage.SIDED` (bundled), registered by `FabricTransfer`. | Same buffer: insert and extract up to the capacity per operation. |
| Machine items | Item handler capability: unsided access sees the whole inventory, sided access follows the machine's face rules (`SidedInvWrapper`). | Transfer API `ItemStorage.SIDED` over the same `WorldlyContainer`, with the same face rules. | |
| Config file | `config/AdvancedGenetics.toml` through a `ForgeConfigSpec` registered as a COMMON config. | The same file through night-config (`FabricConfig`), which applies the corrections `ForgeConfigSpec` makes. | Both reload the file when it changes; see "Config". |
| Networking | `SimpleChannel` `advancedgenetics:main`: index 0 is the gene sync, index 1 the key press. | Play channels `advancedgenetics:sync_genetics` and `advancedgenetics:key_pressed`. | Payload bodies are encoded by the same shared code (`SyncGeneticsPacket`, `KeyPressedPacket`). The wire formats differ, so clients and servers must use the same loader. |
| Gameplay events | `ForgeGeneticsEvents`: `RightClickBlock`, `EntityInteract`, `LivingDeathEvent`, `AttackEntityEvent`, `RightClickItem`, `EntityJoinLevelEvent`, `LivingAttackEvent` and command registration. | `UseBlockCallback`, `UseItemCallback`, `ServerEntityEvents.ENTITY_LOAD` and `CommandRegistrationCallback` in `FabricEvents`; `Player.interactOn`, `Player.attack` and `Player.hurt` in `PlayerFabricMixin`; `ServerPlayer.die` in `ServerPlayerFabricMixin`. | Each mixin injects where Forge fires the matching event. |
| Player lifecycle | `ForgeCapabilityEvents`: `PlayerEvent.Clone` copies genes on respawn, the server `PlayerTickEvent` (end) ticks them, and login, respawn and dimension change sync them. | `ServerPlayerEvents.COPY_FROM` copies genes; the tail of `Player.tick` (server side, in `PlayerFabricMixin`) ticks them; `ServerPlayConnectionEvents.JOIN`, `ServerPlayerEvents.AFTER_RESPAWN` and `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD` sync them. | Adding or removing a gene also syncs, on both loaders. |
| Client | `FMLClientSetupEvent` registers screens and item properties; `RegisterKeyMappingsEvent`, `RegisterColorHandlersEvent.Item` and the client tick event do the rest. | The client entrypoint calls the same shared registration, `KeyBindingHelper`, `ColorProviderRegistry` and `ClientTickEvents`. | Both use the shared `AdvancedGeneticsClient`. The Mob Sight mixin (`client.MinecraftMixin`) is shared. |

## Event ordering

On Forge, `PlayerInteractEvent.RightClickItem` fires after vanilla's spectator and cooldown
checks; Fabric's `UseItemCallback` fires before them. The Fabric adapter therefore skips
spectators and items on cooldown, so both loaders reach the same decision.

## Config

Both loaders read the same file, write it in the same format and apply the same corrections:
wrong types fall back to the default, numbers are clamped into range, missing keys are added,
unknown keys are removed, and each key's comment is rewritten from the spec. The shared
`ConfigGameTests` check these rules against `ForgeConfigSpec` itself on Forge and against
`FabricConfig` on Fabric.

Both watch the file with night-config's `FileWatcher` and reload it when it is modified in
place. On Fabric:

- a reload waits 250 ms for the save to finish, then corrects and rewrites the file as Forge does;
- the new values reach the game on the server thread, or immediately when no server is running;
- a file that cannot be parsed is left untouched and the current values stay.

Forge hands the values over on its watcher thread. Neither loader syncs this config to
clients, at login or after a reload, because Forge does not sync COMMON configs.

Remaining difference: when Forge corrects a file, its loader first keeps a zipped backup of it.
Fabric rewrites the file without a backup. An edited file that cannot be parsed changes nothing on
either loader: Forge prints the parse error from its watcher thread, and Fabric logs a warning.

## API changes from upstream

Upstream registered player genetics as the Cardinal Components key
`ComponentRegistry.PLAYER_GENETICS`. This port does not use Cardinal Components on either loader,
so that key and the `cardinal-components` entrypoint are gone. A Fabric add-on that looks up the
upstream component finds nothing. Use `com.technovision.advancedgenetics.api.AdvancedGeneticsApi.genetics(player)`,
which returns the player's `EntityGeneticsComponent` on both loaders. Old Fabric saves still
load: `PlayerFabricMixin` reads upstream's `cardinal_components` entry when the new key is absent.

## Known gaps

- Clients and servers must run the same loader; cross-loader play and world migration are not
  supported.
- Machine progress and energy are synced through vanilla menu data, which is limited to 16-bit
  values, so very large configured capacities or durations display incorrectly.
- There is no JEI, REI or EMI integration (upstream 1.0.3 had none).
- Client behavior has no automated tests, and the Fabric client has only been built, not run.
- The config file is reloaded only when it is modified in place, as on Forge. An editor that
  saves by replacing the file may need a restart for the change to apply on Linux.

See the README for gameplay status and upstream differences.

## Verification

Run after any change to either adapter or the shared code:

```
./scripts/gradle.sh :advancedgenetics:build
./scripts/gradle.sh -p mods/advancedgenetics/fabric build
python3 mods/advancedgenetics/scripts/check_loader_parity.py
python3 scripts/emc.py check
./scripts/gradle.sh :advancedgenetics:runGameTestServer -PwithProjectE
./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest -PwithItemAlchemy
```

The parity check confirms descriptor isolation, matching mod ID, version, description and
license, no cross-loader class references, byte-identical shared resources and classes,
production mixin refmaps, and that no GameTest code ships.
