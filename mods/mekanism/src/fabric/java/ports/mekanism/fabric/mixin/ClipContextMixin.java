/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forge 43.5.0's {@code ClipContext} constructor uses {@link CollisionContext#empty()} when the entity is null.
 * Vanilla calls {@code CollisionContext.of(null)}, and {@code EntityCollisionContext} then crashes. The laser passes
 * a null entity.
 */
@Mixin(ClipContext.class)
public abstract class ClipContextMixin {

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"))
    private CollisionContext mekanism$emptyWithoutEntity(Entity entity) {
        return entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
    }
}
