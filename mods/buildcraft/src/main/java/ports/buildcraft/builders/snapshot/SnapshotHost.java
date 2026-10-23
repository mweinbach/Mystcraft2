package ports.buildcraft.builders.snapshot;

import com.mojang.authlib.GameProfile;
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

    /**
     * Upstream {@code TileBC_Neptune.getOwner}: whom the machine acts for, the player who placed it or
     * {@link ports.buildcraft.lib.BlockProtection#NULL_PROFILE}. Every block it breaks or places, and every
     * entity it removes or spawns, must be allowed for that player's fake player by
     * {@link ports.buildcraft.lib.BlockProtection}.
     */
    GameProfile owner();
}
