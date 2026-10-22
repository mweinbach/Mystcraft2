/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.google.gson.JsonElement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import ports.mekanism.lib.common.crafting.CraftingHelper;
import ports.mekanism.lib.common.extensions.IForgeIngredient;

/**
 * Reads and writes Forge's ingredient types wherever vanilla reads an ingredient. A leading varint of {@code -1} marks
 * a custom type on the network; anything else is rewound so vanilla's item list reader runs. Fabric's recipe API uses
 * that same marker and rejects an id it does not know, so this reader is ordered first and only keeps ids this port
 * registered. An unknown id is rewound for Fabric's reader.
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

    @Inject(method = "fromNetwork", at = @At("HEAD"), cancellable = true, order = InjectionInfo.InjectorOrder.EARLY)
    private static void mekanism$readForgeIngredient(FriendlyByteBuf buffer, CallbackInfoReturnable<Ingredient> cir) {
        buffer.markReaderIndex();
        if (buffer.readVarInt() != -1) {
            buffer.resetReaderIndex();
            return;
        }
        ResourceLocation type = buffer.readResourceLocation();
        if (!CraftingHelper.hasIngredient(type)) {
            buffer.resetReaderIndex();
            return;
        }
        cir.setReturnValue(CraftingHelper.getIngredient(type, buffer));
    }

    @Inject(method = "toNetwork", at = @At("HEAD"), cancellable = true)
    private void mekanism$writeForgeIngredient(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!isVanilla()) {
            CraftingHelper.write(buffer, (Ingredient) (Object) this);
            ci.cancel();
        }
    }
}
