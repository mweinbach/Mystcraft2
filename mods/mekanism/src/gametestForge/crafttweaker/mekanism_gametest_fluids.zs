// Mekanism 1.19.2 port - CraftTweaker script for ForgeCraftTweakerGameTests, run on Forge only.
// Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
//
// The overloads that take or return CraftTweaker fluid stacks (<fluid:>, IFluidStack) exist only in CraftTweaker for
// Forge 1.19.2. -PwithCraftTweaker copies this file next to mekanism_gametest.zs. Liquid ethene is an input of no other
// Mekanism evaporating recipe, and antimatter gas of no rotary recipe. (The thermal evaporation tests check that a
// valve refuses lava, so the evaporating recipe does not use lava.)
import mods.mekanism.api.ingredient.ChemicalStackIngredient.GasStackIngredient;
import mods.mekanism.api.ingredient.FluidStackIngredient;

<recipetype:mekanism:evaporating>.addRecipe("gametest/evaporating", FluidStackIngredient.from(<fluid:mekanism:ethene> * 10), <fluid:mekanism:oxygen>);
<recipetype:mekanism:rotary>.addRecipe("gametest/rotary_condensing", GasStackIngredient.from(<gas:mekanism:antimatter>), <fluid:minecraft:lava> * 1000);
