/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
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
import ports.mekanism.lib.client.event.ScreenEvent;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.event.TickEvent;
import ports.mekanism.lib.event.level.LevelEvent;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    private float pausePartialTick;

    @Unique
    private float mekanism$renderPartialTick;

    @Unique
    private boolean mekanism$openingScreen;

    /**
     * Forge posts {@code ScreenEvent.Opening} at the start of {@code setScreen}. JEI's recipe screen is how a machine
     * GUI knows to keep its container open.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mekanism$screenOpening(Screen next, CallbackInfo ci) {
        if (this.mekanism$openingScreen) {
            return;
        }
        Minecraft minecraft = (Minecraft) (Object) this;
        ScreenEvent.Opening opening = new ScreenEvent.Opening(minecraft.screen, next);
        if (MinecraftForge.EVENT_BUS.post(opening)) {
            ci.cancel();
            return;
        }
        Screen replacement = opening.getNewScreen();
        if (replacement != next) {
            ci.cancel();
            this.mekanism$openingScreen = true;
            try {
                minecraft.setScreen(replacement);
            } finally {
                this.mekanism$openingScreen = false;
            }
        }
    }

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

    /**
     * Forge unloads the current client level at the start of {@code setLevel}, before the joining screen, while that
     * level is still current. The new level has already posted load from its constructor.
     */
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void mekanism$unloadReplacedLevel(ClientLevel next, CallbackInfo ci) {
        mekanism$unloadLevel();
    }

    /**
     * Forge unloads inside {@code clearLevel} after the screen is shown and before the integrated server is waited
     * on, while the level field is still set.
     */
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/Minecraft;updateScreenAndTick(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void mekanism$unloadOnDisconnect(Screen screen, CallbackInfo ci) {
        mekanism$unloadLevel();
    }

    @Unique
    private void mekanism$unloadLevel() {
        ClientLevel level = ((Minecraft) (Object) this).level;
        if (level != null) {
            FabricGameEvents.post(new LevelEvent.Unload(level));
        }
    }
}
