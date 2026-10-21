/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Function;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.patched.ForgeItemPredicates;

/**
 * Reads item predicates with a {@code type} through the deserializer registered for it, like Forge.
 */
@Mixin(ItemPredicate.class)
public abstract class ItemPredicateMixin {

    @Inject(method = "fromJson", at = @At("HEAD"), cancellable = true)
    private static void mekanism$customPredicate(JsonElement json, CallbackInfoReturnable<ItemPredicate> cir) {
        if (json != null && json.isJsonObject() && json.getAsJsonObject().has("type")) {
            JsonObject object = json.getAsJsonObject();
            ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(object, "type"));
            Function<JsonObject, ItemPredicate> deserializer = ForgeItemPredicates.get(type);
            if (deserializer == null) {
                throw new IllegalArgumentException("There is no ItemPredicate of type " + type);
            }
            cir.setReturnValue(deserializer.apply(object));
        }
    }
}
