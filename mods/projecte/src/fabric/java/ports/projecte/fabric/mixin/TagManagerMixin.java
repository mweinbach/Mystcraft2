/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.projecte.fabric.FabricPlatform;

/**
 * Reads the tags of the registries Mekanism creates from Forge's {@code tags/<namespace>/<path>} directory.
 */
@Mixin(TagManager.class)
public abstract class TagManagerMixin {

    @Inject(method = "getTagDir", at = @At("HEAD"), cancellable = true)
    private static void projecte$forgeTagDirectory(ResourceKey<? extends Registry<?>> registry, CallbackInfoReturnable<String> cir) {
        if (FabricPlatform.usesForgeTagDirectory(registry)) {
            cir.setReturnValue("tags/" + registry.location().getNamespace() + "/" + registry.location().getPath());
        }
    }
}
