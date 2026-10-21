/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionDamageCalculatorMixin {

    @Redirect(method = "getBlockExplosionResistance", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance()F"))
    private float mekanism$blockResistance(Block block, Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
        return state.getExplosionResistance(level, pos, explosion);
    }

    @Redirect(method = "getBlockExplosionResistance", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/material/FluidState;getExplosionResistance()F"))
    private float mekanism$fluidResistance(FluidState fluid, Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState ignored) {
        return fluid.getExplosionResistance(level, pos, explosion);
    }
}
