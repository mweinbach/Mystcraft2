/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.fabric.client.EmissiveBlockElementFace;

/**
 * Writes a face's emissivity into the quad lightmap, as Forge's {@code QuadTransformers.settingEmissivity} does after
 * {@code FaceBakery.bakeQuad}. Fabric's renderer keeps the brighter of that light and the world light.
 */
@Mixin(FaceBakery.class)
public abstract class FaceBakeryMixin {

    private static final int mekanism$LIGHT_OFFSET = 6;
    private static final int mekanism$VERTEX_STRIDE = 8;

    @Inject(method = "bakeQuad(Lcom/mojang/math/Vector3f;Lcom/mojang/math/Vector3f;Lnet/minecraft/client/renderer/block/model/BlockElementFace;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/core/Direction;Lnet/minecraft/client/resources/model/ModelState;Lnet/minecraft/client/renderer/block/model/BlockElementRotation;ZLnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/block/model/BakedQuad;",
          at = @At("RETURN"))
    private void mekanism$applyEmissivity(Vector3f from, Vector3f to, BlockElementFace face, TextureAtlasSprite sprite, Direction direction, ModelState state,
          @Nullable BlockElementRotation rotation, boolean shade, ResourceLocation modelLocation, CallbackInfoReturnable<BakedQuad> cir) {
        int emissivity = ((EmissiveBlockElementFace) face).mekanism$emissivity();
        if (emissivity <= 0) {
            return;
        }
        int light = LightTexture.pack(emissivity, emissivity);
        int[] vertices = cir.getReturnValue().getVertices();
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * mekanism$VERTEX_STRIDE + mekanism$LIGHT_OFFSET] = light;
        }
    }
}
