/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.computer;

/**
 * A block entity that offers a peripheral to the computers next to it.
 */
public interface IComputerPort {

    MultiblockComputerPeripheral<?, ?> getComputerPeripheral();
}
