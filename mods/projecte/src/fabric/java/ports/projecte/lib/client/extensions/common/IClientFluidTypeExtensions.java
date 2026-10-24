/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions.common;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.fluids.FluidStack;
import ports.projecte.lib.fluids.FluidType;
import ports.projecte.lib.fluids.FluidTypes;

/**
 * How a fluid type renders. Types that do not define their own, including vanilla's and other Fabric mods' fluids,
 * are described by Fabric's fluid render handlers.
 */
public interface IClientFluidTypeExtensions {

    IClientFluidTypeExtensions DEFAULT = new IClientFluidTypeExtensions() {
    };

    static IClientFluidTypeExtensions of(FluidState state) {
        return of(state.getType());
    }

    static IClientFluidTypeExtensions of(Fluid fluid) {
        FluidType type = fluid.getFluidType();
        IClientFluidTypeExtensions own = ownExtensions(type);
        return own == null ? new FabricFluidRenderExtensions(fluid) : own;
    }

    static IClientFluidTypeExtensions of(FluidType type) {
        IClientFluidTypeExtensions own = ownExtensions(type);
        return own == null ? new FabricFluidRenderExtensions(FluidTypes.fluidOf(type)) : own;
    }

    @Nullable
    private static IClientFluidTypeExtensions ownExtensions(FluidType type) {
        type.initializeClientOnce();
        return type.getRenderPropertiesInternal() instanceof IClientFluidTypeExtensions extensions && extensions != DEFAULT ? extensions : null;
    }

    default int getTintColor() {
        return 0xFFFFFFFF;
    }

    @Nullable
    default ResourceLocation getStillTexture() {
        return null;
    }

    @Nullable
    default ResourceLocation getFlowingTexture() {
        return null;
    }

    @Nullable
    default ResourceLocation getOverlayTexture() {
        return null;
    }

    default Stream<ResourceLocation> getTextures() {
        return Stream.of(getStillTexture(), getFlowingTexture(), getOverlayTexture()).filter(Objects::nonNull);
    }

    /**
     * The texture drawn over the screen while the camera is inside the fluid.
     */
    @Nullable
    default ResourceLocation getRenderOverlayTexture(Minecraft mc) {
        return null;
    }

    default void renderOverlay(Minecraft mc, PoseStack poseStack) {
        ResourceLocation texture = getRenderOverlayTexture(mc);
        if (texture == null || mc.player == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableTexture();
        RenderSystem.setShaderTexture(0, texture);
        BlockPos eyePos = new BlockPos(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        float brightness = LightTexture.getBrightness(mc.player.level.dimensionType(), mc.player.level.getMaxLocalRawBrightness(eyePos));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(brightness, brightness, brightness, 0.1F);
        float uOffset = -mc.player.getYRot() / 64.0F;
        float vOffset = mc.player.getXRot() / 64.0F;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, -1.0F, -1.0F, -0.5F).uv(4.0F + uOffset, 4.0F + vOffset).endVertex();
        builder.vertex(matrix, 1.0F, -1.0F, -0.5F).uv(uOffset, 4.0F + vOffset).endVertex();
        builder.vertex(matrix, 1.0F, 1.0F, -0.5F).uv(uOffset, vOffset).endVertex();
        builder.vertex(matrix, -1.0F, 1.0F, -0.5F).uv(4.0F + uOffset, vOffset).endVertex();
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    default Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
        return fluidFogColor;
    }

    default void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance,
          FogShape shape) {
    }

    @Nullable
    default ResourceLocation getStillTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getStillTexture();
    }

    @Nullable
    default ResourceLocation getFlowingTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getFlowingTexture();
    }

    @Nullable
    default ResourceLocation getOverlayTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getOverlayTexture();
    }

    default int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getTintColor();
    }

    default int getTintColor(FluidStack stack) {
        return getTintColor();
    }

    @Nullable
    default ResourceLocation getStillTexture(FluidStack stack) {
        return getStillTexture();
    }

    @Nullable
    default ResourceLocation getFlowingTexture(FluidStack stack) {
        return getFlowingTexture();
    }

    @Nullable
    default ResourceLocation getOverlayTexture(FluidStack stack) {
        return getOverlayTexture();
    }
}
