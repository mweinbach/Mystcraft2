/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge 43.5.0's {@code ClipContext} constructor uses {@link CollisionContext#empty()} when the entity is null.
 * Vanilla calls {@code CollisionContext.of(null)}, and {@code EntityCollisionContext} then crashes. The laser passes
 * a null entity. Porting Lib redirects the same call, and Mixin allows one redirect per call, so this wraps it instead.
 */
@Mixin(ClipContext.class)
public abstract class ClipContextMixin {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"))
    private CollisionContext mekanism$emptyWithoutEntity(Entity entity, Operation<CollisionContext> original) {
        return entity == null ? CollisionContext.empty() : original.call(entity);
    }
}
