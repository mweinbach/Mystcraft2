/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.google.gson.JsonElement;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.crafting.CraftingHelper;
import ports.mekanism.lib.common.extensions.IForgeIngredient;

/**
 * Reads Forge's ingredient types wherever vanilla reads an ingredient, as Forge's patched {@code fromJson} does.
 */
@Mixin(Ingredient.class)
public abstract class IngredientMixin implements IForgeIngredient {

    @Inject(method = "fromJson", at = @At("HEAD"), cancellable = true)
    private static void mekanism$readForgeIngredient(@Nullable JsonElement json, CallbackInfoReturnable<Ingredient> cir) {
        Ingredient custom = CraftingHelper.getCustomIngredient(json);
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
