/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.shaders.Program;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Resolves namespaced core shader names ({@code mekanism:rendertype_flame}) and program names under their own
 * namespace, {@code mekanism:shaders/core/...}, as Forge does. Plain names keep vanilla's paths.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {

    private static final String CORE_PATH = "shaders/core/";

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V", ordinal = 0))
    private String mekanism$namespacedDefinition(String location) {
        return mekanism$namespaced(location);
    }

    @ModifyVariable(method = "getOrCreate", at = @At("STORE"), ordinal = 1)
    private static String mekanism$namespacedProgram(String location, ResourceProvider resources, Program.Type type, String name) {
        return mekanism$namespaced(location);
    }

    private static String mekanism$namespaced(String location) {
        int separator = location.indexOf(':');
        if (!location.startsWith(CORE_PATH) || separator < 0) {
            return location;
        }
        String namespace = location.substring(CORE_PATH.length(), separator);
        return namespace + ":" + CORE_PATH + location.substring(separator + 1);
    }
}
