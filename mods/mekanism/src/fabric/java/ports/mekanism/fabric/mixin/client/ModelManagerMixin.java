/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.extensions.IForgeModelManager;

/**
 * Keeps the model bakery of the last resource reload, which Forge's model manager exposes.
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerMixin implements IForgeModelManager {

    @Unique
    private ModelBakery mekanism$modelBakery;

    @Override
    public ModelBakery getModelBakery() {
        return mekanism$modelBakery;
    }

    @Inject(method = "apply(Lnet/minecraft/client/resources/model/ModelBakery;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
          at = @At("HEAD"))
    private void mekanism$keepBakery(ModelBakery modelBakery, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        mekanism$modelBakery = modelBakery;
    }
}
