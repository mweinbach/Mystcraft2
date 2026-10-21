/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.material.FogType;
import ports.mekanism.lib.eventbus.api.Cancelable;
import ports.mekanism.lib.eventbus.api.Event;

public abstract class ViewportEvent extends Event {

    private final GameRenderer renderer;
    private final Camera camera;
    private final double partialTick;

    public ViewportEvent(GameRenderer renderer, Camera camera, double partialTick) {
        this.renderer = renderer;
        this.camera = camera;
        this.partialTick = partialTick;
    }

    public GameRenderer getRenderer() {
        return renderer;
    }

    public Camera getCamera() {
        return camera;
    }

    public double getPartialTick() {
        return partialTick;
    }

    /**
     * Fired after vanilla set up fog. The new distances and shape only apply if the event is canceled.
     */
    @Cancelable
    public static class RenderFog extends ViewportEvent {

        private final FogRenderer.FogMode mode;
        private final FogType type;
        private float farPlaneDistance;
        private float nearPlaneDistance;
        private FogShape fogShape;

        public RenderFog(FogRenderer.FogMode mode, FogType type, Camera camera, float partialTicks, float nearPlaneDistance, float farPlaneDistance, FogShape fogShape) {
            super(Minecraft.getInstance().gameRenderer, camera, partialTicks);
            this.mode = mode;
            this.type = type;
            this.nearPlaneDistance = nearPlaneDistance;
            this.farPlaneDistance = farPlaneDistance;
            this.fogShape = fogShape;
        }

        public FogRenderer.FogMode getMode() {
            return mode;
        }

        public FogType getType() {
            return type;
        }

        public float getFarPlaneDistance() {
            return farPlaneDistance;
        }

        public float getNearPlaneDistance() {
            return nearPlaneDistance;
        }

        public FogShape getFogShape() {
            return fogShape;
        }

        public void setFarPlaneDistance(float distance) {
            farPlaneDistance = distance;
        }

        public void setNearPlaneDistance(float distance) {
            nearPlaneDistance = distance;
        }

        public void setFogShape(FogShape shape) {
            fogShape = shape;
        }

        public void scaleFarPlaneDistance(float factor) {
            farPlaneDistance *= factor;
        }

        public void scaleNearPlaneDistance(float factor) {
            nearPlaneDistance *= factor;
        }
    }

    /**
     * Fired after vanilla computed the fog color, before it is applied.
     */
    public static class ComputeFogColor extends ViewportEvent {

        private float red;
        private float green;
        private float blue;

        public ComputeFogColor(Camera camera, float partialTicks, float red, float green, float blue) {
            super(Minecraft.getInstance().gameRenderer, camera, partialTicks);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public float getRed() {
            return red;
        }

        public void setRed(float red) {
            this.red = red;
        }

        public float getGreen() {
            return green;
        }

        public void setGreen(float green) {
            this.green = green;
        }

        public float getBlue() {
            return blue;
        }

        public void setBlue(float blue) {
            this.blue = blue;
        }
    }
}
