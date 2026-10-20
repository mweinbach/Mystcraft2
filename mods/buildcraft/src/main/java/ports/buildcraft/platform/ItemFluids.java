package ports.buildcraft.platform;

import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.FluidAmount;

/**
 * A single fluid-container item seen through the loader's item fluid API, in whole millibuckets.
 * Executed drains and fills change {@link #container()}; the stack it was made from is untouched.
 */
public interface ItemFluids {
    /** Drains up to {@code max} mB of the item's fluid. Fluids carrying NBT are not offered. */
    FluidAmount drain(long max, boolean simulate);

    long fill(FluidAmount fluid, boolean simulate);

    ItemStack container();
}
