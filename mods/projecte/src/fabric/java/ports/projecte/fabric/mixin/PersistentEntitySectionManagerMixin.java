/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.fabric.EntityJoinGuard;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.event.entity.EntityJoinLevelEvent;

/**
 * Forge posts {@link EntityJoinLevelEvent} before an entity is added, from disk or fresh, and skips the add when a
 * listener cancels it. A player is posted earlier, from {@code ServerLevel.addPlayer}, and this add is then skipped.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin {

    @Inject(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z", at = @At("HEAD"), cancellable = true)
    private void projecte$join(EntityAccess entity, boolean loadedFromDisk, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(EntityJoinGuard.SKIP.get())) {
            return;
        }
        if (entity instanceof Entity real && MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(real, real.level, loadedFromDisk))) {
            cir.setReturnValue(false);
        }
    }
}
