#loader mekanismcontent
// Mekanism 1.19.2 port - CraftTweaker content script for ForgeCraftTweakerGameTests, run on Forge only.
// Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
//
// The mekanismcontent loader runs inside Mekanism's first registry event. Fabric has no equivalent hook yet.
import mods.mekanism.content.builder.GasBuilder;
import mods.mekanism.content.builder.SlurryBuilder;

GasBuilder.builder().color(0x3366CC).build("gametest_gas");
SlurryBuilder.clean().color(0xCC6633).build("gametest_slurry");
