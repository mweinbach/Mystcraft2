package mekanism.common.integration.energy.forgeenergy;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.integration.energy.IEnergyCompat;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.Direction;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.common.capabilities.ICapabilityProvider;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class ForgeEnergyCompat implements IEnergyCompat {

    @Override
    public Capability<IEnergyStorage> getCapability() {
        return ForgeCapabilities.ENERGY;
    }

    @Override
    public boolean isUsable() {
        return EnergyUnit.FORGE_ENERGY.isEnabled();
    }

    @Override
    public LazyOptional<IEnergyStorage> getHandlerAs(IStrictEnergyHandler handler) {
        return LazyOptional.of(() -> new ForgeEnergyIntegration(handler));
    }

    @Override
    public LazyOptional<IStrictEnergyHandler> getLazyStrictEnergyHandler(ICapabilityProvider provider, @Nullable Direction side) {
        return CapabilityUtils.getCapability(provider, getCapability(), side).lazyMap(ForgeStrictEnergyHandler::new);
    }
}