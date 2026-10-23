/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import ports.metalrender.client.MetalRenderClient;
import ports.metalrender.client.MetalTerrainRenderer;
import ports.metalrender.config.MetalRenderConfig;
import ports.metalrender.config.MetalRenderConfig.Tier;
import ports.metalrender.config.MetalRenderConfig.Upscaler;
import ports.metalrender.nativebridge.HardwareCapabilities;
import ports.metalrender.nativebridge.MetalNative;

/**
 * Development-only in-game check, enabled with {@code -Dmetalrender.autotest=true} (the
 * {@code runAutotestClient} task). It creates a fixed-seed creative world, builds a small scene with
 * solid, cutout and translucent blocks, then screenshots the same view with vanilla OpenGL terrain and
 * with every Metal tier and MetalFX mode this Mac supports. Each Metal frame must come from the Metal
 * path (telemetry shows drawn quads) and match the vanilla frame within a PSNR bound. Translucent
 * terrain is always drawn by vanilla, so it is part of every frame but not of the Metal telemetry. Results go to
 * {@code autotest/report.txt} in the run directory. Never part of the production JAR.
 */
public final class MetalRenderAutotest implements ClientModInitializer {

    private static final long SEED = 1192L;
    private static final double MIN_PSNR_NATIVE = 24.0;
    private static final double MIN_PSNR_UPSCALED = 20.0;
    private static final int SETTLE_TICKS = 60;
    private static final int TIMEOUT_TICKS = 20 * 120;

    private record Variant(String name, boolean enabled, Tier tier, Upscaler upscaler, float scale,
                           Predicate<HardwareCapabilities> supported) {
    }

    private static final List<Variant> VARIANTS = List.of(
            new Variant("vanilla", false, Tier.AUTO, Upscaler.NONE, 1.0f, caps -> true),
            new Variant("metal_auto", true, Tier.AUTO, Upscaler.NONE, 1.0f, HardwareCapabilities::metal),
            new Variant("metal4", true, Tier.METAL4, Upscaler.NONE, 1.0f, HardwareCapabilities::metal4),
            new Variant("metal3_mesh", true, Tier.METAL3_MESH, Upscaler.NONE, 1.0f,
                    HardwareCapabilities::meshShaders),
            new Variant("compute_icb", true, Tier.COMPUTE_ICB, Upscaler.NONE, 1.0f, HardwareCapabilities::metal),
            new Variant("metalfx_spatial_50", true, Tier.AUTO, Upscaler.METALFX_SPATIAL, 0.5f,
                    HardwareCapabilities::metalFxSpatial),
            new Variant("metalfx_temporal_50", true, Tier.AUTO, Upscaler.METALFX_TEMPORAL, 0.5f,
                    HardwareCapabilities::metalFxTemporal));

    /** AUTO takes the mesh path where supported; Compute+ICB is the per-section culling path. */
    private static final List<Tier> CULL_TIERS = List.of(Tier.AUTO, Tier.COMPUTE_ICB);

