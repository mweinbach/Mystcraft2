## Curios, Trinkets and Wildfire's Gender Mod

Curios and Trinkets are optional. The JARs load without them, and the GameTests do not install them. A development
run includes Curios with `-PwithCurios` (Forge), Trinkets with `-PwithTrinkets` (Fabric), and the Gender Mod with
`-PwithGender`. `CuriosLoaded` is true when either accessory mod is loaded. The lookups are the same: chest armor
wins, then the first matching accessory stack.

| Mod | Loader | Version | Coordinate |
| --- | --- | --- | --- |
| Curios | Forge | 1.19.2-5.1.4.1, the build Mekanism 10.3.9 compiled against | `top.theillusivec4.curios:curios-forge:1.19.2-5.1.4.1` (`:api` at compile time), `https://maven.theillusivec4.top/` |
| Trinkets | Fabric | 3.4.2 (Modrinth `OLwsMJv4`, file `trinkets-3.4.2.jar`) | `maven.modrinth:trinkets:OLwsMJv4` |
| Cardinal Components | Fabric, compile and a Trinkets dev run | Base and Entity 5.0.0-beta.1, the jars nested in Trinkets 3.4.2. Mekanism does not bundle them | `dev.onyxstudios.cardinal-components-api:cardinal-components-base:5.0.0-beta.1` and `cardinal-components-entity`, `https://maven.ladysnake.org/releases` |
| Wildfire's Female Gender Mod | Forge | File `Female-Gender-Mod-forge-1.19.1-3.0.1.jar` (CurseForge project 481655, file 3920268). Its `mods.toml` version is `1.19-3.0.0`. The file lists Minecraft 1.19, 1.19.1 and 1.19.2 | `curse.maven:female-gender-forge-481655:3920268`, `https://cursemaven.com` |
| Wildfire's Female Gender Mod | Fabric | File `Female-Gender-Mod-fabric-1.19.2-3.0.1.jar` (CurseForge project 456319, file 4023966). Its `fabric.mod.json` version is `1.19-3.0.0` and it depends on Minecraft `~1.19` | `curse.maven:female-gender-456319:4023966` |

Both loaders have a 1.19.2 Gender Mod build, so the integration is ported rather than skipped.

### What an accessory slot does

Upstream's Curios class does not tag Mekanism items into `curios:body`, and it does not special-case the portable
QIO dashboard. The dashboard is not a jetpack, a mode item or a HUD provider. It shows up only because the slot scan
returns every stack. Forge registers Curios' body slot (`SlotTypePreset.BODY`) and renders the jetpack and the
armored jetpack when those items are `ISpecialGear`. Fabric registers a Trinkets `body/body` slot for the player and
renders those same two items. The Trinkets slot uses the `trinkets:tag` validator and the back-slot icon. Equipping
still needs the item in that tag, or in `curios:body` on Forge, which this port does not add. A datapack can.

Once a stack is in a slot, both loaders do what upstream did:

- `IJetpackItem.getPrimaryJetpack` uses the chest slot, then the first accessory jetpack. The MekaSuit bodyarmor is
  a jetpack item, so it counts.
- The chest, legs, head and feet mode keys search accessory slots for a mode item that can equip that armor slot.
  A gas item also has to contain gas. The packet is `PacketModeChangeCurios`. Trinkets slot ids are `group/name`.
- The HUD appends `IItemHUDProvider.addCurioHUDStrings` for every accessory stack.
- The MekaSuit charge-distribution module charges accessory stacks after the player inventory, while charge remains.
- The chargepad charges one accessory stack only when the player inventory charged nothing.

`AccessoryGameTests` installs a stand-in inventory, so it passes with neither mod present. It checks the dashboard
scan, that a chest jetpack beats an accessory jetpack, that the mode packet steps a jetpack from normal to hover,
and that the flag being false ignores the slot.

### Gender Mod

Chest items report the upstream 10.3.9 values. Helmets report nothing.

| Item | Covers | Always hides | Resistance | Tightness |
| --- | --- | --- | --- | --- |
| Jetpack, scuba tank | no | no | 0 | 0 |
| Armored jetpack, MekaSuit bodyarmor | yes | yes | 0 | 0 |
| Hazmat gown | yes | no | 0.5 | 0.25 |
| Bronze chestplate | yes | no | 0.9 | 0 |
| Lapis lazuli chestplate | yes | no | 0.6 | 0.1 |
| Osmium chestplate | yes | no | 1 | 0 |
| Refined glowstone chestplate | yes | no | 0.95 | 0 |
| Refined obsidian chestplate | yes | no | 1 | 0 |
| Steel chestplate | yes | no | 1 | 0 |

Forge attaches those values as the `IGenderArmor` capability the Gender Mod registers. Fabric calls
`WildfireAPI.addGenderArmor` at startup, because that build has no capability. The shared helper never imports
`com.wildfire`. The adapter class is loaded by name only when `wildfire_gender` is present. With the mod absent,
`initCapabilities` adds nothing and the GameTest does not load the adapter.
