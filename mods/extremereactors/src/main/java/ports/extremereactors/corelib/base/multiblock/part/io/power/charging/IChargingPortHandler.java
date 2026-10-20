/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.power.charging;

import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.base.multiblock.part.io.power.IPowerPortHandler;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IWideEnergyStorage2;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

/**
 * The logic of a charging port: items put in the input slots receive the energy offered to the port and are moved
 * to the output slots once they are full.
 */
public interface IChargingPortHandler extends IPowerPortHandler, ISyncableEntity {

    static <Controller extends AbstractCuboidMultiblockController<Controller> & IWideEnergyStorage2,
            T extends AbstractMultiblockEntity<Controller> & IChargingPort>
    IChargingPortHandler create(final EnergySystem system, final T part, final int inputSlotsCount,
                                final int outputSlotsCount) {
        return new ChargingPortHandler<>(system, part, inputSlotsCount, outputSlotsCount);
    }

    IItemHandlerModifiable getItemStackHandler(IoDirection direction);

    /**
     * Move the items in the input slots to the output slots, charged or not.
     */
    void eject();
}
