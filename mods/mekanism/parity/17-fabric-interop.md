## Fabric interop: tags, foreign items, recipe viewers and IMC

### Tags

Fabric mods tag their content with `c:` tags, and Fabric API 0.77 defines only a few of them. `scripts/generate_fabric_tags.py`
writes the Fabric-only bridges: each `forge:` tag Forge defines, or Mekanism defines with a `c:` name, gets a Fabric
copy with an optional reference to that `c:` tag, and Mekanism's own content goes into the `c:` tags. It scans all four
modules (core, Additions, Generators, Tools). Forge's own tags are bridged once, in the core jar. A module's `c:` tags
ship in its own jar. Its `forge:` tags that Forge does not define (Generators' four fluids) get a core bridge holding
only the `c:` reference, because Loom fails when a module's Fabric and shared resources both have the same path. CI
runs `generate_fabric_tags.py --check`. `check_loader_parity.py` lets a module's Fabric jar carry extra files only
under `data/c/tags` and `data/forge/tags`, as new paths whose bytes match `src/<module>/fabric/resources`.

The vanilla materials the recipes use map to the plural names Fabric mods use on 1.19.2:

| `forge:` | `c:` |
| --- | --- |
| `wrenches`, `tools/wrench` | `wrenches` |
| `cobblestone/normal`, `cobblestone/deepslate` | `cobblestone`, `cobbled_deepslate` |
| `slimeballs`, `string`, `leather` | `slime_balls`, `string`, `leather` |
| `obsidian`, `gravel`, `gunpowder`, `nether_stars`, `mushrooms` | the same names |
| `bookshelves`, `crops/wheat`, `fences/wooden` | `bookshelves`, `wheat_crops`, `wooden_fences` |
| `glass/<color>`, `glass_panes/<color>` | `<color>_glass_blocks`, `<color>_glass_panes` |

Of these, Fabric API 0.77 defines only `c:bookshelves`, and only as a block tag, so most of the `c:` names are mod
conventions and stay empty until a mod fills them. `forge:sand` (20 recipes) and the `colorless`, `tinted` and `silica`
glass tags have no mapping yet. Mekanism's wrench check reads `mekanism:configurators`, which holds `forge:wrenches` and
`forge:tools/wrench`. On Fabric those include `c:wrenches`, so BuildCraft's, Extreme Reactors' and other mods' wrenches
rotate and dismantle Mekanism blocks, and the Configurator is in `c:wrenches` for their wrench checks.

### Other mods' items

When Mekanism asks a Fabric mod's block entity or item for a capability, `FabricTransfer.foreignCapability` answers from
the Fabric lookups that mod registered on:

| Capability | Block | Item |
| --- | --- | --- |
| Items | `ItemStorage.SIDED` | `ItemStorageLookup.ITEM` (`fabric:item_storage`) |
| Fluids | `FluidStorage.SIDED` | `FluidStorage.ITEM` |
| Energy | Team Reborn Energy `EnergyStorage.SIDED` | Team Reborn Energy `EnergyStorage.ITEM` |
| Gas, infusion, pigment, slurry, heat | `MekanismChemicalLookups.GAS` and its siblings | `MekanismChemicalLookups.GAS_ITEM` and its siblings |

Fabric API 0.77 has no item lookup for items that hold items; Fabric added `ItemStorage.ITEM` in a later version.
`ports.mekanism.fabric.api.ItemStorageLookup.ITEM` uses that lookup's id (`fabric:item_storage`), storage class and
context class, so a mod that builds the lookup from the id gets the same instance. Mekanism reads other mods' items from
it but does not register its own items there. A provider on any of these lookups must not answer by asking that block
or stack for the same Mekanism capability, because the lookup is what Mekanism falls back to.
`FabricForeignInteropGameTests` covers a test gas tank item, a test pouch, a test wrench, and the tag bridges.

### Recipe viewers

The only recipe-viewer plugin is JEI's (see the JEI section). On Fabric it is the `jei_mod_plugin` entrypoint, built
against the JEI 11.6 Fabric API. There is no REI or EMI plugin. EMI shows Mekanism's categories through its JEI
compatibility layer, which loads JEI plugins when JEI is installed alongside EMI. Recipe transfer and ghost ingredient
dragging are JEI features, and the port has not checked them through EMI. REI is not supported.

### IMC

Forge's InterModComms does not exist on Fabric. The Fabric build rewrites `net.minecraftforge.fml.InterModComms` in the
shared sources onto `ports.mekanism.lib.fml.InterModComms`, so `MekanismIMC` (the MekaSuit and Meka-Tool module
messages) runs on that mirror. A Fabric addon that sends Mekanism IMC must compile against
the port's Fabric jar and call `MekanismIMC` or `ports.mekanism.lib.fml.InterModComms.sendTo`. It cannot use Forge's
class, or a copy of Mekanism's API built for Forge. Messages must be queued before Mekanism posts `InterModProcessEvent`:
at the end of its main initializer on a dedicated server, and of its client initializer on a client. Fabric does not
promise an initializer order between mods. On a dedicated server, a message sent from a main initializer that runs
after Mekanism's is not delivered. `MekanismIMC` records the sender as `minecraft`, because a foreign mod has no active
Mekanism mod container. The four-argument `sendTo` takes an explicit sender id.
