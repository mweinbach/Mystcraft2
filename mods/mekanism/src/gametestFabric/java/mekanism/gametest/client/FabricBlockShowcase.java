/*
 * Mekanism 1.19.2 port - development-only Fabric client for a block screenshot.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mekanism.common.registries.MekanismBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Opens one creative world, places the remaining blocks in a row, and writes a close frame with the HUD hidden.
 * Registered only on the showcase client. It is not in the production JAR.
 */
public final class FabricBlockShowcase implements ClientModInitializer {

    private static final Path SHOT = Path.of("/cursor/stores/bc-50246035-7515-4ce7-9afe-6c26b6fee95c/media/mekanism/f10-blocks.png");
    private static final BlockPos ORIGIN = new BlockPos(0, 80, 0);
    private static final Block[] ROW = {
          MekanismBlocks.DIMENSIONAL_STABILIZER.getBlock(),
          MekanismBlocks.SEISMIC_VIBRATOR.getBlock(),
          MekanismBlocks.INDUSTRIAL_ALARM.getBlock(),
          MekanismBlocks.FUELWOOD_HEATER.getBlock(),
          MekanismBlocks.RESISTIVE_HEATER.getBlock(),
          MekanismBlocks.CHARGEPAD.getBlock(),
          MekanismBlocks.ELECTRIC_PUMP.getBlock(),
          MekanismBlocks.FLUIDIC_PLENISHER.getBlock(),
          MekanismBlocks.FORMULAIC_ASSEMBLICATOR.getBlock(),
          MekanismBlocks.MODIFICATION_STATION.getBlock(),
          MekanismBlocks.PERSONAL_CHEST.getBlock(),
          MekanismBlocks.PERSONAL_BARREL.getBlock()
    };

    private boolean started;
    private boolean placed;
    private int seen;

    @Override
    public void onInitializeClient() {
        // The Jade and fission screenshot clients share this source set. This row stays off for those runs.
        if (Boolean.getBoolean("mekanism.jade.screenshot") || Boolean.getBoolean("mekanism.fission.screenshot")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft minecraft) {
        minecraft.getToasts().clear();
        if (minecraft.player == null) {
            if (!started && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                prepare(minecraft);
                RegistryAccess.Writable access = RegistryAccess.builtinCopy();
                WorldGenSettings settings = WorldPresets.createNormalWorldFromPreset(access);
                LevelSettings level = new LevelSettings("f10", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("f10", level, access, settings);
            }
            return;
        }
        if (minecraft.screen != null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (!placed) {
            placed = true;
            minecraft.getSingleplayerServer().execute(() -> layOut(minecraft.getSingleplayerServer().overworld()));
            return;
        }
        BlockPos sample = ORIGIN.offset(2, 1, 0);
        if (!minecraft.level.getBlockState(sample).is(MekanismBlocks.INDUSTRIAL_ALARM.getBlock())) {
            return;
        }
        seen++;
        if (seen < 80) {
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
        BlockPos floorMin = ORIGIN.offset(-2, 0, -3);
        BlockPos floorMax = ORIGIN.offset(ROW.length + 1, 0, 6);
        for (int x = floorMin.getX(); x <= floorMax.getX(); x++) {
            for (int z = floorMin.getZ(); z <= floorMax.getZ(); z++) {
                level.setBlockAndUpdate(new BlockPos(x, floorMin.getY(), z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 1; y <= 6; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, floorMin.getY() + y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int index = 0; index < ROW.length; index++) {
            BlockPos pos = ORIGIN.offset(index, 1, 0);
            Block block = ROW[index];
            BlockState state = facing(block);
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
            if (block == MekanismBlocks.INDUSTRIAL_ALARM.getBlock()) {
                level.setBlockAndUpdate(pos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        }
        level.setDayTime(6000);
        level.setWeatherParameters(0, 0, false, false);
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        double x = ORIGIN.getX() + ROW.length / 2.0D - 0.5D;
        player.teleportTo(level, x, ORIGIN.getY() + 2.2D, ORIGIN.getZ() + 5.5D, 180.0F, 18.0F);
    }

    private static BlockState facing(Block block) {
        BlockState state = block.defaultBlockState();
        Direction facing = block == MekanismBlocks.INDUSTRIAL_ALARM.getBlock() || block == MekanismBlocks.PERSONAL_BARREL.getBlock()
              ? Direction.UP : Direction.SOUTH;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, facing);
        }
        return state;
    }
}
