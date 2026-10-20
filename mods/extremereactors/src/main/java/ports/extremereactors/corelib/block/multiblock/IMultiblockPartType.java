/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import net.minecraft.util.StringRepresentable;

/**
 * A kind of multiblock part (casing, controller, port, ...), usually an enum constant.
 */
public interface IMultiblockPartType extends StringRepresentable {

    String getTranslationKey();
}
