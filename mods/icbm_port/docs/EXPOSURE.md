# Exposure and fallout

Independent Fabric adaptation of MIT Java: `BlastGasBase`, `BlastChemical`,
`BlastContagious`, `BlastDebilitation`, `ContagiousPoison`,
`ProtectiveArmorHandler`, `RadioactiveHandler`, `BlockRadioactive`,
`ConfigMain.protectiveArmor`, and `ConfigRadioactive`. Upstream art is not used.

Gas clouds (`payload/GasWork.java`) run every five ticks like upstream
`BlastGasBase`, roll the family's protection for each living entity standing in
a reached cell, count hits per entity for that cloud (upstream
`impactedEntityMap`, saved with the cloud, at most 4,096 entities tracked) and
call `ExposureContent.applyGas(entity, payload, hits)`. Source effect durations
and amplifiers are preserved: debilitation poison18s/fatigue60s/slownessIII60s;
chemical poison/nausea/hunger30s, weakness35s, fatigue60s; viral poisonIV45s,
blindnessIV15s, hungerIV30s, weaknessIV35s/fatigueIV60s. Chemical direct damage
starts above20 hits at max(1,hits*0.1); viral above10 at max(1,hits*0.05). Those
starts, scales and minimums are the exposure config keys
`chemicalToxicityStart/Scale/MinDamage` and `viralToxicityStart/Scale/MinDamage`.
Damage uses the `icbm_port.chemical` and `icbm_port.contagious` sources, which,
like upstream, armor can reduce and invulnerable entities ignore. Colors clouds
never affect entities. `ExposureContent.expose(entity, payload)` remains the
persistent-record entry used by viral spread and API callers.
Viral exposure invokes the blast engine's mutation helper; successfully converted
entities are exempt from direct toxicity damage and their old dose record is removed.

Armor protection requires a helmet by default and sums configured item ratings.
Default optional Atomic Science mask/body/leggings/boots ratings are
0.75/0.15/0.05/0.05. Missing optional items are harmless config entries, not
registered content. There is no invented hazmat crafting tree. Server operators
can rate installed armor IDs in `config/icbm_port-exposure.json`. Source minimum
ratings are0.1 debilitation,0.5 chemical,0.8 viral,1.0 radiation; a qualifying
fractional rating still has source random penetration chance. Creative and
spectator players are exempt.

Per-entity UUID dose and infection records live in the overworld persistent state,
so they survive unload, dimension travel and restart. Antidote integration clears
the record. Gas-cloud toxicity uses the cloud-local counters described above;
viral clouds also mark the persistent record infectious. On top of that the port
adds gradual one-hit-per-second recovery outside exposure and persistent nearby
viral spread.
Spread is configurable, defaults every100 ticks within2 blocks, and can be disabled.
Source viral potion code itself has a TODO instead of a functioning spread potion;
this is an explicit gameplay extension, not a claim of exact source behavior.

Nuclear completion queues loaded-only fallout scans, persisted with their cursor.
Each dimension processes at most4096 positions per tick, radius at most128,
and at most64 pending scans. Stone and dirt/farmland/path/grass/mycelium have the
source20% radioactive replacement chance. Plants/leaves are removed. Only the
source radioactive dirt and stone types are registered. These environmental
blocks are obtainable by fallout and mining; they have no invented crafting recipe.

Radioactive blocks schedule source decay checks with delay5 (random1..5 after
the first tick), plus vanilla random ticks; default decay chance0.0001 returns
clean dirt/stone. Nearby entities within5 blocks have85% effect chance,
2 magic damage and20 ticks of wither unless armor protects them. Configuration
can select random ticks only. Vanilla ash and gas particles supply client feedback.
Original JSON radioactive block models use vanilla stone/dirt with a lime marker.

The fallout radius and scan admission bounds are deliberate server constraints;
this does not claim unlimited upstream blast simulation or full original art.
Tests cover distinct exposure effects, persisted state, treatment, helmet gating,
full protection and radioactive block decay. Root build/test workflow supplies
runtime verification; resource validation alone cannot establish gameplay parity.

Exposure state admits at most 65,536 UUID records both at runtime and on NBT load.
Existing records are never evicted to admit new ones, so unloaded living entities
retain their saved exposure. At capacity, newly encountered UUIDs receive immediate
potion/radiation effects using transient counters; persistent toxicity buildup and
infection spread for those UUIDs resume only after capacity becomes available.
This is an explicit saturation limitation. Permanent KILLED/DISCARDED unload events
remove records; chunk unload and dimension changes preserve them. Dead loaded
entities and recovered zero-dose records are also removed by normal ticks. Records
for entities that disappeared before this cleanup hook existed cannot be reliably
distinguished from unloaded living entities and remain until that UUID loads again
or receives explicit treatment. Neighbor spread checks no longer create empty
records simply by inspecting nearby entities. Tests cover saturated admission,
unload/dimension preservation, death capacity reclamation and actual discard cleanup.
