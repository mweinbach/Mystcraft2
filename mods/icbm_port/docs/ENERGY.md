# Fabric energy and batteries

Machines and batteries use Team Reborn Energy API 2.3.0, bundled with the mod. The API uses integer energy units, shown as E (or kE for thousands) in the UI. Numeric source costs are retained where documented by each machine; this is not a Forge Energy capability bridge.

The battery stores 100,000 E by default and transfers up to 10,000 E per operation in either direction. It persists charge in item NBT, shows charge in its item bar/tooltip and accepts ordinary compatible Fabric energy providers. Use it on a compatible machine to insert power; sneak-use extracts power. Machine inventory slots also charge from batteries. Energy moves through Fabric transactions: aborted transfers leave both ends unchanged.

Launchers expose 1,200 E per API transfer. Source bases/cruise start empty, hold 1,200 E and consume 1,200 E per successful launch. The legacy prototype retains its older capacity and initial charge. A launcher can consume redstone dust for up to 1,000 E as a documented compatibility fallback; other energy normally comes from compatible power mods. Launchers no longer move energy between each other; each base pays its own launch cost. Radar and EMP costs and buffers are listed in [DEFENSE.md](DEFENSE.md).

## What other mods see

Launchers and defense machines answer `EnergyStorage.SIDED` on every side with an **insert-only** view: `supportsExtraction()` is false and `extract` returns 0. Cables, batteries and other mods can charge a machine but never drain it. The machines' own costs use the full internal buffer.

Launcher frames, supports, screens and connectors also answer `EnergyStorage.SIDED`, with a network view adapted from MIT ICBM-Classic `content/blocks/launcher/network/NetworkEnergyStorage.java`: it is insert-only, reports the summed amount/capacity of every connected launcher base, and splits an insertion so each base is offered `floor(amount / n) + 1` (bases that are full take less; the transaction commits only what was accepted). The view is rebuilt from the bounded launcher network walk for each lookup, so nothing is cached or duplicated. Upstream's network extraction path is not reproduced.

The EMP tower's buffer is dynamic (firing cost plus 100 ticks of upkeep, recomputed as its radius changes), drains its upkeep every tick, and refuses insertion while cooling down; see [DEFENSE.md](DEFENSE.md).

EMP blasts also drain other mods' energy devices, as upstream EMP did. EMP payload detonations use the blast's radius (the configured EMP payload radius unless an API caller scales the blast); EMP towers use their configured range. In one Fabric transaction the blast empties:

- blocks whose `EnergyStorage.SIDED` view allows extraction, unsided first and then the first side that allows it;
- `EnergyStorage.ITEM` items in the inventories of non-spectator players and inventory entities such as chest minecarts, reached through `ContainerItemContext`.

Each storage gets up to 16 extraction calls, so rate-limited devices still empty. Only loaded chunks are inspected. ICBM machines are not drained because their public view is insert-only; EMP fields disable them instead. The EMP blast itself (not the tower) also empties `EnergyStorage.ITEM` items in block-entity inventories inside its cube and in dropped item entities (upstream `CapabilityEmpInventory`); mob equipment is not affected. The `emp_energy_drain` section of `config/icbm_port/payloads.json` governs every one of these drains: `enabled` (default `true`; when `false` no EMP removes any energy, while creepers, primed explosives, missiles and EMP fields are still affected), `maxBlocks` (nearest loaded block entities examined, default 1,024, at most 8,192; the blast's own inventory drain stays capped at 1,024) and `maxEntities` (nearest players or inventory entities examined, and nearest dropped items drained by the blast, default 64, at most 1,024). `/icbm_payloads reload` applies changes; an invalid file keeps the active settings.

`config/icbm_port/energy.json` controls `batteryCapacity`, `batteryInput` and `batteryOutput`. Values must be integers from 1 to 1,000,000,000. Unknown keys, invalid values and files over 16 KiB are rejected. `/icbm energy reload` requires operator permission level 2; an invalid reload preserves active settings. Lowering capacity clamps the usable charge of existing batteries. Machine capacity/cost settings are not changed by these battery keys.
