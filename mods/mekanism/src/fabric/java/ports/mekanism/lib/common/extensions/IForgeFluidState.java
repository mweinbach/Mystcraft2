/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.material.FluidState;
import ports.mekanism.lib.fluids.FluidType;

/**
 * Methods Forge adds to {@link FluidState}, injected into it on Fabric. Each one asks the state's fluid.
 */
public interface IForgeFluidState {

    private FluidState self() {
        return (FluidState) this;
    }

    default FluidType getFluidType() {
        return self().getType().getFluidType();
    }

    default float getExplosionResistance(BlockGetter level, BlockPos pos, Explosion explosion) {
        return self().getType().getExplosionResistance(self(), level, pos, explosion);
    }
}
