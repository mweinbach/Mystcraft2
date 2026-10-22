/*
 * Mekanism 1.19.2 port - shared gear list for both loaders.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.item.gear;

import java.util.List;
import mekanism.common.Mekanism;
import mekanism.common.registration.impl.ItemRegistryObject;
import mekanism.common.registries.MekanismItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * Armor the worn-gear renderers cover. Forge draws the custom-model pieces from {@code MekanismArmorLayer}.
 * Fabric registers an {@code ArmorRenderer} for every piece in both lists. The HDPE elytra is not armor; the
 * elytra layer draws it.
 */
public final class GearArmorPieces {

    private GearArmorPieces() {
    }

    public static List<ItemRegistryObject<? extends ArmorItem>> customModels() {
        return List.of(
              MekanismItems.MEKASUIT_HELMET,
              MekanismItems.MEKASUIT_BODYARMOR,
              MekanismItems.MEKASUIT_PANTS,
              MekanismItems.MEKASUIT_BOOTS,
              MekanismItems.JETPACK,
              MekanismItems.ARMORED_JETPACK,
              MekanismItems.SCUBA_TANK,
              MekanismItems.SCUBA_MASK,
              MekanismItems.FREE_RUNNERS,
              MekanismItems.ARMORED_FREE_RUNNERS
        );
    }

    public static List<ItemRegistryObject<? extends ArmorItem>> hazmat() {
        return List.of(
              MekanismItems.HAZMAT_MASK,
              MekanismItems.HAZMAT_GOWN,
              MekanismItems.HAZMAT_PANTS,
              MekanismItems.HAZMAT_BOOTS
        );
    }

    /**
     * The texture Forge's armor material name and Fabric's namespaced armor lookup both resolve. Legs use layer 2.
     */
    public static String vanillaArmorTexture(ArmorMaterial material, EquipmentSlot slot) {
        String name = material.getName();
        int separator = name.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : name.substring(0, separator);
        String path = separator < 0 ? name : name.substring(separator + 1);
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return namespace + ":textures/models/armor/" + path + "_layer_" + layer + ".png";
    }

    public static String nullArmorTexture() {
        return Mekanism.MODID + ":render/null_armor.png";
    }
}
