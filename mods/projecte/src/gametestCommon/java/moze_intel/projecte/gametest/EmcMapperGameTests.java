/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import static moze_intel.projecte.gametest.PEGameTestSupport.TEMPLATE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * The EMC mapper's results. Every value below is what upstream ProjectE 1.1.0 computes on Forge from its own
 * {@code pe_custom_conversions} defaults and vanilla's recipes; the Fabric build must reach the same numbers through
 * its tag bridges and recipe loading.
 */
public class EmcMapperGameTests {

    private static Map<ItemLike, Long> expected() {
        Map<ItemLike, Long> values = new LinkedHashMap<>();
        // Fixed values from defaults.json, several through Forge tags (#forge:gems/diamond, #forge:crops/wheat...).
        values.put(Items.COBBLESTONE, 1L);
        values.put(Items.DIRT, 1L);
        values.put(Items.OAK_LOG, 32L);
        values.put(Items.OAK_PLANKS, 8L);
        values.put(Items.STICK, 4L);
        values.put(Items.COAL, 128L);
        values.put(Items.REDSTONE, 64L);
        values.put(Items.GLOWSTONE_DUST, 384L);
        values.put(Items.DIAMOND, 8_192L);
        values.put(Items.EMERALD, 16_384L);
        values.put(Items.WHEAT, 24L);
        values.put(Items.NETHER_STAR, 139_264L);
        // metals.json: iron through #forge:ingots/iron, gold and copper converted from it.
        values.put(Items.IRON_INGOT, 256L);
        values.put(Items.GOLD_INGOT, 2_048L);
        values.put(Items.COPPER_INGOT, 128L);
        // Derived from crafting, smelting and storage recipes.
        values.put(Items.STONE, 1L);
        values.put(Items.IRON_BLOCK, 2_304L);
        values.put(Items.GOLD_BLOCK, 18_432L);
        values.put(Items.DIAMOND_BLOCK, 73_728L);
        values.put(Items.GLOWSTONE, 1_536L);
        values.put(Items.CRAFTING_TABLE, 32L);
        values.put(Items.CHEST, 64L);
        values.put(Items.FURNACE, 8L);
        values.put(Items.TORCH, 9L);
        values.put(Items.BREAD, 72L);
        values.put(Items.IRON_PICKAXE, 776L);
        // ProjectE's own items.
        values.put(PEItems.ALCHEMICAL_COAL, 512L);
        values.put(PEItems.MOBIUS_FUEL, 2_048L);
        values.put(PEItems.AETERNALIS_FUEL, 8_192L);
        values.put(PEItems.LOW_COVALENCE_DUST, 1L);
        values.put(PEItems.DARK_MATTER, 139_264L);
        values.put(PEBlocks.TRANSMUTATION_TABLE, 260L);
        return values;
    }

    @GameTest(template = TEMPLATE)
    public static void vanillaAndProjectEValuesMatchUpstream(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<ItemLike, Long> entry : expected().entrySet()) {
            long actual = ProjectEAPI.getEMCProxy().getValue(entry.getKey().asItem());
            if (actual != entry.getValue()) {
                wrong.add(entry.getKey().asItem() + "=" + actual + " (expected " + entry.getValue() + ")");
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("EMC values differ from upstream: " + String.join(", ", wrong));
        }
        helper.succeed();
    }

    /** Items that must not be worth anything: tools of the trade with container remainders and creative-only blocks. */
    @GameTest(template = TEMPLATE)
    public static void unmappableItemsHaveNoValue(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (ItemLike item : List.of(Items.BEDROCK, Items.COMMAND_BLOCK, Items.BARRIER, Items.SPAWNER)) {
            long actual = ProjectEAPI.getEMCProxy().getValue(item.asItem());
            if (actual != 0) {
                wrong.add(item.asItem() + "=" + actual);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("Items with EMC that should have none: " + String.join(", ", wrong));
        }
        helper.succeed();
    }
}
