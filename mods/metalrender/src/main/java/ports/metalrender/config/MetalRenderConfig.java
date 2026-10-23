/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import ports.metalrender.nativebridge.MetalNative;

/** {@code config/metalrender.json}. Unknown or out-of-range values fall back to safe defaults. */
public final class MetalRenderConfig {

    public enum Tier {
        AUTO(MetalNative.TIER_AUTO),
        METAL4(MetalNative.TIER_METAL4),
        METAL3_MESH(MetalNative.TIER_METAL3_MESH),
        COMPUTE_ICB(MetalNative.TIER_COMPUTE_ICB);

        public final int nativeId;

        Tier(final int nativeId) {
            this.nativeId = nativeId;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Upscaler {
        NONE(MetalNative.UPSCALE_NONE),
        METALFX_SPATIAL(MetalNative.UPSCALE_SPATIAL),
        METALFX_TEMPORAL(MetalNative.UPSCALE_TEMPORAL);

        public final int nativeId;

        Upscaler(final int nativeId) {
            this.nativeId = nativeId;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 1.0f;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Use the Metal renderer for terrain. Off means vanilla OpenGL terrain. */
    public boolean enabled = true;
    public Tier tier = Tier.AUTO;
    public Upscaler upscaler = Upscaler.NONE;
    /** Render-resolution fraction of the window, 0.5 - 1.0. */
    public float resolutionScale = 1.0f;
    public boolean meshShaders = true;
    /** Adds MetalRender lines to the F3 screen. */
    public boolean debugInfo = true;

    public MetalRenderConfig sanitized() {
        if (tier == null) {
            tier = Tier.AUTO;
        }
        if (upscaler == null) {
            upscaler = Upscaler.NONE;
        }
        if (Float.isNaN(resolutionScale)) {
            resolutionScale = MAX_SCALE;
        }
        resolutionScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, resolutionScale));
        return this;
    }

    public static MetalRenderConfig load(final Path file) {
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                final MetalRenderConfig config = GSON.fromJson(reader, MetalRenderConfig.class);
                if (config != null) {
                    return config.sanitized();
                }
            } catch (final IOException | JsonParseException e) {
                // fall through to defaults; the next save rewrites a valid file
            }
        }
        return new MetalRenderConfig();
    }

    public void save(final Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(sanitized(), writer);
        }
    }
}
