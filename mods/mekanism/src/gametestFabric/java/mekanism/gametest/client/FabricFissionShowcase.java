/*
 * Mekanism 1.19.2 port - development-only Fabric client for a fission reactor screenshot.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mekanism.gametest.FissionReactorGameTests;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Opens one creative world, builds a fueled fission reactor and writes a close frame through the reactor glass.
 * Registered only on the fission screenshot client. It is not in the production JAR.
 */
public final class FabricFissionShowcase implements ClientModInitializer {

    private static final Path SHOT = Path.of("/cursor/stores/bc-50246035-7515-4ce7-9afe-6c26b6fee95c/media/mekanism/fission-running.png");
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final int SIZE_X = 5;
    private static final int SIZE_Y = 6;
    private static final int SIZE_Z = 5;

    private boolean started;
    private boolean built;
    private int seen;
    private int lived;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("mekanism.fission.screenshot")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft minecraft) {
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        minecraft.options.hideGui = true;
        minecraft.options.renderDebug = false;
        if (minecraft.player == null) {
            if (!started && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                prepare(minecraft);
                RegistryAccess.Writable access = RegistryAccess.builtinCopy();
                WorldGenSettings settings = WorldPresets.createNormalWorldFromPreset(access);
                LevelSettings level = new LevelSettings("fission", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("fission", level, access, settings);
            }
            return;
        }
        if (minecraft.getSingleplayerServer() == null) {
            return;
        }
        lived++;
        if (lived > 900) {
            TileEntityFissionReactorCasing master = master(minecraft.level, ORIGIN);
            String detail = "none";
            if (master != null) {
                FissionReactorMultiblockData data = master.getMultiblock();
                detail = "formed=" + data.isFormed() + " burning=" + data.isBurning() + " fuel=" + data.fuelTank.getStored()
                      + " water=" + data.fluidCoolantTank.getFluidAmount();
            }
            throw new IllegalStateException("Fission screenshot did not finish. screen=" + minecraft.screen + " " + detail);
        }
        if (!built) {
            if (minecraft.screen != null) {
                return;
            }
            built = true;
            minecraft.getSingleplayerServer().execute(() -> layOut(minecraft.getSingleplayerServer().overworld()));
            return;
        }
        minecraft.getSingleplayerServer().execute(() -> fuelIfReady(minecraft.getSingleplayerServer().overworld()));
        TileEntityFissionReactorCasing client = master(minecraft.level, ORIGIN);
        if (client == null) {
            return;
        }
        FissionReactorMultiblockData data = client.getMultiblock();
        if (!data.isFormed() || !data.isBurning() || data.fluidCoolantTank.isEmpty() || data.fuelTank.isEmpty()) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }
        seen++;
        if (seen < 40) {
            return;
        }
        try {
            Files.createDirectories(SHOT.getParent());
            Screenshot.takeScreenshot(minecraft.getMainRenderTarget()).writeToFile(SHOT.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + SHOT, e);
        }
        minecraft.stop();
    }

    private static void prepare(Minecraft minecraft) {
        minecraft.options.hideGui = true;
        minecraft.options.renderDebug = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        minecraft.options.gamma().set(1.0D);
        minecraft.options.narrator().set(NarratorStatus.OFF);
        minecraft.options.save();
    }

    private static void layOut(ServerLevel level) {
        level.getChunkAt(ORIGIN);
        BlockPos floorMin = ORIGIN.offset(-3, -1, -3);
        BlockPos floorMax = ORIGIN.offset(SIZE_X + 3, 0, SIZE_Z + 8);
        for (int x = floorMin.getX(); x <= floorMax.getX(); x++) {
            for (int z = floorMin.getZ(); z <= floorMax.getZ(); z++) {
                level.setBlockAndUpdate(new BlockPos(x, ORIGIN.getY() - 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 0; y <= SIZE_Y + 4; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, ORIGIN.getY() + y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(ORIGIN.offset(2, 1, 2), GeneratorsBlocks.FISSION_FUEL_ASSEMBLY.getBlock().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(2, 2, 2), GeneratorsBlocks.FISSION_FUEL_ASSEMBLY.getBlock().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(2, 3, 2), GeneratorsBlocks.FISSION_FUEL_ASSEMBLY.getBlock().defaultBlockState());
        level.setBlockAndUpdate(ORIGIN.offset(2, 4, 2), GeneratorsBlocks.CONTROL_ROD_ASSEMBLY.getBlock().defaultBlockState());
        Block casing = GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock();
        Block glass = GeneratorsBlocks.REACTOR_GLASS.getBlock();
        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    int faces = 0;
                    if (x == 0 || x == SIZE_X - 1) {
                        faces++;
                    }
                    if (y == 0 || y == SIZE_Y - 1) {
                        faces++;
                    }
                    if (z == 0 || z == SIZE_Z - 1) {
                        faces++;
                    }
                    if (faces == 0) {
                        continue;
                    }
                    level.setBlockAndUpdate(ORIGIN.offset(x, y, z), (faces >= 2 ? casing : glass).defaultBlockState());
                }
            }
        }
        level.setDayTime(6000);
        level.setWeatherParameters(0, 0, false, false);
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.teleportTo(level, ORIGIN.getX() + 2.5D, ORIGIN.getY() + 1.4D, ORIGIN.getZ() + 6.6D, 180.0F, -6.0F);
    }

    private static void fuelIfReady(ServerLevel level) {
        TileEntityFissionReactorCasing master = master(level, ORIGIN);
        if (master == null) {
            return;
        }
        FissionReactorMultiblockData data = master.getMultiblock();
        if (!data.isFormed()) {
            return;
        }
        if (data.isActive() && data.isBurning() && !data.fuelTank.isEmpty() && !data.fluidCoolantTank.isEmpty()) {
            return;
        }
        FissionReactorGameTests.primeRunningReactor(data);
    }

    private static TileEntityFissionReactorCasing master(Level level, BlockPos origin) {
        if (level == null) {
            return null;
        }
        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    BlockEntity blockEntity = level.getBlockEntity(origin.offset(x, y, z));
                    if (blockEntity instanceof TileEntityFissionReactorCasing casing && casing.isMaster() && casing.getMultiblock().isFormed()) {
                        return casing;
                    }
                }
            }
        }
        return null;
    }
}
