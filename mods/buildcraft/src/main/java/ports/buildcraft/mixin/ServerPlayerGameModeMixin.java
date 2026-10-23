package ports.buildcraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.buildcraft.transport.PipeBlock;

/**
 * Upstream {@code BlockPipeHolder.removedByPlayer}: breaking a pipe's pluggable removes only the pluggable. This runs
 * before the loaders' own break events in {@code destroyBlock}, so {@link PipeBlock#onPlayerDestroy} fires them
 * itself, through {@code BlockProtection.breakPart}, before it takes anything off.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void buildcraft$removePluggable(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (level.getBlockState(pos).getBlock() instanceof PipeBlock && PipeBlock.onPlayerDestroy(level, pos, player)) {
            callback.setReturnValue(false);
        }
    }
}
