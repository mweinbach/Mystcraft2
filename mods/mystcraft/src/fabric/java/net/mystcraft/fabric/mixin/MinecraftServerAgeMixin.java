package net.mystcraft.fabric.mixin;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runtime age creation may occur while a dimension is ticking; iterate a stable snapshot. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerAgeMixin {
    @Inject(method = "getAllLevels", at = @At("HEAD"), cancellable = true)
    private void mystcraft$snapshotLevels(CallbackInfoReturnable<Iterable<ServerLevel>> callback) {
        callback.setReturnValue(List.copyOf(((MinecraftServerAccessor) this).mystcraft$levels().values()));
    }
}
