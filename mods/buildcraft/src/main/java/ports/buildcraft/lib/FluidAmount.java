package ports.buildcraft.lib;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** A fluid and an amount in millibuckets. Fluid NBT is not modelled (see PARITY.md). */
public record FluidAmount(Fluid fluid, long amount) {
    public static final FluidAmount EMPTY = new FluidAmount(Fluids.EMPTY, 0);

    public FluidAmount {
        if (amount <= 0 || fluid == Fluids.EMPTY) {
            fluid = Fluids.EMPTY;
            amount = 0;
        }
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    public FluidAmount withAmount(long newAmount) {
        return new FluidAmount(fluid, newAmount);
    }
}
