# Fabric energy and batteries

Machines and batteries use Team Reborn Energy API 2.3.0, bundled with the mod. The API uses integer energy units, shown as E (or kE for thousands) in the UI. Numeric source costs are retained where documented by each machine; this is not a Forge Energy capability bridge.

The battery stores 100,000 E by default and transfers up to 10,000 E per operation in either direction. It persists charge in item NBT, shows charge in its item bar/tooltip and accepts ordinary compatible Fabric energy providers. Use it on a compatible machine to insert power; sneak-use extracts power. Machine inventory slots also charge from batteries. Energy moves through Fabric transactions: aborted transfers leave both ends unchanged.

Launchers expose 1,200 E per API transfer. Source bases/cruise start empty, hold 1,200 E and consume 1,200 E per successful launch. The legacy prototype retains its older capacity and initial charge. A launcher can consume redstone dust for up to 1,000 E as a documented compatibility fallback; other energy normally comes from compatible power mods. Connected launcher bases share available energy without duplication. Radar and EMP costs and buffers are listed in [DEFENSE.md](DEFENSE.md).

`config/icbm_port/energy.json` controls `batteryCapacity`, `batteryInput` and `batteryOutput`. Values must be integers from 1 to 1,000,000,000. Unknown keys, invalid values and files over 16 KiB are rejected. `/icbm energy reload` requires operator permission level 2; an invalid reload preserves active settings. Lowering capacity clamps the usable charge of existing batteries. Machine capacity/cost settings are not changed by these battery keys.
