/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.gametest;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.ParticleStatus;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import ports.metalrender.MetalRenderMod;
import ports.metalrender.client.MetalRenderClient;
import ports.metalrender.client.PerfStats;
import ports.metalrender.config.MetalRenderConfig;
import ports.metalrender.config.MetalRenderConfig.Tier;
import ports.metalrender.config.MetalRenderConfig.Upscaler;
import ports.metalrender.nativebridge.HardwareCapabilities;
import ports.metalrender.nativebridge.MetalNative;

/**
 * Development-only benchmark, enabled with {@code -Dmetalrender.benchmark=true} (the
 * {@code runBenchmarkClient} task). It hovers above a fixed-seed world with vsync and the frame cap
 * off and, for vanilla terrain and each Metal configuration: rebuilds every chunk and times the
 * rebuild, spins the camera once to compile everything around it, then spins once more while
 * recording per-frame CPU times. Results go to {@code benchmark/report.txt} in the run directory.
 */
public final class MetalRenderBenchmark implements ClientModInitializer {

    private static final long SEED = 1192L;
    private static final int DISTANCE = Integer.getInteger("metalrender.benchmark.distance", 16);
    private static final int LAP_FRAMES = Integer.getInteger("metalrender.benchmark.frames", 720);
    private static final int SKIP_FRAMES = 10;
    private static final int SETTLE_TICKS = 40;
    private static final int TIMEOUT_TICKS = 20 * 300;
    private static final float PITCH = 15.0f;
    /** Comma-separated variant names to run; empty runs them all. */
    private static final String ONLY = System.getProperty("metalrender.benchmark.variants", "");
    private static final int QUERIES = 4;

    private record Variant(String name, boolean enabled, Tier tier, Upscaler upscaler, float scale,
                           Predicate<HardwareCapabilities> supported) {
    }

    private static final List<Variant> VARIANTS = List.of(
            new Variant("vanilla", false, Tier.AUTO, Upscaler.NONE, 1.0f, caps -> true),
            new Variant("metal4", true, Tier.METAL4, Upscaler.NONE, 1.0f, HardwareCapabilities::metal4),
            new Variant("metal3_mesh", true, Tier.METAL3_MESH, Upscaler.NONE, 1.0f, HardwareCapabilities::meshShaders),
            new Variant("compute_icb", true, Tier.COMPUTE_ICB, Upscaler.NONE, 1.0f, HardwareCapabilities::metal),
            new Variant("metalfx_spatial_50", true, Tier.AUTO, Upscaler.METALFX_SPATIAL, 0.5f,
                    HardwareCapabilities::metalFxSpatial),
            new Variant("metalfx_temporal_50", true, Tier.AUTO, Upscaler.METALFX_TEMPORAL, 0.5f,
                    HardwareCapabilities::metalFxTemporal),
            new Variant("vanilla_again", false, Tier.AUTO, Upscaler.NONE, 1.0f, caps -> true));

    /** Cumulative time in {@code Window.updateDisplay} (buffer swap), from {@code WindowMixin}. */
    public static long swapNanos;

    private enum Stage { WORLD, LOAD, APPLY, REBUILD, WARMUP_LAP, SETTLE, MEASURE_LAP, DONE }

    /** Snapshot of the cumulative counters. */
    private record Counters(long time, long packNanos, long packedQuads, long uploadNanos, long uploads,
                            long opaqueNanos, long beginNanos, long waitNanos, long overlapNanos,
                            long opaqueFrames, long vanillaNanos,
                            long workerCpu, long swapNanos) {
        static Counters now() {
            return new Counters(System.nanoTime(), PerfStats.PACK_NANOS.sum(), PerfStats.PACKED_QUADS.sum(),
                    PerfStats.uploadNanos, PerfStats.uploads, PerfStats.opaqueNanos, PerfStats.beginNanos,
                    PerfStats.waitNanos, PerfStats.overlapNanos, PerfStats.opaqueFrames, PerfStats.vanillaTerrainNanos, workerCpuNanos(),
                    MetalRenderBenchmark.swapNanos);
        }
    }

    private final List<String> report = new ArrayList<>();
    private Stage stage = Stage.WORLD;
    private boolean started;
    private int variant = -1;
    private int stageTicks;
    private int stableTicks;
    private long idleSince;
    private Counters stageStart;

