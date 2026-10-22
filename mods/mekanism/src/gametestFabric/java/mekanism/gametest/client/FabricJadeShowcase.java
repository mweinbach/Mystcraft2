/*
 * Mekanism 1.19.2 port - development-only Fabric client for the Jade screenshot.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.machine.TileEntityElectricPump;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import ports.mekanism.lib.fluids.FluidStack;

/**
 * Opens one creative world, looks at a powered electric pump, and writes the frame with Jade's tooltip visible.
 * F3, chat and tutorial toasts stay off. Registered only on the Jade showcase client. It is not in the production JAR.
 */
public final class FabricJadeShowcase implements ClientModInitializer {

    private static final Path SHOT = Path.of("/cursor/stores/bc-50246035-7515-4ce7-9afe-6c26b6fee95c/media/mekanism/i3-jade.png");
    private static final BlockPos MACHINE = new BlockPos(0, 81, 0);

    private boolean started;
    private boolean placed;
    private int seen;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("mekanism.jade.screenshot")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft minecraft) {
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        minecraft.options.renderDebug = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        if (minecraft.player == null) {
            if (!started && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                prepare(minecraft);
                RegistryAccess.Writable access = RegistryAccess.builtinCopy();
                WorldGenSettings settings = WorldPresets.createNormalWorldFromPreset(access);
                LevelSettings level = new LevelSettings("i3", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("i3", level, access, settings);
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
        if (!minecraft.level.getBlockState(MACHINE).is(MekanismBlocks.ELECTRIC_PUMP.getBlock())) {
            return;
        }
        seen++;
        // Jade asks the server for the tank data after the crosshair settles on the pump.
        if (seen < 120) {
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
        minecraft.options.hideGui = false;
        minecraft.options.renderDebug = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        minecraft.options.gamma().set(1.0D);
        minecraft.options.narrator().set(NarratorStatus.OFF);
        minecraft.options.bobView().set(false);
        minecraft.options.save();
    }

    private static void layOut(ServerLevel level) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 5; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 81; y <= 86; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState state = MekanismBlocks.ELECTRIC_PUMP.getBlock().defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        }
        level.setBlockAndUpdate(MACHINE, state);
        MekanismBlocks.ELECTRIC_PUMP.getBlock().setPlacedBy(level, MACHINE, state, null, ItemStack.EMPTY);
        TileEntityElectricPump pump = (TileEntityElectricPump) level.getBlockEntity(MACHINE);
        ((IStrictEnergyHandler) pump).insertEnergy(FloatingLong.createConst(40_000), Action.EXECUTE);
        pump.fluidTank.setStack(new FluidStack(Fluids.WATER, 6_000));
        level.setDayTime(6000);
        level.setWeatherParameters(0, 0, false, false);
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.teleportTo(level, 0.5D, 81.0D, 2.8D, 180.0F, 16.0F);
    }
}
