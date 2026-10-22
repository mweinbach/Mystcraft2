/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.List;
import mekanism.common.Mekanism;
import mekanism.common.item.gear.GearArmorPieces;
import mekanism.common.item.gear.ItemSpecialArmor;
import mekanism.common.registration.impl.ItemRegistryObject;
import mekanism.common.registries.MekanismItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Every worn gear piece equips into its slot. Custom models hide the vanilla armor mesh with the null texture.
 * Hazmat keeps the vanilla mesh and the namespaced layer texture. Rendering those models is the client armor
 * renderer; this test checks the items and the files that renderer uses.
 */
public class ArmorRenderingGameTests {

    private static final String TEMPLATE = "mekanism:empty";

    private static final String[] MODEL_FILES = {
          "assets/mekanism/models/entity/mekasuit.obj",
          "assets/mekanism/models/entity/mekasuit.mtl",
          "assets/mekanism/models/entity/mekasuit_modules.obj",
          "assets/mekanism/models/entity/mekasuit_modules.mtl",
          "assets/mekanism/shaders/core/rendertype_mekasuit.json",
          "assets/mekanism/shaders/core/rendertype_mekasuit.vsh",
          "assets/mekanism/shaders/core/rendertype_mekasuit.fsh",
          "assets/mekanism/textures/entity/armor/mekasuit_player.png",
          "assets/mekanism/textures/entity/armor/mekasuit_armor_body.png",
          "assets/mekanism/textures/entity/armor/mekasuit_armor_helmet.png",
          "assets/mekanism/textures/entity/armor/mekasuit_armor_exoskeleton.png",
          "assets/mekanism/textures/entity/armor/mekasuit_armor_modules.png",
          "assets/mekanism/render/null_armor.png",
          "assets/mekanism/render/jetpack.png",
          "assets/mekanism/render/free_runners.png",
          "assets/mekanism/render/scuba_set.png",
          "assets/mekanism/textures/models/armor/hazmat_layer_1.png",
          "assets/mekanism/textures/models/armor/hazmat_layer_2.png",
          "assets/mekanism/textures/entity/hdpe_elytra.png"
    };

    @GameTest(template = TEMPLATE)
    public static void gearArmorEquipsInItsSlot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        List<ItemRegistryObject<? extends ArmorItem>> custom = GearArmorPieces.customModels();
        List<ItemRegistryObject<? extends ArmorItem>> hazmat = GearArmorPieces.hazmat();
        check(custom.size() == 10, "Expected 10 custom armor pieces, found " + custom.size());
        check(hazmat.size() == 4, "Expected 4 hazmat pieces, found " + hazmat.size());

        checkSlot(MekanismItems.MEKASUIT_HELMET, EquipmentSlot.HEAD);
        checkSlot(MekanismItems.MEKASUIT_BODYARMOR, EquipmentSlot.CHEST);
        checkSlot(MekanismItems.MEKASUIT_PANTS, EquipmentSlot.LEGS);
        checkSlot(MekanismItems.MEKASUIT_BOOTS, EquipmentSlot.FEET);
        checkSlot(MekanismItems.JETPACK, EquipmentSlot.CHEST);
        checkSlot(MekanismItems.ARMORED_JETPACK, EquipmentSlot.CHEST);
        checkSlot(MekanismItems.SCUBA_TANK, EquipmentSlot.CHEST);
        checkSlot(MekanismItems.SCUBA_MASK, EquipmentSlot.HEAD);
        checkSlot(MekanismItems.FREE_RUNNERS, EquipmentSlot.FEET);
        checkSlot(MekanismItems.ARMORED_FREE_RUNNERS, EquipmentSlot.FEET);
        checkSlot(MekanismItems.HAZMAT_MASK, EquipmentSlot.HEAD);
        checkSlot(MekanismItems.HAZMAT_GOWN, EquipmentSlot.CHEST);
        checkSlot(MekanismItems.HAZMAT_PANTS, EquipmentSlot.LEGS);
        checkSlot(MekanismItems.HAZMAT_BOOTS, EquipmentSlot.FEET);

        for (ItemRegistryObject<? extends ArmorItem> piece : custom) {
            equip(player, piece.asItem(), true);
        }
        for (ItemRegistryObject<? extends ArmorItem> piece : hazmat) {
            equip(player, piece.asItem(), false);
        }