    private final Path outDir = Path.of("autotest");
    private final List<String> report = new ArrayList<>();
    private final Map<String, NativeImage> shots = new LinkedHashMap<>();
    private boolean started;
    private boolean sceneBuilt;
    private int ticks;
    private int variant = -1;
    private int stableTicks;
    private int waitedTicks;
    private int turnTicks;
    private int cullIndex;
    private int cullStage;
    private boolean failed;
    private boolean done;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("metalrender.autotest")) {
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        }
    }

    private void tick(final Minecraft minecraft) {
        if (done) {
            return;
        }
        minecraft.getToasts().clear();
        if (minecraft.player == null) {
            if (!started && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                prepareOptions(minecraft);
                final RegistryAccess.Writable access = RegistryAccess.builtinCopy();
                final WorldGenSettings settings = WorldPresets.createNormalWorldFromPreset(access, SEED);
                final LevelSettings level = new LevelSettings("metalrender-autotest", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("metalrender-autotest", level, access, settings);
            }
            return;
        }
        if (minecraft.screen != null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (!sceneBuilt) {
            sceneBuilt = true;
            minecraft.getSingleplayerServer().execute(() -> buildScene(minecraft.getSingleplayerServer().overworld()));
            return;
        }
        if (++ticks > 20 * 600) {
            fail("autotest did not finish within 10 minutes");
            finish(minecraft);
            return;
        }
        if (variant < 0) {
            if (ticks < 40) {
                return; // let the teleport and scene changes reach the client
            }
            report.add("capabilities: " + MetalRenderClient.capabilities().summary());
            nextVariant(minecraft);
            return;
        }
        if (variant >= VARIANTS.size()) {
            cullingPhase(minecraft);
            return;
        }
        waitedTicks++;
        if (!settled(minecraft)) {
            if (waitedTicks > TIMEOUT_TICKS) {
                fail(VARIANTS.get(variant).name() + ": chunks did not settle");
                finish(minecraft);
            }
            return;
        }
        capture(minecraft, VARIANTS.get(variant));
        nextVariant(minecraft);
    }

    /**
     * Vanilla only compiles sections inside the view, so straight after a rebuild every resident
     * section is visible. For each culling path, rebuild, then turn around: the sections left behind
     * the camera must be rejected on the GPU. The mesh path culls per meshlet, Compute+ICB per section.
     */
    private void cullingPhase(final Minecraft minecraft) {
        if (cullIndex >= CULL_TIERS.size()) {
            finish(minecraft);
            return;
        }
        final Tier tier = CULL_TIERS.get(cullIndex);
        if (cullStage == 0) {
            final MetalRenderConfig config = MetalRenderClient.config();
            final boolean tierChanged = config.tier != tier;
            config.enabled = true;
            config.tier = tier;
            config.upscaler = Upscaler.NONE;
            config.resolutionScale = 1.0f;
            MetalRenderClient.applyConfig(tierChanged);
            minecraft.levelRenderer.allChanged();
            stableTicks = 0;
            waitedTicks = 0;
            cullStage = 1;
        } else if (cullStage == 1) {
            if (settled(minecraft)) {
                minecraft.player.setYRot(minecraft.player.getYRot() + 180.0f);
                minecraft.player.setXRot(0.0f);
                turnTicks = 0;
                cullStage = 2;
            } else if (++waitedTicks > TIMEOUT_TICKS) {
                fail("culling_" + tier.id() + ": chunks did not settle");
                finish(minecraft);
            }
        } else if (++turnTicks >= 40) {
            checkCulling(tier);
            cullIndex++;
            cullStage = 0;
        }
    }

    private void checkCulling(final Tier requested) {
        final String name = "culling_" + requested.id();
        final long ctx = MetalRenderClient.context();
        if (!MetalRenderClient.isActive() || ctx == 0) {
            fail(name + ": Metal inactive (" + MetalRenderClient.status() + ")");
            return;
        }
        final int[] counters = new int[8];
        MetalNative.readTelemetry(ctx, counters, new float[2]);
        final int activeTier = MetalNative.getActiveTier(ctx);
        final boolean meshPath = activeTier != MetalNative.TIER_COMPUTE_ICB && MetalRenderClient.config().meshShaders;
        final int meshlets = MetalRenderClient.store() == null ? 0 : MetalRenderClient.store().usedMeshlets();
        final boolean ok = meshPath ? counters[7] > 0 && counters[7] < meshlets : counters[0] > 0 && counters[1] > 0;
        if (!ok) {
            failed = true;
        }
        report.add(String.format("%s: %s | tier %s, after turning around: %s", name, ok ? "ok" : "FAIL",
                MetalNative.tierName(activeTier), meshPath
                        ? String.format("%d of %d meshlets drawn", counters[7], meshlets)
                        : String.format("%d sections visible, %d frustum-culled", counters[0], counters[1])));
    }

    private boolean settled(final Minecraft minecraft) {
        final ChunkRenderDispatcher dispatcher = minecraft.levelRenderer.getChunkRenderDispatcher();
        final boolean idle = dispatcher != null && dispatcher.getToBatchCount() == 0 && dispatcher.getToUpload() == 0
                && dispatcher.isQueueEmpty() && minecraft.levelRenderer.countRenderedChunks() > 0;
        stableTicks = idle ? stableTicks + 1 : 0;
        return stableTicks >= SETTLE_TICKS;
    }

    private void nextVariant(final Minecraft minecraft) {
        final HardwareCapabilities caps = MetalRenderClient.capabilities();
        do {
            variant++;
            if (variant >= VARIANTS.size()) {
                return; // the culling phase runs next
            }
            if (!VARIANTS.get(variant).supported().test(caps)) {
                report.add(VARIANTS.get(variant).name() + ": SKIPPED (not supported on this device)");
            }
        } while (!VARIANTS.get(variant).supported().test(caps));
        final Variant next = VARIANTS.get(variant);
        final MetalRenderConfig config = MetalRenderClient.config();
        final boolean tierChanged = config.tier != next.tier();
        config.enabled = next.enabled();
        config.tier = next.tier();
        config.upscaler = next.upscaler();
        config.resolutionScale = next.scale();
        config.meshShaders = true;
        config.debugInfo = true;
        MetalRenderClient.applyConfig(tierChanged);
        // Always rebuild so every variant starts from freshly uploaded geometry.
        minecraft.levelRenderer.allChanged();
        stableTicks = 0;
        waitedTicks = 0;
    }

    private void capture(final Minecraft minecraft, final Variant current) {
        final NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
        try {
            Files.createDirectories(outDir);
            image.writeToFile(outDir.resolve(current.name() + ".png"));
        } catch (final IOException e) {
            fail(current.name() + ": could not write the screenshot: " + e);
        }
        shots.put(current.name(), image);

        final StringBuilder line = new StringBuilder(current.name()).append(": ");
        if (!current.enabled()) {
            line.append(MetalRenderClient.isActive() ? "FAIL (Metal still active)" : "ok (vanilla OpenGL terrain)");
            if (MetalRenderClient.isActive()) {
                failed = true;
            }
            report.add(line.toString());
            return;
        }
        final long ctx = MetalRenderClient.context();
        if (!MetalRenderClient.isActive() || ctx == 0) {
            fail(current.name() + ": Metal inactive (" + MetalRenderClient.status() + ")");
            return;
        }
        final int[] counters = new int[8];
        final float[] gpuMs = new float[2];
        MetalNative.readTelemetry(ctx, counters, gpuMs);
        final String tier = MetalNative.tierName(MetalNative.getActiveTier(ctx));
        final String upscale = MetalNative.upscaleName(MetalNative.getLastUpscaleMode(ctx));
        final NativeImage vanilla = shots.get("vanilla");
        final double psnr = vanilla == null ? Double.NaN : psnr(vanilla, image);
        final double bound = current.upscaler() == Upscaler.NONE ? MIN_PSNR_NATIVE : MIN_PSNR_UPSCALED;
        final List<String> problems = new ArrayList<>();
        if (counters[0] <= 0 || counters[4] <= 0) {
            problems.add("no visible sections or solid quads in telemetry");
        }
        if (current.upscaler() != Upscaler.NONE && MetalNative.getLastUpscaleMode(ctx) != current.upscaler().nativeId) {
            problems.add("requested " + current.upscaler().id() + " but ran " + upscale);
        }
        if (!(psnr >= bound)) {
            problems.add(String.format("PSNR %.2f dB below %.1f dB", psnr, bound));
        }
        line.append(problems.isEmpty() ? "ok" : "FAIL " + problems)
                .append(String.format(" | tier %s, upscale %s, PSNR vs vanilla %.2f dB, %d px off by >24",
                        tier, upscale, psnr, vanilla == null ? -1 : pixelsOff(vanilla, image, 24)))
                .append(String.format(" | sections visible %d, frustum-culled %d", counters[0], counters[1]))
                .append(String.format(" | quads solid %d, cutout %d, meshlets %d", counters[4], counters[5], counters[7]))
                .append(String.format(" | GPU %.2f ms opaque", gpuMs[0]));
        report.add(line.toString());
        for (final String debug : MetalTerrainRenderer.debugLines()) {
            if (!debug.isEmpty()) {
                report.add("    F3 " + debug);
            }
        }
        if (!problems.isEmpty()) {
            failed = true;
        }
    }

    /** Peak signal-to-noise ratio over RGB. Infinite for identical images. */
    static double psnr(final NativeImage a, final NativeImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return 0.0;
        }
        double sum = 0.0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                final int pa = a.getPixelRGBA(x, y);
                final int pb = b.getPixelRGBA(x, y);
                for (int shift = 0; shift < 24; shift += 8) {
                    final int d = ((pa >> shift) & 0xFF) - ((pb >> shift) & 0xFF);
                    sum += d * d;
                }
            }
        }
        final double mse = sum / (3.0 * a.getWidth() * a.getHeight());
        return mse == 0.0 ? Double.POSITIVE_INFINITY : 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    /** Pixels where any RGB channel differs by more than {@code threshold}. */
    static int pixelsOff(final NativeImage a, final NativeImage b, final int threshold) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return a.getWidth() * a.getHeight();
        }
        int off = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                final int pa = a.getPixelRGBA(x, y);
                final int pb = b.getPixelRGBA(x, y);
                for (int shift = 0; shift < 24; shift += 8) {
                    if (Math.abs(((pa >> shift) & 0xFF) - ((pb >> shift) & 0xFF)) > threshold) {
                        off++;
                        break;
                    }
                }
            }
        }
        return off;
    }

    private void fail(final String message) {
        failed = true;
        report.add("FAIL: " + message);
    }

    private void finish(final Minecraft minecraft) {
        done = true;
        report.add(failed ? "RESULT: FAILED" : "RESULT: PASSED");
        try {
            Files.createDirectories(outDir);
            Files.write(outDir.resolve("report.txt"), report, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            ports.metalrender.MetalRenderMod.LOGGER.error("MetalRender autotest could not write its report", e);
        }
        for (final String line : report) {
            ports.metalrender.MetalRenderMod.LOGGER.info("[autotest] {}", line);
        }
        shots.values().forEach(NativeImage::close);
        shots.clear();
        minecraft.stop();
    }

    private static void prepareOptions(final Minecraft minecraft) {
        minecraft.options.hideGui = true;
        minecraft.options.renderDebug = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        minecraft.options.narrator().set(NarratorStatus.OFF);
        minecraft.options.renderDistance().set(8);
        minecraft.options.simulationDistance().set(8);
        minecraft.options.cloudStatus().set(CloudStatus.OFF);
        minecraft.options.particles().set(ParticleStatus.MINIMAL);
        minecraft.options.bobView().set(false);
        minecraft.options.fov().set(70);
        minecraft.options.gamma().set(0.5D);
        minecraft.options.save();
    }

    /** A floating stone deck with solid, cutout (leaves, flowers, glass) and translucent (water, stained glass) blocks. */
    private static void buildScene(final ServerLevel level) {
        final GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);

        final BlockPos spawn = level.getSharedSpawnPos();
        final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        final BlockPos o = new BlockPos(spawn.getX(), Math.max(ground + 12, 90), spawn.getZ());
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                level.setBlockAndUpdate(o.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
                for (int y = 1; y <= 8; y++) {
                    level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        // water basin (translucent fluid) with a stone rim
        for (int x = -4; x <= -1; x++) {
            for (int z = -5; z <= -2; z++) {
                final boolean rim = x == -4 || x == -1 || z == -5 || z == -2;
                level.setBlockAndUpdate(o.offset(x, 1, z),
                        rim ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
            }
        }
        // stained glass wall (translucent) and clear glass (cutout)
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlockAndUpdate(o.offset(x, y, -4), (x % 2 == 0 ? Blocks.BLUE_STAINED_GLASS
                        : Blocks.ORANGE_STAINED_GLASS).defaultBlockState());
            }
            level.setBlockAndUpdate(o.offset(x, 1, -6), Blocks.GLASS.defaultBlockState());
        }
        // a small tree (cutout-mipped leaves) and flowers (cutout)
        for (int y = 1; y <= 4; y++) {
            level.setBlockAndUpdate(o.offset(-5, y, 3), Blocks.OAK_LOG.defaultBlockState());
        }
        final BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = -7; x <= -3; x++) {
            for (int z = 1; z <= 5; z++) {
                for (int y = 4; y <= 6; y++) {
                    if (!(x == -5 && z == 3 && y == 4)) {
                        level.setBlockAndUpdate(o.offset(x, y, z), leaves);
                    }
                }
            }
        }
        for (int x = -1; x <= 3; x++) {
            level.setBlockAndUpdate(o.offset(x, 0, 3), Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(o.offset(x, 1, 3),
                    (x % 2 == 0 ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState());
        }
        level.setBlockAndUpdate(o.offset(4, 1, 1), Blocks.GLOWSTONE.defaultBlockState());
        level.setBlockAndUpdate(o.offset(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());

        final ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.teleportTo(level, o.getX() + 0.5, o.getY() + 1.0, o.getZ() + 7.3, 180.0f, 20.0f);
    }
}
