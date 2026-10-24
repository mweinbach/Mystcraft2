/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.client.extensions.IForgeBlockModel;
import ports.mekanism.lib.client.model.ExtendedBlockModelJson;
import ports.mekanism.lib.client.model.SimpleModelState;
import ports.mekanism.lib.client.model.geometry.BlockGeometryBakingContext;

/**
 * Gives every block model the Forge geometry context Forge keeps in {@code BlockModel.customData}, and bakes and
 * collects the textures of models with custom geometry through it.
 */
@Mixin(BlockModel.class)
public abstract class BlockModelMixin implements IForgeBlockModel {

    private static final String BAKE = "bake(Lnet/minecraft/client/resources/model/ModelBakery;Lnet/minecraft/client/renderer/block/model/BlockModel;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/resources/model/BakedModel;";

    @Unique
    @Nullable
    private BlockGeometryBakingContext mekanism$customData;

    @Shadow
    public abstract ItemOverrides getItemOverrides(ModelBakery modelBakery, BlockModel blockModel);

    @Override
    public BlockGeometryBakingContext getCustomData() {
        if (mekanism$customData == null) {
            mekanism$customData = new BlockGeometryBakingContext((BlockModel) (Object) this);
        }
        return mekanism$customData;
    }

    @Inject(method = BAKE, at = @At("HEAD"), cancellable = true)
    private void mekanism$bakeCustomGeometry(ModelBakery bakery, BlockModel owner, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ResourceLocation modelLocation, boolean guiLight3d, CallbackInfoReturnable<BakedModel> cir) {
        BlockGeometryBakingContext customData = getCustomData();
        if (customData.hasCustomGeometry()) {
            BakedModel baked = customData.bake(bakery, spriteGetter, modelState, getItemOverrides(bakery, owner), modelLocation);
            ExtendedBlockModelJson.recordRenderType(baked, customData);
            cir.setReturnValue(baked);
        }
    }

    @ModifyVariable(method = BAKE, at = @At("HEAD"), argsOnly = true)
    private ModelState mekanism$applyRootTransform(ModelState modelState) {
        var rootTransform = getCustomData().getRootTransform();
        if (rootTransform.mekanism$isIdentity() || getCustomData().hasCustomGeometry()) {
            return modelState;
        }
        return new SimpleModelState(modelState.getRotation().compose(rootTransform), modelState.isUvLocked());
    }

    @Inject(method = BAKE, at = @At("RETURN"))
    private void mekanism$recordRenderType(ModelBakery bakery, BlockModel owner, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ResourceLocation modelLocation, boolean guiLight3d, CallbackInfoReturnable<BakedModel> cir) {
        ExtendedBlockModelJson.recordRenderType(cir.getReturnValue(), getCustomData());
    }

    @Inject(method = "getMaterials", at = @At("RETURN"))
    private void mekanism$customGeometryMaterials(Function<ResourceLocation, UnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors,
          CallbackInfoReturnable<Collection<Material>> cir) {
        BlockGeometryBakingContext customData = getCustomData();
        if (customData.hasCustomGeometry()) {
            cir.getReturnValue().addAll(customData.getTextureDependencies(modelGetter, missingTextureErrors));
        }
    }
}
