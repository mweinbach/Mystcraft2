/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import ports.extremereactors.multiblock.IMultiblockController;
import ports.extremereactors.multiblock.variant.IMultiblockVariant;

/**
 * A part type whose blocks and block entities are described by {@link MultiblockPartTypeProperties}.
 */
public interface IMultiblockPartType2<Controller extends IMultiblockController<Controller>,
                                      PartType extends Enum<PartType> & IMultiblockPartType2<Controller, PartType>>
        extends IMultiblockPartType {

    MultiblockPartTypeProperties<Controller, PartType> getPartTypeProperties();

    @Override
    default String getTranslationKey() {
        return this.getPartTypeProperties().getTranslationKey();
    }

    @SuppressWarnings("unchecked")
    default MultiblockPartBlock<Controller, PartType> createBlock(final IMultiblockVariant variant) {
        return this.getPartTypeProperties().createBlock((PartType) this, variant);
    }

    @SuppressWarnings("unchecked")
    default MultiblockPartBlock<Controller, PartType> createBlock() {
        return this.getPartTypeProperties().createBlock((PartType) this, null);
    }
}
