package ports.buildcraft.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.buildcraft.core.MapLocationItem;

/** Upstream {@code ItemMapLocation.getItemStackLimit}: a clean map stacks to 16 and a recorded one does not stack. */
@Mixin(ItemStack.class)
public abstract class ItemStackLimitMixin {
    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void buildcraft$mapLocationLimit(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof MapLocationItem) callback.setReturnValue(MapLocationItem.maxStack(stack));
    }
}
