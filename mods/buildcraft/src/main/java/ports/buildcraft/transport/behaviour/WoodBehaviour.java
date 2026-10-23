package ports.buildcraft.transport.behaviour;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.flow.FluidFlow;
import ports.buildcraft.transport.flow.ItemFlow;

/**
 * Upstream {@code PipeBehaviourWood}: power received from an engine pulls items or fluid out of the
 * inventory or tank on the selected face, at upstream's default cost of 1 MJ per item and
 * 1 MJ per 1000 mB.
 */
public class WoodBehaviour extends DirectionalBehaviour {
    public static final long MJ_PER_ITEM = Power.MJ;
    public static final long MJ_PER_MILLIBUCKET = 1_000;
    private static final long MAX_REQUEST = 512 * Power.MJ;

    private final EnergyPort energy = new EnergyPort() {
        @Override
        public long receive(long amount, boolean simulate) {
            if (currentDir == null || amount <= 0) return 0;
            long offered = Power.fromUnits(Math.min(amount, Power.toUnits(MAX_REQUEST)));
            long used = offered - extractFromSource(offered, simulate);
            if (used <= 0) return 0;
            return Math.min(amount, (used + Power.MICRO_MJ_PER_UNIT - 1) / Power.MICRO_MJ_PER_UNIT);
        }

        @Override public long extract(long amount, boolean simulate) { return 0; }
        @Override public long stored() { return 0; }
        @Override public long capacity() { return Power.toUnits(MAX_REQUEST); }
        @Override public boolean canReceive() { return true; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean acceptsRedstoneEngine() { return true; }
    };

    public WoodBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof WoodBehaviour);
    }

    @Override
    protected boolean canFaceDirection(@Nullable Direction dir) {
        return dir != null && pipe.connectedType(dir) == PipeBlockEntity.ConnectedType.TILE;
    }

    @Override
    public boolean isSpecialSide(Direction face) {
        return face == currentDir;
    }

    @Override
    public void fluidSideCheck(SideCheck check) {
        if (currentDir != null) check.disallow(currentDir);
    }

    /** @return the power left over after extracting. */
    protected long extractFromSource(long power, boolean simulate) {
        if (power <= 0 || currentDir == null) return power;
        if (pipe.flow() instanceof ItemFlow items) {
            int maxItems = (int) Math.min(Integer.MAX_VALUE, power / MJ_PER_ITEM);
            if (maxItems > 0) {
                int extracted = extractItems(items, currentDir, maxItems, simulate);
                if (extracted > 0) return power - extracted * MJ_PER_ITEM;
            }
        } else if (pipe.flow() instanceof FluidFlow fluids) {
            int maxMillibuckets = (int) Math.min(Integer.MAX_VALUE, power / MJ_PER_MILLIBUCKET);
            if (maxMillibuckets > 0) {
                FluidAmount extracted = extractFluid(fluids, currentDir, maxMillibuckets, simulate);
                if (!extracted.isEmpty()) return power - extracted.amount() * MJ_PER_MILLIBUCKET;
            }
        }
        return power;
    }

    /** Upstream {@code extractItems}: @return the number of items pulled from {@code dir}. */
    protected int extractItems(ItemFlow flow, Direction dir, int count, boolean simulate) {
        return flow.tryExtractItems(count, dir, null, stack -> true, simulate);
    }

    /** Upstream {@code extractFluid}. */
    protected FluidAmount extractFluid(FluidFlow flow, Direction dir, int millibuckets, boolean simulate) {
        return flow.tryExtractFluid(millibuckets, dir, simulate);
    }

    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energy;
    }

    @Override
    public long receiveRedstonePower(long microJoules, boolean simulate) {
        return extractFromSource(microJoules, simulate);
    }
}
