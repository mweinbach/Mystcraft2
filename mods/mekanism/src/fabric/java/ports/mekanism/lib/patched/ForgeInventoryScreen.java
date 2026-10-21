/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.patched;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;

/**
 * Forge's {@code InventoryScreen.renderEntityInInventoryRaw}: vanilla's inventory entity preview, turned by angle
 * components the caller gives instead of ones derived from the mouse position.
 */
public final class ForgeInventoryScreen {

    private ForgeInventoryScreen() {
    }

    public static void renderEntityInInventoryRaw(int x, int y, int scale, float angleXComponent, float angleYComponent, LivingEntity entity) {
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.translate(x, y, 1050);
        modelView.scale(1, 1, -1);
        RenderSystem.applyModelViewMatrix();
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0, 0, 1000);
        poseStack.scale(scale, scale, scale);
        Quaternion rotation = Vector3f.ZP.rotationDegrees(180);
        Quaternion tilt = Vector3f.XP.rotationDegrees(angleYComponent * 20);
        rotation.mul(tilt);
        poseStack.mulPose(rotation);
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180 + angleXComponent * 20;
        entity.setYRot(180 + angleXComponent * 40);
        entity.setXRot(-angleYComponent * 20);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        tilt.conj();
        dispatcher.overrideCameraOrientation(tilt);
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 1, poseStack, buffers, LightTexture.FULL_BRIGHT));
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
