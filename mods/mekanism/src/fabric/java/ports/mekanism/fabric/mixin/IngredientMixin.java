/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import ports.mekanism.lib.common.crafting.CraftingHelper;
import ports.mekanism.lib.common.extensions.IForgeIngredient;

/**
 * Reads and writes Forge's ingredient types wherever vanilla reads an ingredient. A leading varint of {@code -1} marks
 * a custom type on the network; anything else is rewound so vanilla's item list reader runs. Fabric's recipe API uses
 * that same marker and rejects an id it does not know, so this reader only keeps ids this port registered and rewinds
 * the rest for Fabric's reader.
 * <p>
 * Only the types registered with {@link CraftingHelper} are handled here; every other ingredient, type id or JSON
 * {@code type} goes to the original method, so other mods' injections still read and write their own.
 * <p>
 * The methods are wrapped rather than injected at HEAD, because a {@link WrapMethod} runs outside every other mod's
 * injection and the order of HEAD injections from different mods is not fixed. IngredientExtensionAPI, which
 * CraftTweaker for Fabric bundles, writes its own int marker at the head of {@code toNetwork} for every ingredient it
 * does not own and lets the method continue. When its hook ran first, this port's {@code -1} came after that int, this
 * reader rewound, and Fabric's reader rejected the Forge type id. Its {@code fromJson} hook also logged a warning for
 * every Forge type it saw first.
 */
@Mixin(Ingredient.class)
public abstract class IngredientMixin implements IForgeIngredient {

    @WrapMethod(method = "fromJson")
    private static Ingredient mekanism$readForgeIngredient(@Nullable JsonElement json, Operation<Ingredient> original) {
        Ingredient custom = CraftingHelper.getCustomIngredient(json);
        if (custom != null) {
            return custom;
        }
        Ingredient read = original.call(json);
        CraftingHelper.requireClaimedType(json, read);
        return read;
    }

    @WrapMethod(method = "fromNetwork")
    private static Ingredient mekanism$readForgeIngredient(FriendlyByteBuf buffer, Operation<Ingredient> original) {
        int start = buffer.readerIndex();
        if (buffer.readVarInt() == -1) {
            ResourceLocation type = buffer.readResourceLocation();
            if (CraftingHelper.hasIngredient(type)) {
                return CraftingHelper.getIngredient(type, buffer);
            }
        }
        buffer.readerIndex(start);
        return original.call(buffer);
    }

    @WrapMethod(method = "toNetwork")
    private void mekanism$writeForgeIngredient(FriendlyByteBuf buffer, Operation<Void> original) {
        Ingredient self = (Ingredient) (Object) this;
        if (CraftingHelper.writesIngredient(self)) {
            CraftingHelper.write(buffer, self);
        } else {
            original.call(buffer);
        }
    }
}
