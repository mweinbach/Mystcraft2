/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.power;

import ports.extremereactors.corelib.data.IIoEntity;

/**
 * A machine part that moves energy out of, or into, its machine.
 */
public interface IPowerPort extends IIoEntity {

    IPowerPortHandler getPowerPortHandler();
}
