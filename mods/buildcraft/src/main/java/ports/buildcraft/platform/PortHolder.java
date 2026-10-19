package ports.buildcraft.platform;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/**
 * Implemented by BuildCraft block entities. Each loader adapter exposes these ports through its
 * native API (Forge capabilities, Fabric block API lookups); shared code never sees those APIs.
 */
public interface PortHolder {
    @Nullable
    default EnergyPort energyPort(@Nullable Direction side) {
        return null;
    }

    @Nullable
    default ItemPort itemPort(@Nullable Direction side) {
        return null;
    }

    @Nullable
    default FluidPort fluidPort(@Nullable Direction side) {
        return null;
    }
}
