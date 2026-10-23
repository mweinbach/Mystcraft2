# Loader parity and known gaps

Advanced Genetics 1.19.2 ships as one gameplay implementation with two thin loader
adapters. This file records how each loader service is provided on Forge versus
Fabric, so the remaining differences are documented rather than hidden. The audit
underlying it: every shared-code path reaches a `Platform`/`ClientPlatform` method;
every event and capability that exists on one loader has an equivalent on the other.

## Layout

| Path | Contents |
| --- | --- |
| `src/main/java/com/technovision/advancedgenetics` | All gameplay, registries, shared code (reaches services only through `platform.Platform`), resources and data. |
| `src/forge/resources/META-INF/mods.toml` | The Forge descriptor. |
| `src/fabric/resources/fabric.mod.json` | The Fabric descriptor; the isolated Loom build lives in `fabric/`. |
| `src/main/java/com/technovision/advancedgenetics/platform` | The shared `Platform` interface and its two implementations are not here — each loader supplies one under `src/<loader>/java/.../platform`, reached through a single backend. |
| `src/forge` | ForgeMod, client entrypoint, config backend (`ForgeConfig`), capability events (player genetics + machine energy/item handlers), gameplay event routing and network registration. |
| `src/fabric` | Fabric platform, client entrypoint, config loader (`FabricConfig`), Team Reborn Energy / Transfer API wiring, the two player mixins that implement component storage on players, and event routing through Fabric API callbacks plus those mixins. |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | Development-only GameTest mods and fixtures; never packaged. |

## Loader services

| Service | Forge (`src/forge`) | Fabric (`src/fabric`) | Notes |
| --- | --- | --- |
| Player genetics storage | A capability provider registered on every player via `ForgeCapabilityEvents` (replacing the old Cardinal Components component). Saved under key `advancedgenetics:player_genetics`. | Every player implements `GeneticsHolder` through `PlayerFabricMixin`; same save key is preserved. | Same data, different mechanism — a mixin-implemented holder instead of a capability provider. |
| Machine energy (machines) | Forge Energy via the machine's per-face item/energy handler (`ForgeCapabilityEvents.MachineProvider`). | Team Reborn Energy 2.3.0 `EnergyStorage.SIDED` (bundled), registered through `FabricTransfer`. Same underlying buffer and view semantics as upstream. |
| Machine items (machines) | Forge item handlers: unsided access sees the whole inventory, sided access follows the machine's face rules (`SidedInvWrapper`). | Fabric Transfer API `ItemStorage.SIDED` with transactions; same unsided-sees-all / sided-follows-face semantics as the Forge side. |
| Config file & format | `config/AdvancedGenetics.toml` via a `ModConfigSpec`. | Same file and TOML format, loaded through night-config (`FabricConfig`) which then applies the corrections that `ModConfigSpec` would have enforced — so validation matches on both sides. |
| Networking (client↔server) | Forge `SimpleChannel` over shared payloads in `ModNetwork` (`KEY_PRESSED`, `SYNC_GENETICS`). | Fabric networking via `ServerPlayNetworking` / `ClientPlayNetworking`; same payload IDs and buffer layout, forwarded to the shared handlers. |
| Event routing — gameplay events | `ForgeGeneticsEvents` registers: RightClickBlock, EntityInteract, PlayerDeath (copy-on-respawn), AttackEntity(attacker), RightClickItem, EntityJoinLevel, **PlayerAttacked(victim)**, plus command registration and player sync on login/respawn/dimension change. | Fabric API callbacks route the same handlers; the four that need a hook (`onEntityInteract`, `onAttackEntity`, `onPlayerAttacked`) are handled in `mixin/fabric/PlayerFabricMixin.java`; death/copy-on-respawn via `ServerPlayerFabricMixin` + `ComponentEvents`. Command registration and player sync use Fabric API callbacks. |
| Client rendering & keybinds | Forge client entrypoint registers render layers, GUIs and the key-bind system directly. | Fabric client entrypoint mirrors them; any hooking that needs a class-level change is done through mixins in the same package (loader-only). |

## Event-by-event parity detail

The one ordering nuance worth recording: on Forge `PlayerInteractEvent.RightClickItem`
fires *after* vanilla's spectator and cooldown checks, while Fabric's
`UseItemCallback` fires *before* them. The shared handler is therefore guarded in the
Fabric adapter (skip when a player is spectating or already cooling down) so both loaders
reach the same decision on whether to consume the interaction.

## Known gaps & upstream differences

- **None remaining.** Every Forge capability and event has an equivalent Fabric path,
  verified by static audit of `src/forge` against `src/fabric`. The parity gate below
  confirms it mechanically: descriptor isolation, mod ID/version/description/license match,
  no cross-loader class references, byte-identical shared resources, matching mixin refmaps,
  and GameTest sources kept out of both JARs.

## Verification

Run after any change to either adapter or the shared code:

```
./scripts/gradle.sh :advancedgenetics:build
./scripts/gradle.sh -p mods/advancedgenetics/fabric build
python3 mods/advancedgenetics/scripts/check_loader_parity.py
```
