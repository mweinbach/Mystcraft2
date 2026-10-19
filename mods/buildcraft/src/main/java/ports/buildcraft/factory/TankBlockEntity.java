package ports.buildcraft.factory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileTank}: a 16,000 mB tank. Fills and drains act on the whole vertical column, with
 * liquids settling at the bottom and gases at the top.
 */
public class TankBlockEntity extends SyncedBlockEntity implements PortHolder {
    public static final int CAPACITY = 16_000;

    final Tank tank = new Tank("tank", CAPACITY);
    private final FluidPort port = new ColumnPort();
    private int lastComparatorLevel;
    private boolean playerInteracting;
    private Fluid syncedFluid = Fluids.EMPTY;
    private int syncedAmount;

    private Fluid clientFluid = Fluids.EMPTY;
    private float clientAmount;
    private float lastClientAmount;
    private int clientTarget;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(BCFactory.TANK_TYPE.get(), pos, state);
    }

    public Tank tank() {
        return tank;
    }

    public int getComparatorLevel() {
        int amount = tank.amount();
        return amount * 14 / tank.capacity() + (amount > 0 ? 1 : 0);
    }

    void setPlayerInteracting(boolean interacting) {
        playerInteracting = interacting;
    }

    void serverTick() {
        int level = getComparatorLevel();
        if (level != lastComparatorLevel) {
            lastComparatorLevel = level;
            setChanged();
        }
        if (syncedFluid != tank.fluid() || syncedAmount != tank.amount()) {
            syncedFluid = tank.fluid();
            syncedAmount = tank.amount();
            queueSync();
        }
        flushSync();
    }

    /** Upstream {@code FluidSmoother}: the rendered level eases toward the synced amount. */
    void clientTick() {
        lastClientAmount = clientAmount;
        float diff = clientTarget - clientAmount;
        clientAmount = Math.abs(diff) < 1 ? clientTarget : clientAmount + diff / 4;
    }

    public Fluid fluidForRender() {
        return clientFluid;
    }

    public float amountForRender(float partialTicks) {
        return lastClientAmount + (clientAmount - lastClientAmount) * partialTicks;
    }

    /** Upstream {@code balanceTankFluids}: settles a freshly placed tank's column. */
    public void balanceTankFluids() {
        List<TankBlockEntity> tanks = getTanks();
        Fluid fluid = null;
        for (TankBlockEntity tile : tanks) {
            if (tile.tank.isEmpty()) continue;
            if (fluid == null) fluid = tile.tank.fluid();
            else if (fluid != tile.tank.fluid()) return;
        }
        if (fluid == null) return;
        if (Platform.get().isGaseous(fluid)) Collections.reverse(tanks);
        TankBlockEntity prev = null;
        for (TankBlockEntity tile : tanks) {
            if (prev != null) {
                FluidAmount moved = tile.tank.drain(null, prev.tank.space(), true);
                int filled = prev.tank.fill(moved.fluid(), (int) moved.amount(), false);
                tile.tank.drain(null, filled, false);
                tile.setChanged();
                prev.setChanged();
            }
            prev = tile;
        }
    }

    /** All connected tanks in this column, bottom to top. */
    List<TankBlockEntity> getTanks() {
        Deque<TankBlockEntity> tanks = new ArrayDeque<>();
        tanks.add(this);
        if (level == null) return new ArrayList<>(tanks);
        BlockPos pos = worldPosition.above();
        while (level.getBlockEntity(pos) instanceof TankBlockEntity above) {
            tanks.addLast(above);
            pos = pos.above();
        }
        pos = worldPosition.below();
        while (level.getBlockEntity(pos) instanceof TankBlockEntity below) {
            tanks.addFirst(below);
            pos = pos.below();
        }
        return new ArrayList<>(tanks);
    }

    /** Total contents of the column, as upstream {@code getTankProperties}. */
    public FluidAmount columnContents() {
        List<TankBlockEntity> tanks = getTanks();
        Fluid fluid = tanks.get(0).tank.fluid();
        if (fluid == Fluids.EMPTY) fluid = tanks.get(tanks.size() - 1).tank.fluid();
        long amount = 0;
        for (TankBlockEntity tile : tanks) amount += tile.tank.amount();
        return new FluidAmount(fluid, amount);
    }

    public long columnCapacity() {
        return (long) getTanks().size() * CAPACITY;
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        return port;
    }

    private void changed() {
        setChanged();
        if (playerInteracting) {
            syncedFluid = tank.fluid();
            syncedAmount = tank.amount();
            queueSync();
            flushSync();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("tank", tank.save());
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        tank.load(tag.getCompound("tank"));
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putString("fluid", Registry.FLUID.getKey(tank.fluid()).toString());
        tag.putInt("amount", tank.amount());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        boolean first = clientFluid == Fluids.EMPTY;
        clientFluid = Registry.FLUID.get(new ResourceLocation(tag.getString("fluid")));
        clientTarget = tag.getInt("amount");
        if (first || clientFluid == Fluids.EMPTY) clientAmount = lastClientAmount = clientTarget;
    }

    private final class ColumnPort implements FluidPort {
        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            if (amount <= 0 || fluid == Fluids.EMPTY) return 0;
            List<TankBlockEntity> tanks = getTanks();
            for (TankBlockEntity tile : tanks) {
                if (!tile.tank.isEmpty() && tile.tank.fluid() != fluid) return 0;
            }
            if (Platform.get().isGaseous(fluid)) Collections.reverse(tanks);
            long filled = 0;
            for (TankBlockEntity tile : tanks) {
                int tankFilled = tile.tank.fill(fluid, (int) Math.min(Integer.MAX_VALUE, amount - filled), simulate);
                if (tankFilled > 0) {
                    if (!simulate) tile.changed();
                    filled += tankFilled;
                    if (filled >= amount) break;
                }
            }
            return filled;
        }

        @Override
        public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
            if (amount <= 0) return FluidAmount.EMPTY;
            List<TankBlockEntity> tanks = getTanks();
            boolean gas = false;
            for (TankBlockEntity tile : tanks) {
                if (!tile.tank.isEmpty()) {
                    gas = Platform.get().isGaseous(tile.tank.fluid());
                    break;
                }
            }
            if (!gas) Collections.reverse(tanks);
            Fluid drainedFluid = null;
            long total = 0;
            for (TankBlockEntity tile : tanks) {
                long realMax = amount - total;
                if (realMax <= 0) break;
                FluidAmount drained = tile.tank.drain(drainedFluid != null ? drainedFluid : filter, (int) Math.min(Integer.MAX_VALUE, realMax), simulate);
                if (drained.isEmpty()) continue;
                if (!simulate) tile.changed();
                drainedFluid = drained.fluid();
                total += drained.amount();
            }
            return drainedFluid == null ? FluidAmount.EMPTY : new FluidAmount(drainedFluid, total);
        }

        @Override
        public List<FluidAmount> contents() {
            return List.of(columnContents());
        }

        @Override
        public long capacity(int index) {
            return columnCapacity();
        }

        @Override
        public Object snapshot() {
            List<TankBlockEntity> tanks = getTanks();
            long[] values = new long[tanks.size()];
            for (int i = 0; i < values.length; i++) values[i] = tanks.get(i).tank.snapshotValue();
            return new ColumnSnapshot(tanks, values);
        }

        @Override
        public void restore(Object snapshot) {
            ColumnSnapshot column = (ColumnSnapshot) snapshot;
            for (int i = 0; i < column.values.length; i++) column.tanks.get(i).tank.restoreValue(column.values[i]);
        }

        @Override
        public void onCommitted() {
            for (BlockEntity tile : getTanks()) tile.setChanged();
        }
    }

    private record ColumnSnapshot(List<TankBlockEntity> tanks, long[] values) {}
}
