/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.power;

import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.base.multiblock.part.io.IIOPortHandler;
import ports.extremereactors.corelib.data.IoMode;
import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IWideEnergyStorage2;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

/**
 * The energy side of a power port. The port's machine is the energy storage it works with.
 */
public interface IPowerPortHandler extends IIOPortHandler {

    static <Controller extends AbstractCuboidMultiblockController<Controller> & IWideEnergyStorage2,
            T extends AbstractMultiblockEntity<Controller> & IPowerPort>
    IPowerPortHandler create(final EnergySystem system, final IoMode mode, final T part) {
        return new PowerPortHandler<>(system, mode, part);
    }

    EnergySystem getEnergySystem();

    /**
     * Offer energy to the neighbour of an active port.
     *
     * @return the amount accepted by the neighbour
     */
    WideAmount outputEnergy(WideAmount amount);
}