        wear(player, MekanismItems.MEKASUIT_HELMET.asItem());
        wear(player, MekanismItems.MEKASUIT_BODYARMOR.asItem());
        wear(player, MekanismItems.MEKASUIT_PANTS.asItem());
        wear(player, MekanismItems.MEKASUIT_BOOTS.asItem());
        check(player.getItemBySlot(EquipmentSlot.HEAD).is(MekanismItems.MEKASUIT_HELMET.get()), "The helmet did not stay on with the suit");
        check(player.getItemBySlot(EquipmentSlot.CHEST).is(MekanismItems.MEKASUIT_BODYARMOR.get()), "The bodyarmor did not stay on with the suit");
        check(player.getItemBySlot(EquipmentSlot.LEGS).is(MekanismItems.MEKASUIT_PANTS.get()), "The pants did not stay on with the suit");
        check(player.getItemBySlot(EquipmentSlot.FEET).is(MekanismItems.MEKASUIT_BOOTS.get()), "The boots did not stay on with the suit");

        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        wear(player, MekanismItems.JETPACK.asItem());
        wear(player, MekanismItems.SCUBA_MASK.asItem());
        check(player.getItemBySlot(EquipmentSlot.CHEST).is(MekanismItems.JETPACK.get()), "The jetpack did not stay on with the mask");
        check(player.getItemBySlot(EquipmentSlot.HEAD).is(MekanismItems.SCUBA_MASK.get()), "The scuba mask did not stay on with the jetpack");

        ItemStack elytra = new ItemStack(MekanismItems.HDPE_REINFORCED_ELYTRA.get());
        check(elytra.getEquipmentSlot() == EquipmentSlot.CHEST, "The HDPE elytra slot was " + elytra.getEquipmentSlot());
        check(Mob.getEquipmentSlotForItem(elytra) == EquipmentSlot.CHEST, "The HDPE elytra did not equip as chest");
        check(elytra.canEquip(EquipmentSlot.CHEST, player), "The HDPE elytra refused the chest slot");
        player.setItemSlot(EquipmentSlot.CHEST, elytra);
        check(player.getItemBySlot(EquipmentSlot.CHEST).is(MekanismItems.HDPE_REINFORCED_ELYTRA.get()), "The HDPE elytra did not stay equipped");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorModelFilesArePresent(GameTestHelper helper) {
        for (String path : MODEL_FILES) {
            if (ArmorRenderingGameTests.class.getClassLoader().getResource(path) == null) {
                throw new GameTestAssertException("Missing armor resource " + path);
            }
        }
        String hazmatLegs = GearArmorPieces.vanillaArmorTexture(MekanismItems.HAZMAT_PANTS.asItem().getMaterial(), EquipmentSlot.LEGS);
        String hazmatChest = GearArmorPieces.vanillaArmorTexture(MekanismItems.HAZMAT_GOWN.asItem().getMaterial(), EquipmentSlot.CHEST);
        check(hazmatLegs.equals(Mekanism.MODID + ":textures/models/armor/hazmat_layer_2.png"), hazmatLegs);
        check(hazmatChest.equals(Mekanism.MODID + ":textures/models/armor/hazmat_layer_1.png"), hazmatChest);
        check(GearArmorPieces.nullArmorTexture().equals(Mekanism.MODID + ":render/null_armor.png"), GearArmorPieces.nullArmorTexture());
        helper.succeed();
    }

    private static void checkSlot(ItemRegistryObject<? extends ArmorItem> piece, EquipmentSlot slot) {
        check(piece.asItem().getSlot() == slot, piece.get().getDescriptionId() + " is in " + piece.asItem().getSlot());
    }

    private static void equip(Player player, ArmorItem item, boolean customModel) {
        EquipmentSlot slot = item.getSlot();
        ItemStack stack = new ItemStack(item);
        check(Mob.getEquipmentSlotForItem(stack) == slot, item + " equipped into " + Mob.getEquipmentSlotForItem(stack));
        check(stack.canEquip(slot, player), item + " refused " + slot);
        player.setItemSlot(slot, stack);
        check(player.getItemBySlot(slot).is(item), item + " did not stay in " + slot);
        if (customModel) {
            check(item instanceof ItemSpecialArmor, item + " is not special armor");
            String texture = item.getArmorTexture(stack, player, slot, null);
            check(GearArmorPieces.nullArmorTexture().equals(texture), item + " vanilla texture was " + texture);
        } else {
            check(!(item instanceof ItemSpecialArmor), item + " should use the vanilla armor mesh");
            String texture = GearArmorPieces.vanillaArmorTexture(item.getMaterial(), slot);
            String path = "assets/" + texture.replace(':', '/');
            if (ArmorRenderingGameTests.class.getClassLoader().getResource(path) == null) {
                throw new GameTestAssertException("Missing hazmat texture " + texture);
            }
        }
        player.setItemSlot(slot, ItemStack.EMPTY);
    }

    private static void wear(Player player, ArmorItem item) {
        ItemStack stack = new ItemStack(item);
        check(stack.canEquip(item.getSlot(), player), item + " refused to be worn with the rest of the set");
        player.setItemSlot(item.getSlot(), stack);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
