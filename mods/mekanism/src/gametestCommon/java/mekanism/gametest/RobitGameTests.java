/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;
import static mekanism.gametest.CoreMachineGameTests.item;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.common.entity.EntityRobit;
import mekanism.common.item.ItemRobit;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismContainerTypes;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Robit is placed from its item onto an idle chargepad. The item's energy and name move onto the entity. With
 * pickup enabled it collects a dropped item into its inventory. Standing on a powered chargepad fills its energy,
 * and its smelting slots run an energized-smelter recipe.
 */
public class RobitGameTests {

    private static final BlockPos PAD = new BlockPos(2, 1, 2);

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void robitPlacedFromItemKeepsEnergyAndName(GameTestHelper helper) {
        FloatingLong stored = FloatingLong.createConst(12_345);
        EntityRobit robit = place(helper, "Scout", stored);
        helper.succeedWhen(() -> {
            check(robit.isAlive(), "The placed Robit was removed");
            check("Scout".equals(robit.getName().getString()), "Name is " + robit.getName().getString());
            check(robit.getEnergyContainer().getEnergy().equals(stored), "Energy is " + robit.getEnergyContainer().getEnergy());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void robitPicksUpDroppedItem(GameTestHelper helper) {
        EntityRobit robit = place(helper, "Porter", FloatingLong.createConst(10_000));
        robit.setDropPickup(true);
        ServerLevel level = helper.getLevel();
        ItemEntity dropped = new ItemEntity(level, robit.getX(), robit.getY() + 0.2, robit.getZ(), new ItemStack(Items.DIAMOND));
        dropped.setPickUpDelay(0);
        dropped.setNoGravity(true);
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);
        helper.succeedWhen(() -> {
            ItemStack slot = robit.getInventorySlots(null).get(0).getStack();
            if (!slot.is(Items.DIAMOND)) {
                throw new GameTestAssertException("Inventory holds " + slot);
            }
            check(!dropped.isAlive(), "The dropped diamond is still in the world");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void robitRechargesOnChargepad(GameTestHelper helper) {
        EntityRobit robit = place(helper, "Charger", FloatingLong.createConst(100));
        TileEntityMekanism pad = (TileEntityMekanism) helper.getBlockEntity(PAD);
        long accepted = TestPlatform.insertEnergy(pad, Direction.DOWN, 10_000);
        check(accepted > 0, "The chargepad accepted no energy through its bottom");
        FloatingLong before = robit.getEnergyContainer().getEnergy().copy();
        helper.succeedWhen(() -> {
            FloatingLong energy = robit.getEnergyContainer().getEnergy();
            if (!energy.greaterThan(before)) {
                throw new GameTestAssertException("Robit energy stayed at " + energy);
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void robitSmeltsItem(GameTestHelper helper) {
        EntityRobit robit = place(helper, "Smelter", FloatingLong.createConst(50_000));
        List<IInventorySlot> slots = robit.getContainerInventorySlots(MekanismContainerTypes.SMELTING_ROBIT.get());
        check(slots.size() == 2, "Smelting container has " + slots.size() + " slots");
        slots.get(0).setStack(new ItemStack(item("dust_iron")));
        helper.succeedWhen(() -> {
            ItemStack output = slots.get(1).getStack();
            if (!output.is(Items.IRON_INGOT) || output.getCount() < 1) {
                throw new GameTestAssertException("Expected an iron ingot, found " + output);
            }
        });
    }

    private static EntityRobit place(GameTestHelper helper, String name, FloatingLong energy) {
        helper.setBlock(PAD, MekanismBlocks.CHARGEPAD.getBlock());
        ServerPlayer player = player(helper);
        ItemStack stack = new ItemStack(MekanismItems.ROBIT.get());
        IEnergyContainer container = StorageUtils.getEnergyContainer(stack, 0);
        check(container != null, "The Robit item has no energy container");
        container.setEnergy(energy);
        ((ItemRobit) stack.getItem()).setName(stack, Component.literal(name));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = MekanismItems.ROBIT.get().useOn(context(helper, player, PAD));
        check(result.consumesAction(), "Placing the Robit returned " + result);
        check(player.getMainHandItem().isEmpty(), "The Robit item was not consumed");
        EntityRobit robit = find(helper);
        check(robit != null, "No Robit was placed");
        return robit;
    }

    private static EntityRobit find(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        AABB box = new AABB(origin, helper.absolutePos(new BlockPos(8, 8, 8)));
        List<EntityRobit> found = helper.getLevel().getEntitiesOfClass(EntityRobit.class, box);
        return found.isEmpty() ? null : found.get(0);
    }

    private static UseOnContext context(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "robit-test"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }
}
