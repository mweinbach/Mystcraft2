/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.event.RenderHighlightEvent;
import ports.mekanism.lib.client.event.sound.PlaySoundEvent;
import ports.mekanism.lib.common.MinecraftForge;

public final class ForgeHooksClient {

    private ForgeHooksClient() {
    }

    /**
     * Posts the highlight event for a target.
     *
     * @return {@code true} if the event was canceled and vanilla's outline should be skipped
     */
    public static boolean onDrawHighlight(LevelRenderer context, Camera camera, HitResult target, float partialTick, PoseStack poseStack,
          MultiBufferSource bufferSource) {
        if (target instanceof BlockHitResult blockTarget && target.getType() == HitResult.Type.BLOCK) {
            return MinecraftForge.EVENT_BUS.post(new RenderHighlightEvent.Block(context, camera, blockTarget, partialTick, poseStack, bufferSource));
        } else if (target instanceof EntityHitResult entityTarget && target.getType() == HitResult.Type.ENTITY) {
            return MinecraftForge.EVENT_BUS.post(new RenderHighlightEvent.Entity(context, camera, entityTarget, partialTick, poseStack, bufferSource));
        }
        return false;
    }

    /**
     * Posts the play sound event.
     *
     * @return the sound to play instead, or {@code null} to play nothing
     */
    @Nullable
    public static SoundInstance playSound(SoundEngine manager, SoundInstance sound) {
        PlaySoundEvent event = new PlaySoundEvent(manager, sound);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getSound();
    }
}
