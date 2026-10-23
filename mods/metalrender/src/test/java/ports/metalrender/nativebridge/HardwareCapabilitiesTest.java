/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.nativebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HardwareCapabilitiesTest {

    @Test
    void parsesTheNativeProbe() {
        final HardwareCapabilities caps = HardwareCapabilities.parse(
                "metal=1;device=Apple M4 Pro;os=26.1;metal3=1;metal4=1;msl41=0;meshShaders=1;"
                        + "metalfxSpatial=1;metalfxTemporal=1;metalfxTemporalScale=1.000-3.000");
        assertTrue(caps.metal4());
        assertFalse(caps.msl41());
        assertEquals("Apple M4 Pro", caps.device());
        assertEquals("1.000-3.000", caps.get("metalfxTemporalScale", ""));
        assertEquals("Apple M4 Pro: Metal 4, mesh shaders, MetalFX spatial+temporal", caps.summary());
        assertEquals("no Metal device", HardwareCapabilities.parse("metal=0;os=12.0").summary());
        assertEquals("no Metal device", HardwareCapabilities.parse(null).summary());
    }
}
