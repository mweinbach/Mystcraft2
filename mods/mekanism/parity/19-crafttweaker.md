# CraftTweaker

Upstream's `mekanism.common.integration.crafttweaker` package (Mekanism `v1.19.2-10.3.9.13`, MIT) is imported. It is
optional on both loaders: the JARs compile against CraftTweaker but do not depend on it. CraftTweaker finds the
integration by scanning for its annotations (`@ZenRegister`, `@ZenCodeType`, `@BracketResolver`,
`@ZenCodeType.Expansion`, the `@CraftTweakerPlugin` class), so nothing loads those classes when CraftTweaker is absent.
`MekanismHooks.CraftTweakerLoaded` is set in `hookConstructor`, as upstream.

| | Forge | Fabric |
| --- | --- | --- |
| CraftTweaker build compiled against | `com.blamejared.crafttweaker:CraftTweaker-forge-1.19.2:10.1.44` (upstream's version) | `com.blamejared.crafttweaker:CraftTweaker-fabric-1.19.2:10.1.44` |
| Declared | `mods.toml` optional dependency `[10.0.6,)`, ordered `AFTER` (upstream's range) | `fabric.mod.json` `suggests` `>=10.1.44` |
| Development run | `-PwithCraftTweaker` | `-PwithCraftTweaker` (adds IngredientExtensionAPI 3.0.5, FauxCustomEntityData 2.0.2, Reflections 0.10.2 and Javassist 3.29.0-GA, which Loom does not unpack from CraftTweaker's nested jars) |

Both are on `maven.blamejared.com`. 1.19.2 builds 10.1.35 to 10.1.56 exist for each loader. JEITweaker (4.0.13) is not
imported, so upstream's `jeitweaker` package is not built.

## What scripts can do on each loader

Every recipe type has CraftTweaker's `<recipetype:...>` manager on both loaders, so `removeByName`, `removeByRegex`,
`removeAll` and the other generic operations work for all 27 types. "Add" below means Mekanism's own
`addRecipe` overloads.

| Recipe type(s) | Manager | Forge add | Fabric add | Remove (both) |
| --- | --- | --- | --- | --- |
| `crushing`, `enriching`, `smelting` | `ItemStackToItemStackRecipeManager` | yes | yes | yes |
| `energy_conversion` | `ItemStackToEnergyRecipeManager` | yes | yes | yes |
| `gas_conversion`, `oxidizing` | `ItemStackToGasRecipeManager` | yes | yes | yes |
| `infusion_conversion` | `ItemStackToInfuseTypeRecipeManager` | yes | yes | yes |
| `pigment_extracting` | `ItemStackToPigmentRecipeManager` | yes | yes | yes |
| `compressing`, `purifying`, `injecting` | `ItemStackGasToItemStackRecipeManager` | yes | yes | yes |
| `nucleosynthesizing` | `NucleosynthesizingRecipeManager` | yes | yes | yes |
| `metallurgic_infusing` | `MetallurgicInfuserRecipeManager` | yes | yes | yes |
| `painting` | `PaintingRecipeManager` | yes | yes | yes |
| `combining` | `CombinerRecipeManager` | yes | yes | yes |
| `sawing` | `SawmillRecipeManager` | yes | yes | yes |
| `chemical_infusing` | `ChemicalInfuserRecipeManager` | yes | yes | yes |
| `pigment_mixing` | `PigmentMixingRecipeManager` | yes | yes | yes |
| `activating`, `centrifuging` | `GasToGasRecipeManager` | yes | yes | yes |
| `dissolution` | `ChemicalDissolutionRecipeManager` | yes | yes | yes |
| `crystallizing` | `ChemicalCrystallizerRecipeManager` | yes | yes | yes |
| `separating` | `ElectrolysisRecipeManager` | yes | yes, fluid tag/`Fluid` inputs | yes |
| `washing` | `FluidSlurryToSlurryRecipeManager` | yes | yes, fluid tag/`Fluid` inputs | yes |
| `reaction` | `PressurizedReactionRecipeManager` | yes | yes, fluid tag/`Fluid` inputs | yes |
| `rotary` | `RotaryRecipeManager` | yes | fluid to gas only | yes |
| `evaporating` | `FluidToFluidRecipeManager` | yes | no | yes |

Shared on both loaders: the `<gas:>`, `<infuse_type:>`, `<pigment:>`, `<slurry:>` and `<robit_skin:>` brackets and their
validators; `ICrTGasStack`/`ICrTInfusionStack`/`ICrTPigmentStack`/`ICrTSlurryStack` and the chemical expansions;
`ItemStackIngredient` and the `GasStackIngredient`/`InfusionStackIngredient`/`PigmentStackIngredient`/
`SlurryStackIngredient` factories (single stacks, tags with amounts, `|` unions); `FluidStackIngredient` from a `Fluid`
or a fluid tag; the chemical tag managers (`<tag:mekanism/gas:...>` etc.); `FloatingLong`; module data expansions; the
recipe dumps and recipe-to-script handlers for the non-fluid types; and the example scripts upstream ships in
`data/mekanism/scripts`.

## Fabric gaps

- **Fluid stacks.** CraftTweaker for Fabric 1.19.2 (checked through 10.1.56) has no
  `com.blamejared.crafttweaker.api.fluid` package: no `IFluidStack`, `MCFluidStack`, `CTFluidIngredient` and no
  `<fluid:>` bracket. `FluidStackIngredient` is shared, but on Fabric only its `Fluid`, fluid-tag
  (`<tag:fluids:...>`, with or without `* amount`), `createMulti` and `|` factories exist; `from(IFluidStack)`,
  `from(CTFluidIngredient)`, `test`, `testType` and `representations` are Forge-only, as are the `IFluidStack` and
  `CTFluidIngredient` casts. So on Fabric:
  - separating, washing and reaction recipes can be added from fluid tags;
  - rotary recipes can be added fluid to gas only (gas to fluid returns an `IFluidStack`);
  - evaporating recipes cannot be added (fluid output);
  - the five fluid recipe types have no Mekanism recipe handler, because the handlers decompose through CraftTweaker
    fluid stacks. `/ct recipes` falls back to CraftTweaker's generic dump for them, and CraftTweaker's recipe
    replacer cannot rewrite them;
  - the `RotaryRecipe` and `FluidToFluidRecipe` script expansions (`CrTRotaryRecipe`, `CrTFluidToFluidRecipe`) are
    missing.

  Removal works on both loaders through `<recipetype:mekanism:...>`. The upstream example scripts for evaporating,
  rotary gas to fluid and the `<fluid:>` forms in separating, washing and reaction only work on Forge.
  Shared files mark this code with `// region forge-only` ... `// endregion forge-only`, which the Fabric build
  blanks. Whole Forge-only classes are in `src/forge`.
- **Custom content.** `#loader mekanismcontent` scripts (custom gases, infuse types, pigments, slurries and robit
  skins) run inside Mekanism's first registry event, from a listener on CraftTweaker's Forge mod bus. Fabric posts the
  same registry events, but CraftTweaker for Fabric initialises in its own entrypoint with no ordering against
  Mekanism's, so the hook is Forge-only. The builders are shared.
- **ProjectE expansions** (`CrTNSSResolverExpansion`, `CrTNSSCastingExpansion`) are Forge-only because ProjectE is.

## Deviations from upstream

- `asImmutable()` on a mutable chemical stack returns an immutable `CrT*Stack` wrapping a copy. Upstream returns
  another mutable wrapper around the same stack, so later `setAmount()` calls on either wrapper changed both.

## IngredientExtensionAPI on Fabric

CraftTweaker for Fabric bundles IngredientExtensionAPI, which hooks `Ingredient.toNetwork`, `fromNetwork` and
`fromJson` to sync its own ingredient types. Its `toNetwork` hook writes an int marker for every ingredient it does not
own and lets the method continue. When it ran before the port's hook for Forge ingredient types (`forge:nbt`,
`forge:partial_nbt`, `forge:difference`, ...), the port's `-1` marker came after that int and the ingredient could not be
read back. Which HEAD injection runs first depends on mixin config order. The port's `IngredientMixin` now uses
MixinExtras `@WrapMethod` for all three methods, so it runs outside every other mod's injection.
`CustomIngredientGameTests` cover the round trip with `-PwithCraftTweaker`. Before the change they failed in 3 of 5
flagged Fabric runs.

The wrappers only handle the types the port registers. A JSON `type` the port does not know, an ingredient subclass
it does not serialize, and a network id it did not write all go to the original method, so IngredientExtensionAPI
reads and writes CraftTweaker's own types (`crafttweaker:any`, `crafttweaker:list`, ...), alone or inside an array.
A `type` that no mod reads is still rejected after the original method returns, as on Forge.
`CraftTweakerGameTests.craftTweakerIngredientTypesReadAndSync` reads `crafttweaker:any` from JSON, alone and in an
array, and round-trips both over the network.

## Tests

`CraftTweakerGameTests` (both loaders) and `ForgeCraftTweakerGameTests` (Forge only, listed in
`gametest-loader-only.txt`) read `-Dmekanism.gametest.crafttweaker`, which the GameTest runs set only for
`-PwithCraftTweaker`. With the flag, Gradle copies `src/gametestCommon/crafttweaker/*.zs` (and on Forge
`src/gametestForge/crafttweaker/*.zs`) into the run's `scripts/` folder; without it, that folder is deleted. The tests
then expect either the scripted recipes (`crafttweaker:gametest/<type>`) and the removed upstream recipes gone, or the
reverse.

| Covered by a GameTest | Forge | Fabric |
| --- | --- | --- |
| Added: enriching, energy_conversion, combining, sawing, chemical_infusing (tag-based gas input), activating, oxidizing, nucleosynthesizing, crystallizing, metallurgic_infusing, infusion_conversion, painting, pigment_extracting, dissolution | yes | yes |
| Added from a fluid tag: separating, washing, reaction, rotary (fluid to gas) | yes | yes |
| Removed: crushing, sawing, chemical_infusing, metallurgic_infusing, separating, rotary | yes | yes |
| Mutable stack `asImmutable()` is immutable and detached | yes | yes |
| Gas tag manager adds ethene to `mekanism:gametest_crt` | yes | yes |
| Enrichment chamber accepts and processes the scripted input | yes | yes |
| Added with `<fluid:>`/`IFluidStack`: evaporating, rotary (gas to fluid) | yes | n/a |
| Content loader registers `crafttweaker:gametest_gas` and `crafttweaker:gametest_slurry` | yes | n/a |
