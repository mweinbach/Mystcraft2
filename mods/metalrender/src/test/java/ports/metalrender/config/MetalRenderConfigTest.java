/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetalRenderConfigTest {

    @Test
    void roundTripsThroughJson(@TempDir final Path dir) throws Exception {
        final Path file = dir.resolve("config/metalrender.json");
        final MetalRenderConfig config = new MetalRenderConfig();
        config.tier = MetalRenderConfig.Tier.METAL3_MESH;
        config.upscaler = MetalRenderConfig.Upscaler.METALFX_TEMPORAL;
        config.resolutionScale = 0.67f;
        config.meshShaders = false;
        config.save(file);
        final MetalRenderConfig loaded = MetalRenderConfig.load(file);
        assertEquals(MetalRenderConfig.Tier.METAL3_MESH, loaded.tier);
        assertEquals(MetalRenderConfig.Upscaler.METALFX_TEMPORAL, loaded.upscaler);
        assertEquals(0.67f, loaded.resolutionScale);
        assertFalse(loaded.meshShaders);
    }

    @Test
    void sanitizesBadValues(@TempDir final Path dir) throws Exception {
        final Path file = dir.resolve("metalrender.json");
        // hiZOcclusion is a removed option; old config files that still carry it must load.
        Files.writeString(file, "{\"tier\":\"metal9\",\"upscaler\":\"DLSS\",\"resolutionScale\":0.1,\"enabled\":false,"
                + "\"hiZOcclusion\":true}");
        final MetalRenderConfig loaded = MetalRenderConfig.load(file);
        assertEquals(MetalRenderConfig.Tier.AUTO, loaded.tier);
        assertEquals(MetalRenderConfig.Upscaler.NONE, loaded.upscaler);
        assertEquals(MetalRenderConfig.MIN_SCALE, loaded.resolutionScale);
        assertFalse(loaded.enabled);
        Files.writeString(file, "not json");
        assertTrue(MetalRenderConfig.load(file).enabled, "corrupt files fall back to defaults");
    }
}
