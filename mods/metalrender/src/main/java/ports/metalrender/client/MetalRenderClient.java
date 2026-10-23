/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import ports.metalrender.MetalRenderMod;
import ports.metalrender.config.MetalRenderConfig;
import ports.metalrender.nativebridge.HardwareCapabilities;
import ports.metalrender.nativebridge.MetalNative;
import ports.metalrender.nativebridge.NativeArena;
import ports.metalrender.nativebridge.NativeLibraryLoader;
import ports.metalrender.terrain.SectionStore;

/**
 * Client entrypoint and owner of the native Metal context. On anything other than macOS, or if the
 * natives or the Metal device are unavailable, MetalRender stays inactive and vanilla OpenGL terrain
 * rendering runs untouched.
 */
public final class MetalRenderClient implements ClientModInitializer {

    private static final int INITIAL_VERTICES = 4 * 1024 * 1024;
    private static final int INITIAL_SECTIONS = 4096;
    private static final int INITIAL_MESHLETS = 64 * 1024;

    private static MetalRenderConfig config = new MetalRenderConfig();
    private static Path configFile;
    private static HardwareCapabilities capabilities = HardwareCapabilities.parse("");
    private static Path nativeDir;
    private static long context;
    private static SectionStore store;
    private static String status = "not initialized";
    private static boolean failed;
    private static volatile boolean active;
    private static volatile int epoch;

    @Override
    public void onInitializeClient() {
        MetalRenderMod.markClientInitialized();
        configFile = FabricLoader.getInstance().getConfigDir().resolve("metalrender.json");
        config = MetalRenderConfig.load(configFile);
        ClientCommandRegistrationCallback.EVENT.register(MetalRenderCommands::register);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof VideoSettingsScreen) {
                Screens.getButtons(screen).add(new Button(width - 106, 6, 100, 20,
                        Component.translatable("metalrender.settings.button"),
                        button -> client.setScreen(new MetalRenderSettingsScreen(screen))));
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> destroyContext());

        if (!NativeLibraryLoader.isMacOs()) {
            status = "inactive: MetalRender needs macOS; vanilla OpenGL terrain is in use";
            MetalRenderMod.LOGGER.info(status);
            return;
        }
        try {
            final String version = FabricLoader.getInstance().getModContainer(MetalRenderMod.MOD_ID)
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
            nativeDir = NativeLibraryLoader.load(FabricLoader.getInstance().getGameDir()
                    .resolve(".metalrender").resolve("natives").resolve(version));
            capabilities = HardwareCapabilities.parse(MetalNative.probeCapabilities());
        } catch (final IOException | LinkageError e) {
            status = "inactive: could not load the Metal natives (" + e.getMessage() + ")";
            MetalRenderMod.LOGGER.error("MetalRender {}", status, e);
            return;
        }
        MetalRenderMod.LOGGER.info("MetalRender capabilities: {}", capabilities.asMap());
        createContext();
    }

    private static void createContext() {
        context = MetalNative.initContext(nativeDir.toString(), config.tier.nativeId,
                INITIAL_VERTICES, INITIAL_SECTIONS, INITIAL_MESHLETS);
        if (context == 0) {
            status = "inactive: no usable Metal device or shader library";
            MetalRenderMod.LOGGER.error("MetalRender {}", status);
            updateActive();
            return;
        }
        store = new SectionStore(new NativeArena(context));
        epoch++;
        MetalTerrainRenderer.onContextCreated();
        final String[] info = MetalNative.queryDeviceInfo(context).split("\\|", -1);
        status = MetalNative.tierName(MetalNative.getActiveTier(context))
                + (info.length > 10 && !info[10].isEmpty() ? " (" + info[10] + ")" : "")
                + " on " + (info.length > 0 ? info[0] : "unknown GPU");
        MetalRenderMod.LOGGER.info("MetalRender active: {}", status);
        updateActive();
    }

    private static void destroyContext() {
        epoch++;
        active = false;
        if (store != null) {
            store.releaseAll();
            store = null;
        }
        MetalTerrainRenderer.onContextDestroyed();
        if (context != 0) {
            MetalNative.destroyContext(context);
            context = 0;
        }
    }

    private static void updateActive() {
        active = context != 0 && config.enabled && !failed;
    }

    /** Whether chunk geometry currently goes to Metal. Safe to read from chunk worker threads. */
    public static boolean isActive() {
        return active;
    }

    /** Changes whenever previously queued uploads must be discarded. */
    public static int epoch() {
        return epoch;
    }

    public static long context() {
        return context;
    }

    @Nullable
    public static SectionStore store() {
        return store;
    }

    public static MetalRenderConfig config() {
        return config;
    }

    public static HardwareCapabilities capabilities() {
        return capabilities;
    }

    public static String status() {
        if (context != 0 && !config.enabled) {
            return "disabled in settings (" + status + ")";
        }
        return status;
    }

    /** Turns MetalRender off for this session after an unrecoverable error. */
    static void fail(final String reason) {
        failed = true;
        status = "inactive after error: " + reason;
        updateActive();
        reloadChunks();
    }

    /**
     * Saves the config and applies it. Must run on the render thread. A tier change recreates the
     * Metal context; any change of whether Metal is active rebuilds every chunk.
     */
    public static void applyConfig(final boolean tierChanged) {
        try {
            config.save(configFile);
        } catch (final IOException e) {
            MetalRenderMod.LOGGER.warn("MetalRender could not save {}", configFile, e);
        }
        final boolean wasActive = active;
        if (tierChanged && nativeDir != null) {
            destroyContext();
            failed = false;
            createContext();
        }
        updateActive();
        MetalTerrainRenderer.onSettingsChanged();
        if (tierChanged || wasActive != active) {
            reloadChunks();
        }
    }

    /** Rebuilds every chunk (on the next client task, never in the middle of a frame). */
    static void reloadChunks() {
        epoch++;
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.tell(() -> {
                if (minecraft.levelRenderer != null) {
                    minecraft.levelRenderer.allChanged();
                }
            });
        }
    }
}
