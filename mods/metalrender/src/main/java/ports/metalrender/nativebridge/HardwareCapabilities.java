/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.nativebridge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parsed form of {@link MetalNative#probeCapabilities()}. */
public final class HardwareCapabilities {

    private final Map<String, String> values;

    private HardwareCapabilities(final Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static HardwareCapabilities parse(final String probe) {
        final Map<String, String> values = new LinkedHashMap<>();
        if (probe != null) {
            for (final String pair : probe.split(";")) {
                final int eq = pair.indexOf('=');
                if (eq > 0) {
                    values.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        return new HardwareCapabilities(values);
    }

    public boolean flag(final String key) {
        return "1".equals(values.get(key));
    }

    public String get(final String key, final String fallback) {
        return values.getOrDefault(key, fallback);
    }

    public boolean metal() {
        return flag("metal");
    }

    public boolean metal3() {
        return flag("metal3");
    }

    public boolean metal4() {
        return flag("metal4");
    }

    public boolean msl41() {
        return flag("msl41");
    }

    public boolean meshShaders() {
        return flag("meshShaders");
    }

    public boolean metalFxSpatial() {
        return flag("metalfxSpatial");
    }

    public boolean metalFxTemporal() {
        return flag("metalfxTemporal");
    }

    public String device() {
        return get("device", "unknown");
    }

    public Map<String, String> asMap() {
        return values;
    }

    /** Short human summary, e.g. "Apple M3 Max: Metal 4 (MSL 4.1), mesh shaders, MetalFX spatial+temporal". */
    public String summary() {
        if (!metal()) {
            return "no Metal device";
        }
        final StringBuilder sb = new StringBuilder(device()).append(": ");
        sb.append(metal4() ? (msl41() ? "Metal 4 (MSL 4.1)" : "Metal 4") : metal3() ? "Metal 3" : "Metal 2");
        if (meshShaders()) {
            sb.append(", mesh shaders");
        }
        if (metalFxSpatial() || metalFxTemporal()) {
            sb.append(", MetalFX ");
            sb.append(metalFxSpatial() && metalFxTemporal() ? "spatial+temporal" : metalFxSpatial() ? "spatial" : "temporal");
        }
        return sb.toString();
    }
}
