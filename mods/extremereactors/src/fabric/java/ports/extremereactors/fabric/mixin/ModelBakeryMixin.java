/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.mixin;

import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.extremereactors.fabric.client.FabricClientPlatform;

/**
 * Fabric API 0.77 has no hook to replace baked models, which our dynamic block models need; this is the moment
 * Forge fires its model baking completed event at.
 */
@Mixin(ModelBakery.class)
abstract class ModelBakeryMixin {

    @Inject(method = "uploadTextures", at = @At("RETURN"))
    private void bigreactors$onModelsBaked(final TextureManager textureManager, final ProfilerFiller profiler,
                                           final CallbackInfoReturnable<AtlasSet> callback) {
        FabricClientPlatform.fireModelBakingCompleted(((ModelBakery) (Object) this).getBakedTopLevelModels());
    }
}
