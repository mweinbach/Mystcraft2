package net.mystcraft.fabric.mixin;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.mystcraft.minecraft.client.age.AgeVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public abstract class AgeWaterColorMixin {
    @Inject(method = "getAverageWaterColor", at = @At("RETURN"), cancellable = true)
    private static void mystcraft$water(BlockAndTintGetter view, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(AgeVisualClient.water(callback.getReturnValue()));
    }
    @Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
    private static void mystcraft$grass(BlockAndTintGetter view, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(AgeVisualClient.grass(pos, callback.getReturnValue()));
    }
    @Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void mystcraft$foliage(BlockAndTintGetter view, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(AgeVisualClient.foliage(pos, callback.getReturnValue()));
    }
}
