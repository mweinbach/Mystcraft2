package net.mystcraft.fabric.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The adapter retains ItemEntity transport, but classic living books were not loose hopper items. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBookPickupMixin {
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void mystcraft$ignorePhysicalBook(Container target, ItemEntity entity,
                                                    CallbackInfoReturnable<Boolean> result) {
        if (entity instanceof LinkBookEntity) result.setReturnValue(false);
    }
}
