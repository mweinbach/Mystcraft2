package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loader fixtures for the transfer GameTests: a test-only fluid tank on vanilla jukeboxes and a
 * two-tank storage on enchanting tables (neither carries item or fluid storage of its own in 1.19.2),
 * and a block-break listener that refuses every position in {@link #PROTECTED}, standing in for a
 * claim mod.
 */
public interface TransferTestPlatform {

    /** Capacity of the jukebox test tanks. */
    long TANK_MILLIBUCKETS = 16_000;

    /** Absolute positions the test break listener refuses on both loaders. */
    Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();

    TankAccess tank(BlockEntity jukebox);

    /**
     * Tank {@code index} (0 or 1) of the two-tank test storage on enchanting tables, which lists tank 0
     * first, like a machine with several tanks.
     */
    TankAccess multiTank(BlockEntity enchantingTable, int index);

    interface TankAccess {
        @Nullable
        Fluid fluid();

        long millibuckets();

        void set(Fluid fluid, long millibuckets);
    }

    final class Holder {
        private static TransferTestPlatform platform;

        private Holder() { }

        public static void install(TransferTestPlatform implementation) {
            platform = implementation;
        }

        public static TransferTestPlatform get() {
            if (platform == null) throw new IllegalStateException("No transfer GameTest platform installed");
            return platform;
        }
    }
}
