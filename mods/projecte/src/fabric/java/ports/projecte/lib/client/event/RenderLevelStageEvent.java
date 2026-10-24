/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.eventbus.api.Event;

/**
 * Fired at fixed points while the level renders: after each terrain layer, after particles and after weather.
 */
public class RenderLevelStageEvent extends Event {

    private final Stage stage;
    private final LevelRenderer levelRenderer;
    private final PoseStack poseStack;
    private final Matrix4f projectionMatrix;
    private final int renderTick;
    private final float partialTick;
    private final Camera camera;
    private final Frustum frustum;

    public RenderLevelStageEvent(Stage stage, LevelRenderer levelRenderer, PoseStack poseStack, Matrix4f projectionMatrix, int renderTick, float partialTick,
          Camera camera, Frustum frustum) {
        this.stage = stage;
        this.levelRenderer = levelRenderer;
        this.poseStack = poseStack;
        this.projectionMatrix = projectionMatrix;
        this.renderTick = renderTick;
        this.partialTick = partialTick;
        this.camera = camera;
        this.frustum = frustum;
    }

    public Stage getStage() {
        return stage;
    }

    public LevelRenderer getLevelRenderer() {
        return levelRenderer;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public int getRenderTick() {
        return renderTick;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public Camera getCamera() {
        return camera;
    }

    public Frustum getFrustum() {
        return frustum;
    }

    public static final class Stage {

        private static final Map<RenderType, Stage> BY_RENDER_TYPE = new HashMap<>();

        public static final Stage AFTER_SKY = new Stage("after_sky", null);
        public static final Stage AFTER_SOLID_BLOCKS = new Stage("after_solid_blocks", RenderType.solid());
        public static final Stage AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS = new Stage("after_cutout_mipped_blocks", RenderType.cutoutMipped());
        public static final Stage AFTER_CUTOUT_BLOCKS = new Stage("after_cutout_blocks", RenderType.cutout());
        public static final Stage AFTER_TRANSLUCENT_BLOCKS = new Stage("after_translucent_blocks", RenderType.translucent());
        public static final Stage AFTER_TRIPWIRE_BLOCKS = new Stage("after_tripwire_blocks", RenderType.tripwire());
        public static final Stage AFTER_PARTICLES = new Stage("after_particles", null);
        public static final Stage AFTER_WEATHER = new Stage("after_weather", null);

        private final String name;

        private Stage(String name, @Nullable RenderType renderType) {
            this.name = name;
            if (renderType != null) {
                BY_RENDER_TYPE.put(renderType, this);
            }
        }

        @Override
        public String toString() {
            return name;
        }

        @Nullable
        public static Stage fromRenderType(RenderType renderType) {
            return BY_RENDER_TYPE.get(renderType);
        }
    }
}
