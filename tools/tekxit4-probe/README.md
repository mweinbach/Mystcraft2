# Tekxit 4 boot probe

Test-only tooling for booting every Mystcraft2 Fabric port inside the Tekxit 4 (20.0.4) pack. Nothing here ships in
a port jar. See [docs/tekxit4-testing.md](../../docs/tekxit4-testing.md) for the runs it supported.

## `mixin_audit.py`

A static check that needs no game launch. It reads the mixin configs of the given jars, nested jars included, and
reports injectors that cannot coexist:

- two `@Redirect`s or `@ModifyConstant`s on the same call or constant;
- any injector that depends on a method body (INVOKE, FIELD, CONSTANT, `@WrapOperation`, `@ModifyExpressionValue`
  and so on) in a method that another mod `@Overwrite`s. Sodium 0.4.4 overwrites `ItemRenderer.renderQuadList`,
  which is how the Mekanism port's item renderer crashed the Tekxit 4 client.

```sh
# Ports against the pack and against each other (what CI runs)
python3 tools/tekxit4-probe/mixin_audit.py --targets tools/tekxit4-probe/tekxit4-exclusive-mixins.json --ports collected-jars
# Regenerate the pack list from a Tekxit 4 mods folder (checks the given port jars at the same time)
python3 tools/tekxit4-probe/mixin_audit.py --pack <pack>/mods --ports <port jars> --dump-pack tools/tekxit4-probe/tekxit4-exclusive-mixins.json
```

`tekxit4-exclusive-mixins.json` lists only the pack's exclusive injectors and overwrites (805 entries). Rules are
covered by `scripts/tests/test_mixin_audit.py`.

`scripts/check_unmapped_overrides.py` is the companion check for remapped jars: an interface of ours that leaves
a method such as `getBlockState()` to a Minecraft superclass works in development, but production renames only
Minecraft's method, and the call then throws `AbstractMethodError`.

`scripts/check_mixin_member_names.py` fails when a mixin merges a field without a mod prefix into a Minecraft class.
JurassiCraft's `Entity` mixin declared `KEY`, and Tree Chop's own `Entity` mixin, which also declares `KEY`, then
failed to apply in Tekxit 4. `scripts/tests/test_check_mixin_member_names.py` runs the check over `mods/` in CI.

## The probe mod

`./scripts/gradle.sh --no-daemon -q -p tools/tekxit4-probe build` builds
`build/libs/tekxit4-probe-fabric-1.19.2-1.0.0.jar`. Install it only in a disposable instance or server.

**Server.** On server start it checks the port namespaces: registries, recipes, tags, loot tables, and Item Alchemy
EMC values when `tekxit-probe/emc/` holds the expected values. It writes `tekxit-probe/server-report-*.json`. With
`-Dtekxitprobe.runTests=<GameTest classes>` and `-Dfabric-api.gametest.command=true` on a dedicated server, it runs
those GameTests after `Done`, logs `[TEKXIT_PROBE] TEST PASS/FAIL` and writes `gametests.json`. With
`-Dtekxitprobe.stopAfterTests=true` it then stops the server and writes `server-stopped.txt`. The Tekxit pack blocks
Minecraft's own GameTest server: BCLib's level-generation hook needs a registry access that the GameTest server never
sets.

**Client.** It is inactive unless `tekxit-probe/autorun` exists in the game directory, or
`-Dtekxitprobe.client=true` is set. When active it:

1. dismisses the pack's pre-title screens, such as Xaero's update notice;
2. creates a disposable creative world named `Tekxit Probe` and closes post-join screens, such as Origins' origin
   picker;
3. places blocks from each port and takes screenshots of the title screen, the world, every port creative tab and
   one EMI recipe page per port;
4. runs the client checks: missing block and item models or textures, missing names, key binding conflicts and
   MetalRender status;
5. runs the GameTests, then saves and quits.

The result goes to `tekxit-probe/client-report.json`, and the log carries a
`[TEKXIT_PROBE] client result PASSED|FAILED` line.

Delete `tekxit-probe/autorun`, the probe jar and the `Tekxit Probe` world before playing the instance.
