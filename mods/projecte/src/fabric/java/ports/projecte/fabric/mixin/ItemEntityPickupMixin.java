/*
 * ProjectE 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.event.entity.player.EntityItemPickupEvent;

/** Posts Forge's {@code EntityItemPickupEvent} when a player could pick an item up; canceling it skips vanilla's pickup. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {

    @Shadow
    private int pickupDelay;

    @Shadow
    @Nullable
    private UUID owner;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void projecte$pickup(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level.isClientSide || pickupDelay != 0 || owner != null && !owner.equals(player.getUUID()) || self.getItem().isEmpty()) {
            return;
        }
        if (MinecraftForge.EVENT_BUS.post(new EntityItemPickupEvent(player, self))) {
            ci.cancel();
        }
    }
}
