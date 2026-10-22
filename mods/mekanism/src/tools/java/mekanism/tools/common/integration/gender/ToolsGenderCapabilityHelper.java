/*
 * Mekanism 1.19.2 port - Tools chestplate settings for Wildfire's Gender Mod.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.tools.common.integration.gender;

import mekanism.common.Mekanism;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.integration.gender.GenderArmorSettings;
import mekanism.common.integration.gender.GenderCapabilityHelper;
import mekanism.tools.common.item.ItemMekanismArmor;
import mekanism.tools.common.registries.ToolsItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

/**
 * Upstream's tool-chestplate values. The adapter is the core mod's, loaded by name, so this class does not reference
 * the Gender Mod.
 */
public final class ToolsGenderCapabilityHelper {

    private ToolsGenderCapabilityHelper() {
    }

    public static void addGenderCapability(ItemMekanismArmor item, ItemCapabilityWrapper wrapper) {
        if (Mekanism.hooks.WildfireGenderModLoaded && item.getSlot() == EquipmentSlot.CHEST) {
            GenderArmorSettings settings = chestSettings(item);
            if (settings != null) {
                GenderCapabilityHelper.attach(item, settings, wrapper::add);
            }
        }
    }

    /**
     * Fabric registers armor with the Gender Mod at startup. Forge also attaches the capability from
     * {@code initCapabilities}; calling this there creates one unused instance and loads the adapter.
     */
    public static void register() {
        register(ToolsItems.BRONZE_CHESTPLATE.asItem());
        register(ToolsItems.LAPIS_LAZULI_CHESTPLATE.asItem());
        register(ToolsItems.OSMIUM_CHESTPLATE.asItem());
        register(ToolsItems.REFINED_GLOWSTONE_CHESTPLATE.asItem());
        register(ToolsItems.REFINED_OBSIDIAN_CHESTPLATE.asItem());
        register(ToolsItems.STEEL_CHESTPLATE.asItem());
    }

    public static GenderArmorSettings chestSettings(Item item) {
        if (item == ToolsItems.BRONZE_CHESTPLATE.asItem()) {
            return GenderArmorSettings.BRONZE;
        } else if (item == ToolsItems.LAPIS_LAZULI_CHESTPLATE.asItem()) {
            return GenderArmorSettings.LAPIS_LAZULI;
        } else if (item == ToolsItems.OSMIUM_CHESTPLATE.asItem()) {
            return GenderArmorSettings.OSMIUM;
        } else if (item == ToolsItems.REFINED_GLOWSTONE_CHESTPLATE.asItem()) {
            return GenderArmorSettings.REFINED_GLOWSTONE;
        } else if (item == ToolsItems.REFINED_OBSIDIAN_CHESTPLATE.asItem()) {
            return GenderArmorSettings.REFINED_OBSIDIAN;
        } else if (item == ToolsItems.STEEL_CHESTPLATE.asItem()) {
            return GenderArmorSettings.STEEL;
        }
        return null;
    }

    private static void register(Item item) {
        if (item instanceof ArmorItem armor) {
            GenderArmorSettings settings = chestSettings(armor);
            if (settings != null) {
                GenderCapabilityHelper.attach(armor, settings, ignored -> {
                });
            }
        }
    }
}
