/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.power.charging;

import ports.extremereactors.corelib.base.multiblock.part.io.power.IPowerPort;

/**
 * A power port that charges the items put in it instead of feeding a neighbour.
 */
public interface IChargingPort extends IPowerPort {

    IChargingPortHandler getChargingPortHandler();
}
