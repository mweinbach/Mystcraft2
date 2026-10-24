/*
 * ProjectE 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes a bush's soil rule to {@code IForgeBlockState.canSustainPlant}. */
@Mixin(BushBlock.class)
public interface BushBlockAccessor {

    @Invoker("mayPlaceOn")
    boolean projecte$mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos);
}
