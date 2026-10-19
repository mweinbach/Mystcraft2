package net.mystcraft.fabric.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.mystcraft.minecraft.item.LinkingBookItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric counterpart of Forge's Item#createEntity hook, including player Q drops. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void mystcraft$replaceDroppedBook(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!(entity instanceof ItemEntity item) || entity instanceof LinkBookEntity
                || !(item.getItem().getItem() instanceof LinkingBookItem book)) return;
        ServerLevel level = (ServerLevel) (Object) this;
        Entity replacement = book.createEntity(level, entity, item.getItem());
        if (replacement == null) return;
        boolean added = level.addFreshEntity(replacement);
        if (added) entity.discard();
        callback.setReturnValue(added);
    }
}
