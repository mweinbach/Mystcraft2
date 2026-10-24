package com.agricraft.agricraft.mixin.fabric;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a lava bucket fill a {@code LiquidBlockContainer} (lava-logged crops), as upstream does. Vanilla only
 * tests {@code this.content == Fluids.WATER}; upstream's MixinExtras expression injector is not available in
 * the MixinExtras 0.4 this port bundles, so the {@code Fluids.WATER} read in that comparison (the only one in
 * each method on 1.19.2) answers lava for a lava bucket.
 */
@Mixin(BucketItem.class)
public class BucketItemMixin {

	@Shadow @Final private Fluid content;

	@ModifyExpressionValue(method = "use", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/material/Fluids;WATER:Lnet/minecraft/world/level/material/FlowingFluid;"))
	private FlowingFluid agricraft$lavaInUse(FlowingFluid original) {
		return this.content == Fluids.LAVA ? Fluids.LAVA : original;
	}

	@ModifyExpressionValue(method = "emptyContents", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/material/Fluids;WATER:Lnet/minecraft/world/level/material/FlowingFluid;"))
	private FlowingFluid agricraft$lavaInEmptyContents(FlowingFluid original) {
		return this.content == Fluids.LAVA ? Fluids.LAVA : original;
	}

}
