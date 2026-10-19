package ports.buildcraft.lib;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/** Fluid helpers shared by tanks, pipes and machines. */
public final class Fluids2 {
    private Fluids2() {}

    /** Storages always hold the source form of a fluid, as Forge fluid stacks and Fabric variants do. */
    public static Fluid still(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }
}
