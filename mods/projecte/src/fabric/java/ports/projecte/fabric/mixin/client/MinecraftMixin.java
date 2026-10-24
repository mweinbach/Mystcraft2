/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.event.entity.player.PlayerInteractEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
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
import ports.projecte.fabric.FabricGameEvents;
import ports.projecte.fabric.client.FabricClientMod;
import ports.projecte.lib.client.event.ScreenEvent;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.event.TickEvent;
import ports.projecte.lib.event.level.LevelEvent;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    private float pausePartialTick;

    @Unique
    private float projecte$renderPartialTick;

    @Unique
    private boolean projecte$openingScreen;

    /**
     * Forge posts {@code ScreenEvent.Opening} at the start of {@code setScreen}. JEI's recipe screen is how a machine
     * GUI knows to keep its container open.
     */
    @Shadow
    protected int missTime;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    @Nullable
    public LocalPlayer player;

    /** Forge posts {@code LeftClickEmpty} when an attack swings at nothing. */
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void projecte$leftClickEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (missTime <= 0 && hitResult != null && hitResult.getType() == HitResult.Type.MISS && player != null && !player.isHandsBusy()) {
            MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickEmpty(player));
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void projecte$screenOpening(Screen next, CallbackInfo ci) {
        if (this.projecte$openingScreen) {
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
            this.projecte$openingScreen = true;
            try {
                minecraft.setScreen(replacement);
            } finally {
                this.projecte$openingScreen = false;
            }
        }
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"))
    private void projecte$renderTickStart(boolean renderLevel, CallbackInfo ci) {
        // Forge caches this partial tick and posts the render tick around gameRenderer.render and the toast pass.
        Minecraft minecraft = (Minecraft) (Object) this;
        this.projecte$renderPartialTick = minecraft.isPaused() ? this.pausePartialTick : minecraft.getFrameTime();
        FabricGameEvents.post(new TickEvent.RenderTickEvent(TickEvent.Phase.START, this.projecte$renderPartialTick));
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void projecte$renderTickEnd(boolean renderLevel, CallbackInfo ci) {
        FabricGameEvents.post(new TickEvent.RenderTickEvent(TickEvent.Phase.END, this.projecte$renderPartialTick));
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
          target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;"))
    private void projecte$beforeInitialReload(GameConfig gameConfig, CallbackInfo ci) {
        FabricClientMod.beforeInitialResourceReload();
    }

    @Redirect(method = "pickBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;getCloneItemStack(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack projecte$pickBlock(Block block, BlockGetter level, BlockPos pos, BlockState state) {
        Minecraft minecraft = (Minecraft) (Object) this;
        return state.getCloneItemStack(minecraft.hitResult, level, pos, minecraft.player);
    }

    /**
     * Forge unloads the current client level at the start of {@code setLevel}, before the joining screen, while that
     * level is still current. The new level has already posted load from its constructor.
     */
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void projecte$unloadReplacedLevel(ClientLevel next, CallbackInfo ci) {
        projecte$unloadLevel();
    }

    /**
     * Forge unloads inside {@code clearLevel} after the screen is shown and before the integrated server is waited
     * on, while the level field is still set.
     */
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/Minecraft;updateScreenAndTick(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void projecte$unloadOnDisconnect(Screen screen, CallbackInfo ci) {
        projecte$unloadLevel();
    }

    @Unique
    private void projecte$unloadLevel() {
        ClientLevel level = ((Minecraft) (Object) this).level;
        if (level != null) {
            FabricGameEvents.post(new LevelEvent.Unload(level));
        }
    }
}
