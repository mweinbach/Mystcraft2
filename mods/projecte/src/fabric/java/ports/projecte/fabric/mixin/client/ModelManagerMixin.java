/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.fabric.FabricLifecycle;
import ports.projecte.lib.client.event.ModelEvent;
import ports.projecte.lib.client.extensions.IForgeModelManager;
import ports.projecte.lib.client.model.ExtendedBlockModelJson;

/**
 * Keeps the model bakery of the last resource reload, which Forge's model manager exposes, and fires Forge's
 * baking completed event before the model shapers cache the baked models.
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerMixin implements IForgeModelManager {

    @Shadow
    private Map<ResourceLocation, BakedModel> bakedRegistry;

    @Unique
    private ModelBakery projecte$modelBakery;

    @Override
    public ModelBakery getModelBakery() {
        return projecte$modelBakery;
    }

    @Inject(method = "apply(Lnet/minecraft/client/resources/model/ModelBakery;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
          at = @At("HEAD"))
    private void projecte$keepBakery(ModelBakery modelBakery, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        projecte$modelBakery = modelBakery;
    }

    @Inject(method = "apply(Lnet/minecraft/client/resources/model/ModelBakery;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;rebuildCache()V"))
    private void projecte$bakingCompleted(ModelBakery modelBakery, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        Set<String> namespaces = new HashSet<>();
        FabricLifecycle.forEachMod(container -> namespaces.add(container.getNamespace()));
        ExtendedBlockModelJson.applyBlockRenderTypes(bakedRegistry, namespaces);
        FabricLifecycle.postToMods(new ModelEvent.BakingCompleted((ModelManager) (Object) this, bakedRegistry, modelBakery));
    }
}
