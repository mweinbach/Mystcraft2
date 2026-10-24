package ports.projecte.fabric.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge saves a furnace's burn time as an int; vanilla saves a short. Aeternalis Fuel (102,400 ticks) and the three fuel
 * blocks (57,600 to 921,600) burn longer than a short holds, so on Fabric a saved furnace would come back with a wrapped,
 * negative burn time. Only burn times a short cannot hold are written as an int, so every other furnace saves exactly as
 * vanilla does, and other mods' injections into these methods still run.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBurnTimeMixin {

	@Shadow
	int litTime;

	@Inject(method = "saveAdditional", at = @At("TAIL"))
	private void projecte$saveLongBurnTime(CompoundTag tag, CallbackInfo ci) {
		if (litTime > Short.MAX_VALUE) {
			tag.putInt("BurnTime", litTime);
		}
	}

	@Inject(method = "load", at = @At("TAIL"))
	private void projecte$loadLongBurnTime(CompoundTag tag, CallbackInfo ci) {
		if (tag.contains("BurnTime", Tag.TAG_INT)) {
			litTime = tag.getInt("BurnTime");
		}
	}
}
