# Tekxit 4 — Mystcraft 0.4 verification

Tested the official Tekxit 4 server 20.0.0 on Minecraft 1.19.2, Java17, Fabric Loader0.18.4 and Fabric API0.77.0+1.19.2, with all440 loaded mods. The disposable server binds localhost25567. Pack mods were retained.

| Installed artifact | SHA-256 |
| --- | --- |
| Mystcraft Fabric0.4.0-dev | `fa372eea04436fc440d3de5728aeedf1d3063ca881de1fff9884686b47129151` |
| Optional server helper | `2f47fc07a13326b08bd4d6c4b27f294f8a321063babf534da6be2977fc69a203` |

The final run passed **25 focused checks** at23:46:04 UTC and **145 full gameplay checks** at23:46:24 UTC on2026-10-18. Evidence: `tekxit4/v04-final-runtime.log` and `v04-final-artifacts.sha256` in the validation archive. A new JVM restored37 Ages and passed both persistence checks at23:48:33 UTC: canonical identity, seed, ordered pages, modified chunks, independent time/weather, and deferred crystal geometry without loading destination chunks. Restart evidence is `tekxit4/v04-final-restart.log`; installed artifact hashes are unchanged.

## Modded treasure fixture correction

The first two candidates exposed a test assumption, not a production loot defect. At seed45022, the loaded table returned eight valid Mystcraft page stacks and an enchanted Simply Swords diamond chakram. The table contained the intact original Mystcraft4–8-roll pool, followed by four Simply Swords pools and one Amethyst Imbuement hammer pool. Counting the whole table as Mystcraft's rolls wrongly rejected that legitimate ninth stack.

The corrected helper validates the exact loaded source-pool schema, executes its native roll/count/weight/luck behavior, and compares that same seeded source output against the whole table by exact stack count and NBT. Additional output must be declared by the appended pools. Across64 seeds the accepted pack run retained all Mystcraft output and produced one declared extra item from those five pools. No production loot, pack configuration or mod was removed to make the check pass. Candidate1/2 logs and artifact hashes remain in the evidence archive.

## Pack adapters and scope

Existing shared adapters and native fixtures continue to cover BCLib generation boundaries, MCA villagers, Origins/Industrial Revolution player initialization, Stxck item merging and Xaero player data. Their earlier investigations are preserved in [the0.3 record](tekxit4-testing-historical-0.3.md); final0.4 acceptance comes from the logs above. The pack retains its preexisting startup warnings and missing-tag reports.

The pack's background threads can keep the JVM alive after vanilla shutdown. Test cleanup issues `stop`, waits for all dimensions to save and Distant Horizons IO to stop, and only then terminates the exact lingering process; that final process status143 is documented separately from passing gameplay checks.

These are dedicated-server tests with native fixture players. They do not establish full-pack graphical-client behavior, simultaneous human clients or a long survival/soak session. Plain Forge and Fabric graphical checks, exact source/runtime scope and installation guidance are recorded in [validation](../mods/mystcraft/docs/validation.md) and [server testing](../mods/mystcraft/docs/server-testing.md). Both loader production JARs remain development builds for disposable-world testing.
