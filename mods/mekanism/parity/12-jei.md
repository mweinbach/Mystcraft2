## JEI

JEI 11.6.0.1015 is an optional dependency on both loaders (`mandatory = false` / `suggests`). The jars do not bundle
it. Mekanism's own startup never loads a JEI class: Forge discovers `@JeiPlugin`, and Fabric loads the
`jei_mod_plugin` entrypoint, only when JEI itself asks. `MekanismAPI.getJeiHelper()` throws if JEI is absent and
otherwise reflects `MekanismJEIHelper`. The shared GameTests and JUnit tests run with JEI off the runtime classpath.

The plugin code is shared. Fluid stacks are Mekanism's millibucket stacks on both loaders; JEI's fluid ingredient is
Forge's `FluidStack` or, on Fabric, `IJeiFluidIngredient` in transfer droplets. `JeiFluids` converts through JEI's
`IPlatformFluidHelper` (one bucket is 1,000 mB on Forge and 81,000 droplets on Fabric). Recipe slots and the fluid
returned when a gauge is hovered both go through that conversion. Items and chemicals are already JEI ingredient types. Gas, infusion, pigment and
slurry are custom ingredient types (`MekanismJEI.TYPE_GAS` and the three siblings), drawn by `ChemicalStackRenderer`.

Categories registered for every core `MekanismRecipeType`: crushing, enriching, smelting, chemical infusing,
combining, separating, washing, evaporating, activating, centrifuging, crystallizing, dissolution, compressing,
purifying, injecting, nucleosynthesizing, energy conversion, gas conversion, oxidizing, infusion conversion, pigment
extracting, pigment mixing, metallurgic infusing, painting, reaction and sawing. Rotary is one recipe type and two
categories (condensentrating and decondensentrating). The nutritional liquifier, thermoelectric boiler and SPS are
JEI-only recipe lists, not recipe types. `JeiRecipeTypeCoverageTest` fails if a new `MekanismRecipeType` has no
category. Catalysts include the machine and, where the block has a factory type, each factory tier.

Generators registers the fission reactor category (water and cooled-coolant recipes). Tools adds anvil repair recipes
for its tools and armor. Additions has no JEI plugin upstream.

Click areas are the progress bars and inner screens GUIs already tag with `MekanismJEIRecipeType`, plus the robit
repair anvil area. Opening JEI from a Mekanism screen sets `switchingToJEI` so the container stays open. On Fabric
that is `ScreenEvent.Opening`, posted from `setScreen`.

Not in this slice: recipe transfer. The formulaic assemblicator, the robit's crafting grid, both QIO dashboards, and
dragging ingredients onto filter ghost slots do not fill from JEI. Upstream's handlers for those are
`FormulaicRecipeTransferInfo`, `QIOCraftingTransferHandler` and `GhostIngredientHandler`.
