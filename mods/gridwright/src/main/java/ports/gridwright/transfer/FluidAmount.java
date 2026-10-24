package ports.gridwright.transfer;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** A fluid and an amount in millibuckets; the loader-neutral fluid stack of the transfer code. */
public record FluidAmount(Fluid fluid, long millibuckets) {

    public static final FluidAmount EMPTY = new FluidAmount(Fluids.EMPTY, 0);

    public boolean isEmpty() {
        return millibuckets <= 0 || fluid == Fluids.EMPTY;
    }
}
