package ports.buildcraft.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.buildcraft.transport.PipeBlock;

/** The client half of {@link ServerPlayerGameModeMixin}: the pipe is not predicted away when a pluggable is broken. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void buildcraft$keepPipe(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && minecraft.level.getBlockState(pos).getBlock() instanceof PipeBlock
                && PipeBlock.onPlayerDestroy(minecraft.level, pos, minecraft.player)) {
            callback.setReturnValue(false);
        }
    }
}
