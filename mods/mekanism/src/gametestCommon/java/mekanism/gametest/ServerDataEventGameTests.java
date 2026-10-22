/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.NBTConstants;
import mekanism.common.CommonWorldTickHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.MekanismRecipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Chunk data, recipe reload and a cancelled entity join reach the same Mekanism listeners Forge calls.
 */
public class ServerDataEventGameTests {

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void chunkDataRoundTripsThroughSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        LevelChunk chunk = level.getChunkAt(absolute);
        int originalVersion = MekanismConfig.world.userGenVersion.get();
        boolean originalRegen = MekanismConfig.world.enableRegeneration.get();
        int current = originalVersion == Integer.MAX_VALUE ? 1 : originalVersion + 1;
        AtomicReference<CompoundTag> captured = new AtomicReference<>();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ChunkDataEvent.Save.class, event -> {
            if (event.getChunk().getPos().equals(chunk.getPos()) && event.getLevel() == level) {
                captured.set(event.getData());
            }
        });
        MekanismConfig.world.userGenVersion.set(current);
        MekanismConfig.world.enableRegeneration.set(true);
        try {
            CompoundTag saved = save(level, chunk, captured);
            check(saved.getInt(NBTConstants.WORLD_GEN_VERSION) == current,
                  "Save wrote world gen version " + saved.getInt(NBTConstants.WORLD_GEN_VERSION) + " instead of " + current);

            CompoundTag older = saved.copy();
            older.putInt(NBTConstants.WORLD_GEN_VERSION, 0);
            ChunkSerializer.read(level, level.getPoiManager(), chunk.getPos(), older);
            CompoundTag remembered = save(level, chunk, captured);
            check(remembered.getInt(NBTConstants.WORLD_GEN_VERSION) == 0,
                  "Load of an older chunk did not keep its world gen version");
        } catch (RuntimeException e) {
            MekanismConfig.world.userGenVersion.set(originalVersion);
            MekanismConfig.world.enableRegeneration.set(originalRegen);
            throw e;
        }
        helper.runAtTickTime(5, () -> {
            try {
                CompoundTag regenerated = save(level, chunk, captured);
                check(regenerated.getInt(NBTConstants.WORLD_GEN_VERSION) == current,
                      "Retrogen left the chunk at world gen version " + regenerated.getInt(NBTConstants.WORLD_GEN_VERSION));
                helper.succeed();
            } finally {
                MekanismConfig.world.userGenVersion.set(originalVersion);
                MekanismConfig.world.enableRegeneration.set(originalRegen);
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void reloadRefreshesRecipeCache(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MekanismRecipeType<?, ?> smelting = MekanismRecipeType.SMELTING.getRecipeType();
        List<?> filled = smelting.getRecipes(level);
        check(!filled.isEmpty(), "The smelting recipe cache was empty");
        List<?> sentinel = List.copyOf(filled);
        try {
            Field cache = MekanismRecipeType.class.getDeclaredField("cachedRecipes");
            cache.setAccessible(true);
            cache.set(smelting, sentinel);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not replace the recipe cache: " + e.getMessage());
        }
        CommonWorldTickHandler.flushTagAndRecipeCaches = false;
        AtomicBoolean refreshed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, AddReloadListenerEvent.class, event -> event.addListener(
              (ResourceManagerReloadListener) manager -> refreshed.set(cacheCleared(smelting, sentinel)
                    && CommonWorldTickHandler.flushTagAndRecipeCaches)));
        level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds()).whenComplete((done, error) -> {
            finished.set(true);
            failure.set(error);
        });
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw new IllegalStateException("Reload failed: " + failure.get().getMessage(), failure.get());
            }
            if (!finished.get()) {
                throw new GameTestAssertException("Reload has not finished");
            }
            check(refreshed.get(), "Reload finished without refreshing Mekanism's recipe cache");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cancelledJoinKeepsTheEntityOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        Pig pig = EntityType.PIG.create(level);
        check(pig != null, "Could not create a pig");
        pig.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        AtomicBoolean sawFresh = new AtomicBoolean();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, event -> {
            if (event.getEntity() == pig) {
                sawFresh.set(true);
                check(!event.loadedFromDisk(), "A freshly added pig was marked loaded from disk");
                event.setCanceled(true);
            }
        });
        check(!level.addFreshEntity(pig), "Cancelling entity join still added the pig");
        check(sawFresh.get(), "Adding a pig did not fire entity join");
        check(level.getEntity(pig.getId()) == null, "The cancelled pig is in the level");

        Pig allowed = EntityType.PIG.create(level);
        check(allowed != null, "Could not create the second pig");
        allowed.moveTo(pos.getX() + 1, pos.getY(), pos.getZ(), 0, 0);
        check(level.addFreshEntity(allowed), "Entities stopped joining after a cancelled join");
        check(level.getEntity(allowed.getId()) == allowed, "The allowed pig is not in the level");

        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "cancelled-join"), null);
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.moveTo(pos.getX(), pos.getY(), pos.getZ() + 1, 0, 0);
        AtomicInteger playerJoins = new AtomicInteger();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, event -> {
            if (event.getEntity() == player) {
                playerJoins.incrementAndGet();
                check(!event.loadedFromDisk(), "A player join was marked loaded from disk");
                event.setCanceled(true);
            }
        });
        addPlayer(level, player);
        check(playerJoins.get() == 1, "Player join fired " + playerJoins.get() + " times");
        check(level.getPlayerByUUID(player.getUUID()) == null, "The cancelled player is in the level");

        ServerPlayer allowedPlayer = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "allowed-join"), null);
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), allowedPlayer);
        allowedPlayer.moveTo(pos.getX() + 1, pos.getY(), pos.getZ() + 1, 0, 0);
        AtomicInteger allowedJoins = new AtomicInteger();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, event -> {
            if (event.getEntity() == allowedPlayer) {
                allowedJoins.incrementAndGet();
            }
        });
        addPlayer(level, allowedPlayer);
        check(allowedJoins.get() == 1, "An added player joined " + allowedJoins.get() + " times");
        check(level.getPlayerByUUID(allowedPlayer.getUUID()) == allowedPlayer, "The player was not added");
        allowedPlayer.discard();
        helper.succeed();
    }

    private static CompoundTag save(ServerLevel level, ChunkAccess chunk, AtomicReference<CompoundTag> captured) {
        captured.set(null);
        chunk.setUnsaved(true);
        try {
            Method method = ChunkMap.class.getDeclaredMethod("save", ChunkAccess.class);
            method.setAccessible(true);
            method.invoke(level.getChunkSource().chunkMap, chunk);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new GameTestAssertException("Could not save the chunk: " + cause.getMessage());
        }
        CompoundTag data = captured.get();
        check(data != null, "Saving " + chunk.getPos() + " did not fire chunk data save");
        return data;
    }

    private static void addPlayer(ServerLevel level, ServerPlayer player) {
        try {
            Method method = ServerLevel.class.getDeclaredMethod("addPlayer", ServerPlayer.class);
            method.setAccessible(true);
            method.invoke(level, player);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new GameTestAssertException("Could not add the player: " + cause.getMessage());
        }
    }

    private static boolean cacheCleared(MekanismRecipeType<?, ?> type, List<?> sentinel) {
        try {
            Field cache = MekanismRecipeType.class.getDeclaredField("cachedRecipes");
            cache.setAccessible(true);
            return cache.get(type) != sentinel;
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not read the recipe cache: " + e.getMessage());
        }
    }
}
