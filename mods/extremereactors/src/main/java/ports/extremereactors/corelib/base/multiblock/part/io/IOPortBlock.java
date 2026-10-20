/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io;

import ports.extremereactors.corelib.base.multiblock.part.GenericDeviceBlock;
import ports.extremereactors.corelib.block.INeighborChangeListener;
import ports.extremereactors.corelib.block.multiblock.IMultiblockPartType;
import ports.extremereactors.multiblock.IMultiblockController;

/**
 * The block of a part that moves items, fluids or energy in or out of a machine. Its neighbours are told when it
 * is placed or removed so they can pick up, or drop, the connection.
 */
public class IOPortBlock<Controller extends IMultiblockController<Controller>,
                         PartType extends Enum<PartType> & IMultiblockPartType>
        extends GenericDeviceBlock<Controller, PartType>
        implements INeighborChangeListener.Notifier {

    public IOPortBlock(final MultiblockPartProperties<PartType> properties) {
        super(properties);
    }
}
