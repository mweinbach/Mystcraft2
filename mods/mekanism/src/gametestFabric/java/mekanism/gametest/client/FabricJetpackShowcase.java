/*
 * Mekanism 1.19.2 port - development-only Fabric client for a jetpack screenshot.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.gear.ItemJetpack;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.util.ChemicalUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Opens one survival world, equips a hydrogen jetpack, and writes a close third-person frame of the player flying.
 * The HUD, pause menu, chat and toasts stay off. Registered only on the jetpack screenshot client.
 */
public final class FabricJetpackShowcase implements ClientModInitializer {

    private static final Path SHOT = Path.of("/cursor/stores/bc-50246035-7515-4ce7-9afe-6c26b6fee95c/media/mekanism/f8c-jetpack-flight.png");
    private static final BlockPos PAD = new BlockPos(0, 4, 0);
    private static final int PAD_RADIUS = 12;

    private boolean started;
    private boolean layoutScheduled;
    private boolean layoutDone;
    private int padTicks;
    private int flying;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("mekanism.jetpack.screenshot")) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft minecraft) {
        minecraft.getToasts().clear();
        minecraft.gui.getChat().clearMessages(false);
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.player == null) {
            if (!started && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                prepare(minecraft);
                RegistryAccess.Writable access = RegistryAccess.builtinCopy();
                Registry<WorldPreset> presets = access.registryOrThrow(Registry.WORLD_PRESET_REGISTRY);
                WorldGenSettings settings = presets.getHolderOrThrow(WorldPresets.FLAT).value().createWorldGenSettings(0L, true, false);
                LevelSettings level = new LevelSettings("f8c", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("f8c", level, access, settings);
            }
            return;
        }
        // The title screen has to stay up until world creation starts. After the player joins,
        // close the progress screen or pause menu so the flight frame can be taken.
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
        if (minecraft.getSingleplayerServer() == null || minecraft.level == null) {
            return;
        }
        if (!layoutDone) {
            if (!layoutScheduled) {
                layoutScheduled = true;
                minecraft.getSingleplayerServer().execute(() -> {
                    layout(minecraft.getSingleplayerServer().overworld());
                    layoutDone = true;
                });
            }
            return;
        }
        if (!overPad(minecraft)) {
            padTicks = 0;
            minecraft.getSingleplayerServer().execute(() -> snap(minecraft.getSingleplayerServer().overworld()));
            return;
        }
        padTicks++;
        if (padTicks < 20) {
            return;
        }
        minecraft.player.yBodyRot = minecraft.player.getYRot();
        minecraft.player.yBodyRotO = minecraft.player.yBodyRot;
        minecraft.options.keyJump.setDown(true);
        ItemStack chest = minecraft.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean rising = chest.getItem() instanceof ItemJetpack && !minecraft.player.isOnGround() && minecraft.player.getDeltaMovement().y > 0.05D;
        if (!rising) {
            flying = 0;
            return;
        }
        flying++;
        if (flying < 40) {
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

    private static boolean overPad(Minecraft minecraft) {
        int x = Mth.floor(minecraft.player.getX());
        int z = Mth.floor(minecraft.player.getZ());
        if (Math.abs(x - PAD.getX()) > PAD_RADIUS || Math.abs(z - PAD.getZ()) > PAD_RADIUS) {
            return false;
        }
        if (!minecraft.level.getBlockState(PAD).is(Blocks.SMOOTH_STONE)) {
            return false;
        }
        return minecraft.level.getBlockState(new BlockPos(x, PAD.getY(), z)).is(Blocks.SMOOTH_STONE);
    }

    private static void prepare(Minecraft minecraft) {
        minecraft.options.hideGui = true;
        minecraft.options.renderDebug = false;
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        minecraft.options.gamma().set(1.0D);
        minecraft.options.narrator().set(NarratorStatus.OFF);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.options.save();
    }

    private static void layout(ServerLevel level) {
        level.getChunk(PAD);
        BlockPos floorMin = PAD.offset(-PAD_RADIUS, 0, -PAD_RADIUS);
        BlockPos floorMax = PAD.offset(PAD_RADIUS, 0, PAD_RADIUS);
        for (int x = floorMin.getX(); x <= floorMax.getX(); x++) {
            for (int z = floorMin.getZ(); z <= floorMax.getZ(); z++) {
                level.setBlockAndUpdate(new BlockPos(x, PAD.getY(), z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 1; y <= 16; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, PAD.getY() + y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setDefaultSpawnPos(PAD.above(), 0.0F);
        level.setDayTime(6000);
        level.setWeatherParameters(0, 0, false, false);
        snap(level);
    }

    private static void snap(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack jetpack = ChemicalUtil.getFilledVariant(new ItemStack(MekanismItems.JETPACK.get()), MekanismConfig.gear.jetpackMaxGas, MekanismGases.HYDROGEN);
        player.setItemSlot(EquipmentSlot.CHEST, jetpack);
        player.teleportTo(level, PAD.getX() + 0.5D, PAD.getY() + 1.0D, PAD.getZ() + 0.5D, 180.0F, 14.0F);
        player.setYHeadRot(180.0F);
        player.yBodyRot = 180.0F;
        player.yBodyRotO = 180.0F;
        player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
    }
}
