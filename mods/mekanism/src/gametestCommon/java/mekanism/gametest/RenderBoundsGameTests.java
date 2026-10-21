/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import mekanism.common.Mekanism;
import mekanism.common.entity.EntityRobit;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Render bounds are what the client frustum tests, and a following Robit asks for a chunk ticket from
 * {@code onRemovedFromWorld} when tracking ends.
 */
public class RenderBoundsGameTests {

    @GameTest(template = TEMPLATE)
    public static void renderBoundingBoxesFollowTheRenderer(GameTestHelper helper) {
        assertSpecialCase(helper, new BlockPos(0, 1, 1), Blocks.ENCHANTING_TABLE.defaultBlockState(), 0, 0, 0, 1, 1, 1);
        assertSpecialCase(helper, new BlockPos(2, 1, 1), Blocks.CHEST.defaultBlockState(), -1, 0, -1, 2, 2, 2);
        assertInfinite(helper, new BlockPos(4, 1, 1), Blocks.BEACON.defaultBlockState());

        assertCollisionBox(helper, new BlockPos(6, 1, 1), MekanismBlocks.ENRICHMENT_CHAMBER.getBlock().defaultBlockState());
        assertSpecialCase(helper, new BlockPos(1, 1, 3), MekanismBlocks.SEISMIC_VIBRATOR.getBlock().defaultBlockState(), 0, 0, 0, 1, 2, 1);
        assertSpecialCase(helper, new BlockPos(3, 1, 3), MekanismBlocks.PIGMENT_MIXER.getBlock().defaultBlockState(), 0, 1, 0, 1, 2, 1);
        assertSpecialCase(helper, new BlockPos(5, 1, 3), MekanismBlocks.BASIC_UNIVERSAL_CABLE.getBlock().defaultBlockState(), 0, 0, 0, 1, 1, 1);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void followingRobitHoldsItsChunkWhenRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = new ServerPlayer(level.getServer(), level, Mekanism.gameProfile, null);
        // 1.19.2 resolves an owner by scanning the level's player list, which placeNewPlayer would have filled.
        level.players().add(owner);
        try {
            EntityRobit following = robit(helper, new BlockPos(1, 2, 1));
            following.setFollowing(true);
            check(following.getOwner() != null, "The following Robit has no owner for the unload ticket");
            following.discard();
            check(hasUnloadTicket(level, following), "Removing a following Robit did not request its chunk ticket");

            EntityRobit staying = robit(helper, new BlockPos(3, 2, 1));
            staying.setFollowing(false);
            staying.discard();
            check(!hasUnloadTicket(level, staying), "A Robit that is not following requested an unload ticket");
        } finally {
            level.players().remove(owner);
        }
        helper.succeed();
    }

    private static EntityRobit robit(GameTestHelper helper, BlockPos pos) {
        Entity spawned = helper.spawnWithNoFreeWill(MekanismEntityTypes.ROBIT.get(), pos);
        check(spawned instanceof EntityRobit, "Expected a Robit, found " + spawned);
        return (EntityRobit) spawned;
    }

    private static void assertSpecialCase(GameTestHelper helper, BlockPos pos, BlockState state, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        helper.setBlock(pos, state);
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        BlockPos world = blockEntity.getBlockPos();
        AABB expected = new AABB(world.offset(minX, minY, minZ), world.offset(maxX, maxY, maxZ));
        AABB actual = TestPlatform.renderBounds(blockEntity);
        check(actual.equals(expected), state.getBlock() + " render box " + actual + " != " + expected);
    }

    private static void assertInfinite(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state);
        AABB actual = TestPlatform.renderBounds(helper.getBlockEntity(pos));
        check(actual.minX == Double.NEGATIVE_INFINITY && actual.minY == Double.NEGATIVE_INFINITY && actual.minZ == Double.NEGATIVE_INFINITY
              && actual.maxX == Double.POSITIVE_INFINITY && actual.maxY == Double.POSITIVE_INFINITY && actual.maxZ == Double.POSITIVE_INFINITY,
              state.getBlock() + " should not be frustum culled, box " + actual);
    }

    private static void assertCollisionBox(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state);
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        BlockPos world = blockEntity.getBlockPos();
        VoxelShape shape = state.getCollisionShape(blockEntity.getLevel(), world);
        AABB actual = TestPlatform.renderBounds(blockEntity);
        check(!shape.isEmpty() && actual.equals(shape.bounds().move(world)),
              state.getBlock() + " render box " + actual + " != collision " + shape.bounds());
    }

    private static boolean hasUnloadTicket(ServerLevel level, Entity entity) {
        ChunkPos chunk = new ChunkPos(entity.blockPosition());
        SortedArraySet<Ticket<?>> tickets = tickets(distanceManager(level.getChunkSource()), chunk.toLong());
        if (tickets == null) {
            return false;
        }
        for (Ticket<?> ticket : tickets) {
            if ("robit_chunk_unload".equals(String.valueOf(ticket.getType())) && Objects.equals(ticketKey(ticket), entity.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code DistanceManager.getTickets} is private. The ticket set is the only {@code SortedArraySet} returned for a
     * chunk position, on both the official and SRG names.
     */
    @SuppressWarnings("unchecked")
    private static SortedArraySet<Ticket<?>> tickets(DistanceManager manager, long chunk) {
        for (Method method : DistanceManager.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != long.class
                || method.getReturnType() != SortedArraySet.class) {
                continue;
            }
            method.setAccessible(true);
            try {
                return (SortedArraySet<Ticket<?>>) method.invoke(manager, chunk);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Distance manager has no ticket lookup");
    }

    private static DistanceManager distanceManager(ServerChunkCache chunks) {
        for (Field field : ServerChunkCache.class.getDeclaredFields()) {
            if (field.getType() != DistanceManager.class) {
                continue;
            }
            field.setAccessible(true);
            try {
                return (DistanceManager) field.get(chunks);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Chunk cache has no distance manager");
    }

    private static Object ticketKey(Ticket<?> ticket) {
        for (Field field : Ticket.class.getDeclaredFields()) {
            if (field.getType() != Object.class) {
                continue;
            }
            field.setAccessible(true);
            try {
                return field.get(ticket);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Ticket has no key");
    }
}
