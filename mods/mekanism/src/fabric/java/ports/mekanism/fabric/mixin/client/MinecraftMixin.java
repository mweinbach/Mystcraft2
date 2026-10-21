/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.fabric.FabricGameEvents;
import ports.mekanism.fabric.client.FabricClientMod;
import ports.mekanism.lib.event.TickEvent;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    private float pausePartialTick;

    @Unique
    private float mekanism$renderPartialTick;

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"))
    private void mekanism$renderTickStart(boolean renderLevel, CallbackInfo ci) {
        // Forge caches this partial tick and posts the render tick around gameRenderer.render and the toast pass.
        Minecraft minecraft = (Minecraft) (Object) this;
        this.mekanism$renderPartialTick = minecraft.isPaused() ? this.pausePartialTick : minecraft.getFrameTime();
        FabricGameEvents.post(new TickEvent.RenderTickEvent(TickEvent.Phase.START, this.mekanism$renderPartialTick));
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void mekanism$renderTickEnd(boolean renderLevel, CallbackInfo ci) {
        FabricGameEvents.post(new TickEvent.RenderTickEvent(TickEvent.Phase.END, this.mekanism$renderPartialTick));
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
          target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;"))
    private void mekanism$beforeInitialReload(GameConfig gameConfig, CallbackInfo ci) {
        FabricClientMod.beforeInitialResourceReload();
    }

    @Redirect(method = "pickBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;getCloneItemStack(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mekanism$pickBlock(Block block, BlockGetter level, BlockPos pos, BlockState state) {
        Minecraft minecraft = (Minecraft) (Object) this;
        return state.getCloneItemStack(minecraft.hitResult, level, pos, minecraft.player);
    }
}
