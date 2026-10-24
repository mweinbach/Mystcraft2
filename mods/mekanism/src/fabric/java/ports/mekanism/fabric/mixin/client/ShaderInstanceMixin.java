/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Resolves a namespaced core shader name ({@code mekanism:rendertype_flame}) to
 * {@code mekanism:shaders/core/rendertype_flame.json}, as Forge does. Fabric API already resolves namespaced program
 * names, but the definition only for its own shader subclass.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {

    private static final String mekanism$CORE_PATH = "shaders/core/";

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V", ordinal = 0))
    private String mekanism$namespacedDefinition(String location) {
        int separator = location.indexOf(':');
        if (!location.startsWith(mekanism$CORE_PATH) || separator < 0) {
            return location;
        }
        String namespace = location.substring(mekanism$CORE_PATH.length(), separator);
        return namespace + ":" + mekanism$CORE_PATH + location.substring(separator + 1);
    }
}
