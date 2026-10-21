/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import java.lang.reflect.Type;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.client.model.ExtendedBlockModelJson;

/**
 * Reads the model JSON keys Forge adds, as Forge's extended block model deserializer does.
 */
@Mixin(BlockModel.Deserializer.class)
public abstract class BlockModelDeserializerMixin {

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;",
          at = @At("RETURN"))
    private void mekanism$readForgeKeys(JsonElement json, Type type, JsonDeserializationContext context, CallbackInfoReturnable<BlockModel> cir) {
        ExtendedBlockModelJson.read(cir.getReturnValue(), json.getAsJsonObject(), context);
    }
}
