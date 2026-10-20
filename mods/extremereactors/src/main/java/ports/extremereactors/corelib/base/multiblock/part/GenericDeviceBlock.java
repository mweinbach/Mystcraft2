/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part;

import ports.extremereactors.corelib.block.multiblock.IMultiblockPartType;
import ports.extremereactors.corelib.block.multiblock.MultiblockPartBlock;
import ports.extremereactors.multiblock.IMultiblockController;

/**
 * The block of a machine part that does something on its own (controllers, ports, rods...), as opposed to the
 * plain structural blocks.
 */
public class GenericDeviceBlock<Controller extends IMultiblockController<Controller>,
                                PartType extends Enum<PartType> & IMultiblockPartType>
        extends MultiblockPartBlock<Controller, PartType> {

    public GenericDeviceBlock(final MultiblockPartProperties<PartType> properties) {
        super(properties);
    }
}
