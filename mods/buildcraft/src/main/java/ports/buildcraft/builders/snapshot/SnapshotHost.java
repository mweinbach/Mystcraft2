package ports.buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import ports.buildcraft.lib.MjBattery;

/** Upstream {@code ITileForSnapshotBuilder}: the machine a snapshot builder works for. */
public interface SnapshotHost {
    Level hostLevel();

    MjBattery battery();

    /** Where placed items fly from and how far placing costs power. */
    BlockPos builderPos();

    boolean canExcavate();
}
