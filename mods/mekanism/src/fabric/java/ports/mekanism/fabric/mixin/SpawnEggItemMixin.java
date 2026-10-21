/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.ForgeSpawnEggItem;

/**
 * Vanilla records the egg's entity type in the constructor. A Forge egg is built before that type exists, so the
 * null key is dropped and later lookups use the supplier.
 */
@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {

    @Shadow
    @Final
    private static Map<EntityType<? extends Mob>, SpawnEggItem> BY_ID;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mekanism$dropNullEgg(EntityType<? extends Mob> type, int background, int highlight, Item.Properties properties, CallbackInfo ci) {
        if (type == null) {
            BY_ID.remove(null);
        }
    }

    @Inject(method = "byId", at = @At("HEAD"), cancellable = true)
    private static void mekanism$forgeEgg(EntityType<?> type, CallbackInfoReturnable<SpawnEggItem> cir) {
        SpawnEggItem egg = ForgeSpawnEggItem.byId(type);
        if (egg != null) {
            cir.setReturnValue(egg);
        }
    }

    @Inject(method = "getType", at = @At("RETURN"), cancellable = true)
    private void mekanism$deferredType(CompoundTag tag, CallbackInfoReturnable<EntityType<?>> cir) {
        if (cir.getReturnValue() == null && (Object) this instanceof ForgeSpawnEggItem egg) {
            EntityType<?> resolved = egg.getDefaultType();
            if (resolved != null) {
                cir.setReturnValue(resolved);
            }
        }
    }
}
