package ports.gridwright.gametest.fabric;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import ports.gridwright.gametest.TransferTestPlatform;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fabric fixtures: a Transfer API fluid storage on jukeboxes, a combined two-tank one on enchanting
 * tables and a refusing block-break listener.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricTransferTestPlatform implements TransferTestPlatform {

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;
    private static final Map<BlockEntity, Tank> TANKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<BlockEntity, CombinedStorage<FluidVariant, Tank>> MULTI_TANKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    static void register() {
        FluidStorage.SIDED.registerForBlockEntity((jukebox, side) -> TANKS.computeIfAbsent(jukebox, key -> new Tank()),
                BlockEntityType.JUKEBOX);
        FluidStorage.SIDED.registerForBlockEntity((table, side) -> MULTI_TANKS.computeIfAbsent(table,
                key -> new CombinedStorage<>(List.of(new Tank(), new Tank()))), BlockEntityType.ENCHANTING_TABLE);
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
        return access(tank);
    }

    @Override
    public TankAccess multiTank(BlockEntity enchantingTable, int index) {
        if (!MULTI_TANKS.containsKey(enchantingTable)) {
            FluidStorage.SIDED.find(enchantingTable.getLevel(), enchantingTable.getBlockPos(), Direction.UP);
        }
        CombinedStorage<FluidVariant, Tank> tanks = MULTI_TANKS.get(enchantingTable);
        if (tanks == null) throw new GameTestAssertException("No test tanks on " + enchantingTable);
        return access(tanks.parts.get(index));
    }

    private static TankAccess access(Tank tank) {
        return new TankAccess() {
            @Nullable
            @Override
            public Fluid fluid() {
                return tank.isResourceBlank() || tank.amount == 0 ? null : tank.variant.getFluid();
            }

            @Override
            public long millibuckets() {
                return tank.amount / DROPLETS_PER_MB;
            }

            @Override
            public void set(Fluid fluid, long millibuckets) {
                tank.variant = FluidVariant.of(fluid);
                tank.amount = millibuckets * DROPLETS_PER_MB;
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
