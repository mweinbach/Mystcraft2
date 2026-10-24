package com.agricraft.agricraft.mixin.fabric;

import com.agricraft.agricraft.client.SeedBagSorterInput;
import com.agricraft.agricraft.common.item.SeedBagItem;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MouseHandler;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

	@Shadow private double accumulatedScroll;

	@Inject(method = "onScroll(JDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;swapPaint(D)V"), cancellable = true)
	private void onMouseScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
		if (SeedBagSorterInput.onScroll((int) this.accumulatedScroll, sorter -> {
			if (!ClientPlayNetworking.canSend(SeedBagItem.SORTER_CHANNEL)) {
				return;
			}
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeVarInt(sorter);
			ClientPlayNetworking.send(SeedBagItem.SORTER_CHANNEL, buf);
		})) {
			ci.cancel();
		}
	}
}
