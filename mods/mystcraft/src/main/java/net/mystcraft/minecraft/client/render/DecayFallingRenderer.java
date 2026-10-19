package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.instability.DecayFallingBlockEntity;

/** Generic adapter retains the vanilla falling block mesh and texture for source black decay. */
public final class DecayFallingRenderer extends EntityRenderer<DecayFallingBlockEntity> {
    private final FallingBlockRenderer delegate;
    public DecayFallingRenderer(EntityRendererProvider.Context context) { super(context); delegate = new FallingBlockRenderer(context); }
    @Override public void render(DecayFallingBlockEntity entity, float yaw, float delta, PoseStack pose, MultiBufferSource buffers, int light) {
        delegate.render(entity, yaw, delta, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(DecayFallingBlockEntity entity) { return delegate.getTextureLocation(entity); }
}
