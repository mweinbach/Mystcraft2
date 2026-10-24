package ports.buildcraft.robotics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Robots that stopped calling a station home (picked up, killed or moved to another home) while that station's chunk
 * was unloaded. The station still names the robot as linked, so it drops the link when it next ticks or is used.
 * Saved with the level, so a restart in between does not leave the station reserved forever. Written for this port.
 */
public final class PendingUnlinks extends SavedData {
    private static final String DATA_NAME = "buildcraft_robot_pending_unlinks";

    private final Map<StationRef, UUID> pending = new HashMap<>();

    public static PendingUnlinks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PendingUnlinks::load, PendingUnlinks::new, DATA_NAME);
    }

    /** Records that {@code robot} no longer calls {@code station} home. */
    public void add(StationRef station, UUID robot) {
        if (robot.equals(pending.put(station, robot))) return;
        setDirty();
    }

    /** The robot {@code station} should no longer be linked to, if any, without removing it. */
    @Nullable
    public UUID peek(StationRef station) {
        return pending.get(station);
    }

    /** Removes and returns the robot {@code station} should no longer be linked to, if any. */
    @Nullable
    UUID take(StationRef station) {
        if (pending.isEmpty()) return null;
        UUID robot = pending.remove(station);
        if (robot != null) setDirty();
        return robot;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        pending.forEach((station, robot) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("station", station.save());
            entry.putUUID("robot", robot);
            list.add(entry);
        });
        tag.put("pending", list);
        return tag;
    }

    public static PendingUnlinks load(CompoundTag tag) {
        PendingUnlinks data = new PendingUnlinks();
        ListTag list = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("station", Tag.TAG_COMPOUND) && entry.hasUUID("robot")) {
                data.pending.put(StationRef.load(entry.getCompound("station")), entry.getUUID("robot"));
            }
        }
        return data;
    }
}
