/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import mekanism.api.NBTConstants;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registration.impl.ItemRegistryObject;
import mekanism.tools.common.MekanismTools;
import mekanism.tools.common.item.ItemMekanismArmor;
import mekanism.tools.common.item.ItemMekanismPaxel;
import mekanism.tools.common.registries.ToolsItems;
import mekanism.tools.common.registries.ToolsRecipeSerializers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;

/**
 * Mekanism Tools registers its items and recipe serializers, crafts them, and paxels strip and flatten without tilling.
 */
public class ToolsModuleGameTests {

    public static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void toolsModuleRegistersItemsAndSerializers(GameTestHelper helper) {
        int count = 0;
        for (Field field : ToolsItems.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !ItemRegistryObject.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            ItemRegistryObject<?> registered;
            try {
                registered = (ItemRegistryObject<?>) field.get(null);
            } catch (IllegalAccessException e) {
                throw new GameTestAssertException("Could not read " + field.getName() + ": " + e.getMessage());
            }
            ResourceLocation id = Registry.ITEM.getKey(registered.asItem());
            check(MekanismTools.MODID.equals(id.getNamespace()), field.getName() + " is not a mekanismtools item: " + id);
            count++;
        }
        check(count == 72, "Tools registered " + count + " items");
        ResourceLocation paxel = new ResourceLocation(MekanismTools.MODID, "paxel");
        ResourceLocation banner = new ResourceLocation(MekanismTools.MODID, "banner_shield");
        check(Registry.RECIPE_SERIALIZER.get(paxel) == ToolsRecipeSerializers.PAXEL.get(), "paxel serializer is not registered");
        check(Registry.RECIPE_SERIALIZER.get(banner) == ToolsRecipeSerializers.BANNER_SHIELD.get(), "banner shield serializer is not registered");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsCraftBronzePickaxeAndChestplate(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        ItemStack ingot = new ItemStack(MekanismItems.BRONZE_INGOT.get());
        ItemStack stick = new ItemStack(Items.STICK);
        ItemStack empty = ItemStack.EMPTY;
        ItemStack pickaxe = craft(helper, player, new BlockPos(1, 1, 1),
              ingot.copy(), ingot.copy(), ingot.copy(),
              empty, stick.copy(), empty,
              empty, stick.copy(), empty);
        check(pickaxe.is(ToolsItems.BRONZE_PICKAXE.get()), "Bronze pickaxe recipe made " + name(pickaxe));
        ItemStack chestplate = craft(helper, player, new BlockPos(3, 1, 1),
              ingot.copy(), empty, ingot.copy(),
              ingot.copy(), ingot.copy(), ingot.copy(),
              ingot.copy(), ingot.copy(), ingot.copy());
        check(chestplate.is(ToolsItems.BRONZE_CHESTPLATE.get()), "Bronze chestplate recipe made " + name(chestplate));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsCraftBronzePaxelFromTools(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        ItemStack stick = new ItemStack(Items.STICK);
        ItemStack empty = ItemStack.EMPTY;
        ItemStack paxel = craft(helper, player, new BlockPos(1, 1, 1),
              new ItemStack(ToolsItems.BRONZE_AXE.get()), new ItemStack(ToolsItems.BRONZE_PICKAXE.get()), new ItemStack(ToolsItems.BRONZE_SHOVEL.get()),
              empty, stick, empty,
              empty, stick.copy(), empty);
        check(paxel.is(ToolsItems.BRONZE_PAXEL.get()), "Bronze paxel recipe made " + name(paxel));
        check(!paxel.isDamaged(), "Undamaged tools crafted a damaged paxel");
        check(paxel.getMaxDamage() == 750, "Bronze paxel durability is " + paxel.getMaxDamage());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsPaxelKeepsAverageDurability(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        ItemStack axe = new ItemStack(ToolsItems.BRONZE_AXE.get());
        ItemStack pickaxe = new ItemStack(ToolsItems.BRONZE_PICKAXE.get());
        ItemStack shovel = new ItemStack(ToolsItems.BRONZE_SHOVEL.get());
        axe.setDamageValue(axe.getMaxDamage() / 2);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() / 2);
        shovel.setDamageValue(shovel.getMaxDamage() / 2);
        ItemStack stick = new ItemStack(Items.STICK);
        ItemStack empty = ItemStack.EMPTY;
        ItemStack paxel = craft(helper, player, new BlockPos(1, 1, 1),
              axe, pickaxe, shovel,
              empty, stick, empty,
              empty, stick.copy(), empty);
        check(paxel.is(ToolsItems.BRONZE_PAXEL.get()), "Damaged tools did not craft a bronze paxel: " + name(paxel));
        int expected = averagedDamage(new ItemStack[]{axe, pickaxe, shovel}, paxel.getMaxDamage());
        check(paxel.getDamageValue() == expected, "Paxel damage is " + paxel.getDamageValue() + ", expected " + expected);
        check(Math.abs(expected - 375) <= 2, "Half-used bronze tools should land near half of 750, got " + expected);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsWoodPaxelHasDoubleDurability(GameTestHelper helper) {
        ItemStack wood = new ItemStack(ToolsItems.WOOD_PAXEL.get());
        check(wood.getMaxDamage() == 118, "Wood paxel durability is " + wood.getMaxDamage());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsBannerShieldCopiesBannerColor(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        ItemStack empty = ItemStack.EMPTY;
        ItemStack shield = craft(helper, player, new BlockPos(1, 1, 1),
              new ItemStack(Items.RED_BANNER), new ItemStack(ToolsItems.BRONZE_SHIELD.get()), empty,
              empty, empty, empty,
              empty, empty, empty);
        check(shield.is(ToolsItems.BRONZE_SHIELD.get()), "Banner shield recipe made " + name(shield));
        CompoundTag tag = shield.getTagElement(NBTConstants.BLOCK_ENTITY_TAG);
        check(tag != null && tag.getInt(NBTConstants.BASE) == DyeColor.RED.getId(), "Banner base is " + tag);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsPaxelStripsAndFlattens(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        BlockPos log = new BlockPos(1, 1, 1);
        helper.setBlock(log, Blocks.OAK_LOG);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        BlockState stickResult = helper.getBlockState(log).getToolModifiedState(context(helper, player, log, Direction.UP), ToolActions.AXE_STRIP, true);
        check(stickResult == null, "A stick stripped the log");
        ItemStack paxel = new ItemStack(ToolsItems.BRONZE_PAXEL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, paxel);
        BlockState stripped = helper.getBlockState(log).getToolModifiedState(context(helper, player, log, Direction.UP), ToolActions.AXE_STRIP, true);
        check(stripped != null && stripped.is(Blocks.STRIPPED_OAK_LOG), "Paxel strip result is " + stripped);
        InteractionResult used = paxel.useOn(context(helper, player, log, Direction.UP));
        check(used.consumesAction(), "Paxel did not strip: " + used);
        check(helper.getBlockState(log).is(Blocks.STRIPPED_OAK_LOG), "Log is " + helper.getBlockState(log));

        BlockPos grass = new BlockPos(3, 1, 1);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        helper.setBlock(grass.above(), Blocks.AIR);
        InteractionResult flattened = paxel.useOn(context(helper, player, grass, Direction.UP));
        check(flattened.consumesAction(), "Paxel did not flatten: " + flattened);
        check(helper.getBlockState(grass).is(Blocks.DIRT_PATH), "Grass is " + helper.getBlockState(grass));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsPaxelDoesNotTillAndHoeDoes(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1));
        ItemStack paxel = new ItemStack(ToolsItems.BRONZE_PAXEL.get());
        check(!paxel.canPerformAction(ToolActions.HOE_TILL), "Paxels till");
        BlockPos dirt = new BlockPos(1, 1, 1);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(dirt.above(), Blocks.AIR);
        player.setItemInHand(InteractionHand.MAIN_HAND, paxel);
        BlockState tilled = helper.getBlockState(dirt).getToolModifiedState(context(helper, player, dirt, Direction.UP), ToolActions.HOE_TILL, true);
        check(tilled == null, "Paxel till result is " + tilled);
        check(helper.getBlockState(dirt).is(Blocks.DIRT), "Asking to till changed the dirt");

        ItemStack hoe = new ItemStack(ToolsItems.BRONZE_HOE.get());
        check(hoe.canPerformAction(ToolActions.HOE_TILL), "Bronze hoe cannot till");
        player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
        InteractionResult used = hoe.useOn(context(helper, player, dirt, Direction.UP));
        check(used.consumesAction(), "Hoe did not till: " + used);
        check(helper.getBlockState(dirt).is(Blocks.FARMLAND), "Dirt is " + helper.getBlockState(dirt));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void toolsPaxelHarvestsByTier(GameTestHelper helper) {
        ItemMekanismPaxel bronze = ToolsItems.BRONZE_PAXEL.get();
        ItemMekanismPaxel steel = ToolsItems.STEEL_PAXEL.get();
        check(bronze.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()), "Bronze paxel cannot mine iron ore");
        check(!bronze.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Bronze paxel mines obsidian");
        check(steel.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Steel paxel cannot mine obsidian");
        float speed = bronze.getDestroySpeed(new ItemStack(bronze), Blocks.STONE.defaultBlockState());
        check(speed > 1, "Bronze paxel destroy speed on stone is " + speed);
        ItemMekanismArmor chestplate = ToolsItems.BRONZE_CHESTPLATE.get();
        check(chestplate.getDefense() == 7, "Bronze chestplate defense is " + chestplate.getDefense());
        check(chestplate.getSlot() == EquipmentSlot.CHEST, "Bronze chestplate slot is " + chestplate.getSlot());
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, ServerPlayer player, BlockPos table, ItemStack... grid) {
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        for (int i = 0; i < grid.length; i++) {
            menu.getSlot(i + 1).set(grid[i]);
        }
        return menu.getSlot(0).getItem().copy();
    }

    private static int averagedDamage(ItemStack[] inputs, int outputMax) {
        int totalDurability = 0;
        int totalMax = 0;
        for (ItemStack stack : inputs) {
            int max = stack.getMaxDamage();
            totalDurability += max - stack.getDamageValue();
            totalMax += max;
        }
        int target = (int) (outputMax * (totalDurability / (double) totalMax));
        return outputMax - target;
    }

    private static UseOnContext context(GameTestHelper helper, ServerPlayer player, BlockPos relative, Direction face) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), face, absolute, false);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "tools-test"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }

    private static String name(ItemStack stack) {
        return stack.isEmpty() ? "nothing" : stack.getItem().getName(stack).getString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
