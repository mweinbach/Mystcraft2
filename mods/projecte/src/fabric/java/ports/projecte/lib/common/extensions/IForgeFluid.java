/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import ports.projecte.lib.fluids.FluidType;
import ports.projecte.lib.fluids.FluidTypes;

/**
 * Methods Forge adds to {@link Fluid}, injected into it on Fabric. Fluids not built on
 * {@link ports.projecte.lib.fluids.ForgeFlowingFluid} get their type from {@link FluidTypes#of}.
 */
public interface IForgeFluid {

    default FluidType getFluidType() {
        return FluidTypes.of((Fluid) this);
    }

    default float projecte$getExplosionResistance(FluidState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getExplosionResistance();
    }
}
