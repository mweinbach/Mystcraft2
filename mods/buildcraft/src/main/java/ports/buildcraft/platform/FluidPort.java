package ports.buildcraft.platform;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.FluidAmount;

/**
 * A fluid storage face measured in millibuckets. Fabric adapters convert to droplets (81 per mB)
 * and drop sub-millibucket remainders, which keeps gameplay identical on both loaders.
 */
public interface FluidPort {
    /** @return the amount accepted, in mB. */
    long fill(Fluid fluid, long amount, boolean simulate);

    /** Drains up to {@code amount} mB of {@code filter}, or of any fluid when the filter is null. */
    FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate);

    /** The fluids currently visible through this face, for extraction by filter. */
    default List<FluidAmount> contents() {
        return List.of();
    }

    /** Capacity of the tank at {@code index} in {@link #contents()}, in mB. */
    default long capacity(int index) {
        return 0;
    }

    /** Null when the port cannot be rolled back; see {@link EnergyPort#snapshot()}. */
    @Nullable
    default Object snapshot() {
        return null;
    }

    default void restore(Object snapshot) {}

    default void onCommitted() {}
}
