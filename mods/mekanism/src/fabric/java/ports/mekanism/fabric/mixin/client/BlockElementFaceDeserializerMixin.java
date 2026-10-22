/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.fabric.client.EmissiveBlockElementFace;

/**
 * Reads the {@code emissivity} key Forge's face deserializer reads. A value outside 0–15 fails the model load.
 * The deserializer is a protected nested class, so the mixin names it rather than referencing the class token.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElementFace$Deserializer")
public abstract class BlockElementFaceDeserializerMixin {

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockElementFace;",
          at = @At("RETURN"))
    private void mekanism$readEmissivity(JsonElement json, Type type, JsonDeserializationContext context, CallbackInfoReturnable<BlockElementFace> cir) {
        JsonObject object = json.getAsJsonObject();
        int emissivity = GsonHelper.getAsInt(object, "emissivity", 0);
        if (emissivity != Mth.clamp(emissivity, 0, 15)) {
            throw new JsonParseException("The emissivity value must be between 0 and 15. Found: " + emissivity);
        }
        ((EmissiveBlockElementFace) cir.getReturnValue()).mekanism$setEmissivity(emissivity);
    }
}