    // per-frame state (render thread)
    private boolean spinning;
    private int spinFrame;
    private int spinFrames;
    private float yawStart;
    private long frameStart;
    private long lastFrameStart;
    private long[] frameNanos = new long[0];
    private long[] levelNanos = new long[0];
    private double gpuMsSum;
    private int gpuSamples;
    private final int[] glQueries = new int[QUERIES];
    private int glQueryFrame;
    private double glGpuMsSum;
    private int glGpuSamples;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("metalrender.benchmark")) {
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
            WorldRenderEvents.START.register(context -> onFrameStart());
            WorldRenderEvents.END.register(context -> onFrameEnd());
        }
    }

    // ------------------------------------------------------------------ frames

    private void onFrameStart() {
        frameStart = System.nanoTime();
        if (glQueries[0] == 0) {
            GL15.glGenQueries(glQueries);
        }
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, glQueries[glQueryFrame % QUERIES]);
        if (spinning && spinFrame > 0 && spinFrame <= spinFrames) {
            frameNanos[spinFrame - 1] = frameStart - lastFrameStart;
        }
        lastFrameStart = frameStart;
    }

    private void onFrameEnd() {
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        glQueryFrame++;
        // Read the query issued QUERIES - 1 frames ago, if the GPU has finished it.
        final int old = glQueries[glQueryFrame % QUERIES];
        final boolean ready = glQueryFrame >= QUERIES
                && GL15.glGetQueryObjecti(old, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_TRUE;
        final long glNanos = ready ? GL33.glGetQueryObjecti64(old, GL15.GL_QUERY_RESULT) : -1L;
        if (!spinning) {
            return;
        }
        if (glNanos >= 0 && spinFrame > SKIP_FRAMES + QUERIES) {
            glGpuMsSum += glNanos / 1e6;
            glGpuSamples++;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (spinFrame > 0 && spinFrame <= spinFrames) {
            levelNanos[spinFrame - 1] = System.nanoTime() - frameStart;
            final long ctx = MetalRenderClient.context();
            if (MetalRenderClient.isActive() && ctx != 0) {
                final float[] gpu = new float[2];
                MetalNative.readTelemetry(ctx, new int[8], gpu);
                if (spinFrame > SKIP_FRAMES) {
                    gpuMsSum += gpu[0];
                    gpuSamples++;
                }
            }
        }
        if (spinFrame >= spinFrames) {
            spinning = false;
            return;
        }
        spinFrame++;
        setRotation(minecraft, yawStart + 360.0f * spinFrame / spinFrames);
    }

    private static void setRotation(final Minecraft minecraft, final float yaw) {
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.setYRot(yaw);
        minecraft.player.yRotO = yaw;
        minecraft.player.setXRot(PITCH);
        minecraft.player.xRotO = PITCH;
    }

    private void startSpin(final Minecraft minecraft, final int frames) {
        spinFrames = frames;
        spinFrame = 0;
        frameNanos = new long[frames];
        levelNanos = new long[frames];
        gpuMsSum = 0.0;
        gpuSamples = 0;
        glGpuMsSum = 0.0;
        glGpuSamples = 0;
        yawStart = minecraft.player.getYRot();
        spinning = true;
    }

    // ------------------------------------------------------------------ ticks

    private void tick(final Minecraft minecraft) {
        if (stage == Stage.DONE) {
            return;
        }
        minecraft.getToasts().clear();
        if (minecraft.player == null) {
            if (!started && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                prepareOptions(minecraft);
                final RegistryAccess.Writable access = RegistryAccess.builtinCopy();
                final WorldGenSettings settings = WorldPresets.createNormalWorldFromPreset(access, SEED);
                final LevelSettings level = new LevelSettings("metalrender-benchmark", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), DataPackConfig.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel("metalrender-benchmark", level, access, settings);
            }
            return;
        }
        if (minecraft.screen != null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        stageTicks++;
        switch (stage) {
            case WORLD -> {
                minecraft.getSingleplayerServer().execute(() -> placePlayer(minecraft.getSingleplayerServer().overworld()));
                report.add("capabilities: " + MetalRenderClient.capabilities().summary());
                report.add(String.format("setup: render distance %d, %dx%d framebuffer, %d-frame lap, %d CPUs",
                        DISTANCE, minecraft.getMainRenderTarget().width, minecraft.getMainRenderTarget().height,
                        LAP_FRAMES, Runtime.getRuntime().availableProcessors()));
                enter(Stage.LOAD);
            }
            case LOAD -> {
                // Wait for the server to send every chunk in range and vanilla to build them.
                if (stageTicks > 100 && settled(minecraft, 100)) {
                    nextVariant(minecraft);
                } else if (stageTicks > TIMEOUT_TICKS) {
                    fail(minecraft, "the world did not finish loading");
                }
            }
            case APPLY -> {
                // applyConfig may queue its own allChanged for the next tick; rebuild after that.
                if (stageTicks >= 3) {
                    setRotation(minecraft, 180.0f);
                    minecraft.levelRenderer.allChanged();
                    stageStart = Counters.now();
                    idleSince = 0L;
                    stableTicks = 0;
                    enter(Stage.REBUILD);
                }
            }
            case REBUILD -> {
                if (settled(minecraft, SETTLE_TICKS)) {
                    final Counters end = Counters.now();
                    report.add(rebuildLine(stageStart, end, idleSince));
                    startSpin(minecraft, LAP_FRAMES / 2);
                    enter(Stage.WARMUP_LAP);
                } else if (stageTicks > TIMEOUT_TICKS) {
                    fail(minecraft, current().name() + ": rebuild did not settle");
                }
            }
            case WARMUP_LAP -> {
                if (!spinning) {
                    stableTicks = 0;
                    enter(Stage.SETTLE);
                }
            }
            case SETTLE -> {
                if (settled(minecraft, SETTLE_TICKS)) {
                    stageStart = Counters.now();
                    startSpin(minecraft, LAP_FRAMES);
                    enter(Stage.MEASURE_LAP);
                } else if (stageTicks > TIMEOUT_TICKS) {
                    fail(minecraft, current().name() + ": warm-up lap did not settle");
                }
            }
            case MEASURE_LAP -> {
                if (!spinning) {
                    report.add(lapLine(stageStart, Counters.now()));
                    nextVariant(minecraft);
                }
            }
            default -> {
            }
        }
    }

    private void enter(final Stage next) {
        stage = next;
        stageTicks = 0;
    }

    private Variant current() {
        return VARIANTS.get(variant);
    }

    private boolean settled(final Minecraft minecraft, final int ticks) {
        final ChunkRenderDispatcher dispatcher = minecraft.levelRenderer.getChunkRenderDispatcher();
        final boolean idle = dispatcher != null && dispatcher.getToBatchCount() == 0 && dispatcher.getToUpload() == 0
                && dispatcher.isQueueEmpty() && minecraft.levelRenderer.countRenderedChunks() > 0;
        if (idle) {
            if (stableTicks == 0) {
                idleSince = System.nanoTime();
            }
            stableTicks++;
        } else {
            stableTicks = 0;
        }
        return stableTicks >= ticks;
    }

    private void nextVariant(final Minecraft minecraft) {
        final HardwareCapabilities caps = MetalRenderClient.capabilities();
        do {
            variant++;
            if (variant >= VARIANTS.size()) {
                finish(minecraft);
                return;
            }
            if (!selected(current())) {
                continue;
            }
            if (!current().supported().test(caps)) {
                report.add(current().name() + ": SKIPPED (not supported on this device)");
            }
        } while (!selected(current()) || !current().supported().test(caps));
        final Variant next = current();
        final MetalRenderConfig config = MetalRenderClient.config();
        final boolean tierChanged = config.tier != next.tier();
        config.enabled = next.enabled();
        config.tier = next.tier();
        config.upscaler = next.upscaler();
        config.resolutionScale = next.scale();
        config.meshShaders = true;
        config.debugInfo = false;
        MetalRenderClient.applyConfig(tierChanged);
        if (next.enabled() && !MetalRenderClient.isActive()) {
            fail(minecraft, next.name() + ": Metal inactive (" + MetalRenderClient.status() + ")");
            return;
        }
        enter(Stage.APPLY);
    }

    private static boolean selected(final Variant variant) {
        return ONLY.isBlank() || Arrays.asList(ONLY.split(",")).contains(variant.name());
    }

    // ------------------------------------------------------------------ report

    private String rebuildLine(final Counters a, final Counters b, final long idleAt) {
        final double wallMs = ((idleAt > 0 ? idleAt : b.time()) - a.time()) / 1e6;
        return String.format("%s rebuild: %.0f ms wall, worker CPU %.0f ms, convert %.1f ms (%d quads), "
                        + "render-thread apply %.1f ms (%d uploads)",
                current().name(), wallMs, (b.workerCpu() - a.workerCpu()) / 1e6, (b.packNanos() - a.packNanos()) / 1e6,
                b.packedQuads() - a.packedQuads(), (b.uploadNanos() - a.uploadNanos()) / 1e6, b.uploads() - a.uploads());
    }

    private String lapLine(final Counters a, final Counters b) {
        final long[] frames = Arrays.copyOfRange(frameNanos, SKIP_FRAMES, frameNanos.length);
        final long[] level = Arrays.copyOfRange(levelNanos, SKIP_FRAMES, levelNanos.length);
        final int n = frames.length;
        final double meanFrame = mean(frames);
        final long[] sorted = frames.clone();
        Arrays.sort(sorted);
        final long opaqueFrames = Math.max(1, b.opaqueFrames() - a.opaqueFrames());
        final boolean metal = current().enabled();
        final StringBuilder line = new StringBuilder(String.format(
                "%s lap: %d frames, %.1f fps | frame mean %.2f ms, p50 %.2f, p95 %.2f, p99 %.2f, max %.2f | "
                        + "renderLevel CPU %.2f ms, OpenGL GPU %.2f ms",
                current().name(), n, 1e9 / meanFrame, meanFrame / 1e6, pct(sorted, 0.50), pct(sorted, 0.95),
                pct(sorted, 0.99), sorted[n - 1] / 1e6, mean(level) / 1e6,
                glGpuSamples == 0 ? 0.0 : glGpuMsSum / glGpuSamples));
        final double swapMs = (b.swapNanos() - a.swapNanos()) / 1e6 / spinFrames;
        line.append(String.format(" | swap %.2f ms, frame minus swap %.2f ms", swapMs, meanFrame / 1e6 - swapMs));
        if (metal) {
            line.append(String.format(" | Metal pass CPU %.3f ms (begin %.3f, wait %.3f; %.3f ms of vanilla work "
                            + "overlapped), GPU %.3f ms",
                    (b.opaqueNanos() - a.opaqueNanos()) / 1e6 / opaqueFrames,
                    (b.beginNanos() - a.beginNanos()) / 1e6 / opaqueFrames,
                    (b.waitNanos() - a.waitNanos()) / 1e6 / opaqueFrames,
                    (b.overlapNanos() - a.overlapNanos()) / 1e6 / opaqueFrames,
                    gpuSamples == 0 ? 0.0 : gpuMsSum / gpuSamples));
        } else {
            line.append(String.format(" | vanilla solid+cutout CPU %.3f ms", (b.vanillaNanos() - a.vanillaNanos()) / 1e6 / n));
        }
        line.append(String.format(" | uploads during lap %d", b.uploads() - a.uploads()));
        return line.toString();
    }

    private static double mean(final long[] values) {
        double sum = 0.0;
        for (final long v : values) {
            sum += v;
        }
        return values.length == 0 ? 0.0 : sum / values.length;
    }

    private static double pct(final long[] sorted, final double p) {
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(p * (sorted.length - 1)))] / 1e6;
    }

    private static long workerCpuNanos() {
        final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long total = 0L;
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith("Worker-Main")) {
                final long cpu = bean.getThreadCpuTime(thread.getId());
                if (cpu > 0) {
                    total += cpu;
                }
            }
        }
        return total;
    }

    private void fail(final Minecraft minecraft, final String message) {
        report.add("FAIL: " + message);
        finish(minecraft);
    }

    private void finish(final Minecraft minecraft) {
        stage = Stage.DONE;
        final Path out = Path.of("benchmark");
        try {
            Files.createDirectories(out);
            Files.write(out.resolve("report.txt"), report, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            MetalRenderMod.LOGGER.error("MetalRender benchmark could not write its report", e);
        }
        for (final String line : report) {
            MetalRenderMod.LOGGER.info("[benchmark] {}", line);
        }
        minecraft.stop();
    }

    // ------------------------------------------------------------------ world

    private static void prepareOptions(final Minecraft minecraft) {
        minecraft.options.hideGui = true;
        minecraft.options.renderDebug = false;
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        minecraft.options.narrator().set(NarratorStatus.OFF);
        minecraft.options.renderDistance().set(DISTANCE);
        minecraft.options.simulationDistance().set(8);
        minecraft.options.cloudStatus().set(CloudStatus.OFF);
        minecraft.options.particles().set(ParticleStatus.MINIMAL);
        minecraft.options.bobView().set(false);
        minecraft.options.enableVsync().set(false);
        minecraft.options.framerateLimit().set(260); // unlimited
        minecraft.getWindow().updateVsync(false);
        minecraft.options.fov().set(70);
        minecraft.options.save();
    }

    /** Hovers the player, flying, 30 blocks above the spawn surface at noon. */
    private static void placePlayer(final ServerLevel level) {
        final GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        final BlockPos spawn = level.getSharedSpawnPos();
        final int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        final ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, spawn.getX() + 0.5, ground + 30.0, spawn.getZ() + 0.5, 180.0f, PITCH);
    }
}
