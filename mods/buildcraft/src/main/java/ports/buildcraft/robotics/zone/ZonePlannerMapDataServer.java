package ports.buildcraft.robotics.zone;

import net.minecraft.world.level.Level;

/** Upstream {@code ZonePlannerMapDataServer}: scans the world it is asked about, whichever dimension the key names. */
public final class ZonePlannerMapDataServer extends ZonePlannerMapData {
    public static final ZonePlannerMapDataServer INSTANCE = new ZonePlannerMapDataServer();

    private ZonePlannerMapDataServer() {}

    @Override
    protected ZonePlannerMapChunk load(Level level, ZonePlannerMapChunkKey key) {
        return new ZonePlannerMapChunk(level, key);
    }
}
