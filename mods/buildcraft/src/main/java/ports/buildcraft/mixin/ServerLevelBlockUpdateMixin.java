package ports.buildcraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.buildcraft.lib.BlockUpdateListeners;

/** Upstream's builders registered world event listeners; 1.19 levels report block changes through {@code sendBlockUpdated}. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockUpdateMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void buildcraft$notifyBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo callback) {
        BlockUpdateListeners.fire((ServerLevel) (Object) this, pos);
    }
}
