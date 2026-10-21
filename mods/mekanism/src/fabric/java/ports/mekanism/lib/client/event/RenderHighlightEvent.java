/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import ports.mekanism.lib.eventbus.api.Cancelable;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired before the outline of the targeted block or entity is drawn. Canceling it skips vanilla's outline.
 */
@Cancelable
public abstract class RenderHighlightEvent extends Event {

    private final LevelRenderer levelRenderer;
    private final Camera camera;
    private final HitResult target;
    private final float partialTick;
    private final PoseStack poseStack;
    private final MultiBufferSource multiBufferSource;

    protected RenderHighlightEvent(LevelRenderer levelRenderer, Camera camera, HitResult target, float partialTick, PoseStack poseStack,
          MultiBufferSource multiBufferSource) {
        this.levelRenderer = levelRenderer;
        this.camera = camera;
        this.target = target;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.multiBufferSource = multiBufferSource;
    }

    public LevelRenderer getLevelRenderer() {
        return levelRenderer;
    }

    public Camera getCamera() {
        return camera;
    }

    public HitResult getTarget() {
        return target;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public MultiBufferSource getMultiBufferSource() {
        return multiBufferSource;
    }

    @Cancelable
    public static class Block extends RenderHighlightEvent {

        public Block(LevelRenderer levelRenderer, Camera camera, BlockHitResult target, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
            super(levelRenderer, camera, target, partialTick, poseStack, bufferSource);
        }

        @Override
        public BlockHitResult getTarget() {
            return (BlockHitResult) super.getTarget();
        }
    }

    @Cancelable
    public static class Entity extends RenderHighlightEvent {

        public Entity(LevelRenderer levelRenderer, Camera camera, EntityHitResult target, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
            super(levelRenderer, camera, target, partialTick, poseStack, bufferSource);
        }

        @Override
        public EntityHitResult getTarget() {
            return (EntityHitResult) super.getTarget();
        }
    }
}
