package ports.gridwright.gametest.fabric;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import ports.gridwright.gametest.TransferTestPlatform;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Fabric fixtures: a Transfer API fluid storage on jukeboxes and a refusing block-break listener. */
@SuppressWarnings("UnstableApiUsage")
final class FabricTransferTestPlatform implements TransferTestPlatform {

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;
    private static final Map<BlockEntity, Tank> TANKS = Collections.synchronizedMap(new WeakHashMap<>());

    static void register() {
        FluidStorage.SIDED.registerForBlockEntity((jukebox, side) -> TANKS.computeIfAbsent(jukebox, key -> new Tank()),
                BlockEntityType.JUKEBOX);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !PROTECTED.contains(pos));
    }

    @Override
    public TankAccess tank(BlockEntity jukebox) {
        Tank tank = TANKS.get(jukebox);
        if (tank == null) {
            // The storage is created on first lookup.
            FluidStorage.SIDED.find(jukebox.getLevel(), jukebox.getBlockPos(), Direction.UP);
            tank = TANKS.get(jukebox);
        }
        if (tank == null) throw new GameTestAssertException("No test tank on " + jukebox);
        Tank found = tank;
        return new TankAccess() {
            @Nullable
            @Override
            public Fluid fluid() {
                return found.isResourceBlank() || found.amount == 0 ? null : found.variant.getFluid();
            }

            @Override
            public long millibuckets() {
                return found.amount / DROPLETS_PER_MB;
            }

            @Override
            public void set(Fluid fluid, long millibuckets) {
                found.variant = FluidVariant.of(fluid);
                found.amount = millibuckets * DROPLETS_PER_MB;
            }
        };
    }

    private static final class Tank extends SingleVariantStorage<FluidVariant> {
        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return TANK_MILLIBUCKETS * DROPLETS_PER_MB;
        }
    }
}
