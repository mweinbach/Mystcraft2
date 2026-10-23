// Mekanism 1.19.2 port - CraftTweaker script for CraftTweakerGameTests, run on both loaders.
// Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
//
// -PwithCraftTweaker copies this file into the GameTest server's scripts folder. Every added recipe uses an input no
// Mekanism recipe or other GameTest uses, and every removed recipe is one no other GameTest needs.
import mods.mekanism.api.ingredient.ChemicalStackIngredient.GasStackIngredient;
import mods.mekanism.api.ingredient.ChemicalStackIngredient.InfusionStackIngredient;
import mods.mekanism.api.ingredient.ChemicalStackIngredient.PigmentStackIngredient;
import mods.mekanism.api.ingredient.ChemicalStackIngredient.SlurryStackIngredient;
import mods.mekanism.api.ingredient.FluidStackIngredient;
import mods.mekanism.api.ingredient.ItemStackIngredient;

// Item to item, item to energy, two items to item, and the sawmill's chanced secondary output.
<recipetype:mekanism:enriching>.addRecipe("gametest/enriching", ItemStackIngredient.from(<item:minecraft:dead_bush>), <item:minecraft:diamond>);
<recipetype:mekanism:crushing>.removeByName("mekanism:crushing/flint_to_gunpowder");
<recipetype:mekanism:energy_conversion>.addRecipe("gametest/energy_conversion", ItemStackIngredient.from(<item:minecraft:dead_bush>), 12345);
<recipetype:mekanism:combining>.addRecipe("gametest/combining", ItemStackIngredient.from(<item:minecraft:dead_bush> * 2), ItemStackIngredient.from(<item:minecraft:sponge>), <item:minecraft:diamond_block>);
<recipetype:mekanism:sawing>.addRecipe("gametest/sawing", ItemStackIngredient.from(<item:minecraft:dead_bush>), <item:minecraft:stick> * 2, <item:minecraft:string>, 0.25);
<recipetype:mekanism:sawing>.removeByName("mekanism:sawing/bed/purple");

// Gases: the chemical tag manager adds ethene to a new gas tag, and a tag-based ingredient reads it back.
<tag:mekanism/gas:mekanism:gametest_crt>.add(<gas:mekanism:ethene>);
<recipetype:mekanism:chemical_infusing>.addRecipe("gametest/chemical_infusing", GasStackIngredient.from(<tag:mekanism/gas:mekanism:gametest_crt>, 2), GasStackIngredient.from(<gas:mekanism:chlorine>), <gas:mekanism:oxygen> * 3);
<recipetype:mekanism:chemical_infusing>.removeByName("mekanism:chemical_infusing/sulfur_trioxide");
<recipetype:mekanism:activating>.addRecipe("gametest/activating", GasStackIngredient.from(<gas:mekanism:ethene> * 5), <gas:mekanism:oxygen> * 5);
<recipetype:mekanism:oxidizing>.addRecipe("gametest/oxidizing", ItemStackIngredient.from(<item:minecraft:dead_bush>), <gas:mekanism:ethene> * 50);
<recipetype:mekanism:nucleosynthesizing>.addRecipe("gametest/nucleosynthesizing", ItemStackIngredient.from(<item:minecraft:dead_bush>), GasStackIngredient.from(<gas:mekanism:antimatter> * 2), <item:minecraft:nether_star>, 100);

// Infuse types, pigments and slurries.
<recipetype:mekanism:metallurgic_infusing>.addRecipe("gametest/metallurgic_infusing", ItemStackIngredient.from(<item:minecraft:sponge>), InfusionStackIngredient.from(<infuse_type:mekanism:carbon> * 10), <item:minecraft:wet_sponge>);
<recipetype:mekanism:metallurgic_infusing>.removeByName("mekanism:metallurgic_infusing/netherrack_to_crimson_nylium");
<recipetype:mekanism:infusion_conversion>.addRecipe("gametest/infusion_conversion", ItemStackIngredient.from(<item:minecraft:dead_bush>), <infuse_type:mekanism:bio> * 7);
<recipetype:mekanism:painting>.addRecipe("gametest/painting", ItemStackIngredient.from(<item:minecraft:bone_block>), PigmentStackIngredient.from(<pigment:mekanism:red> * 128), <item:minecraft:red_concrete>);
<recipetype:mekanism:pigment_extracting>.addRecipe("gametest/pigment_extracting", ItemStackIngredient.from(<item:minecraft:bone_block>), <pigment:mekanism:white> * 256);
<recipetype:mekanism:dissolution>.addRecipe("gametest/dissolution", ItemStackIngredient.from(<item:minecraft:sponge>), GasStackIngredient.from(<gas:mekanism:sulfuric_acid>), <slurry:mekanism:dirty_tin> * 500);
<recipetype:mekanism:crystallizing>.addRecipe("gametest/crystallizing", GasStackIngredient.from(<gas:mekanism:ethene> * 100), <item:minecraft:slime_ball>);

// Fluid inputs. CraftTweaker for Fabric 1.19.2 has no fluid stacks or <fluid:> bracket, so these use fluid tags, which
// both loaders have. Lava is an input of no other Mekanism recipe, and no GameTest feeds it to these machines.
<recipetype:mekanism:separating>.removeByName("mekanism:separator/brine");
<recipetype:mekanism:separating>.addRecipe("gametest/separating", FluidStackIngredient.from(<tag:fluids:minecraft:lava>, 2), <gas:mekanism:oxygen>, <gas:mekanism:ethene> * 2);
<recipetype:mekanism:washing>.addRecipe("gametest/washing", FluidStackIngredient.from(<tag:fluids:minecraft:lava>, 5), SlurryStackIngredient.from(<slurry:mekanism:dirty_tin>), <slurry:mekanism:clean_copper>);
<recipetype:mekanism:reaction>.addRecipe("gametest/reaction", ItemStackIngredient.from(<item:minecraft:dead_bush>), FluidStackIngredient.from(<tag:fluids:minecraft:lava>, 100), GasStackIngredient.from(<gas:mekanism:ethene> * 10), 60, <item:minecraft:diamond>);
<recipetype:mekanism:rotary>.addRecipe("gametest/rotary", FluidStackIngredient.from(<tag:fluids:minecraft:lava>, 10), <gas:mekanism:ethene> * 10);
<recipetype:mekanism:rotary>.removeByName("mekanism:rotary/hydrofluoric_acid");
