package net.mystcraft.gametest.mixin;

import net.minecraft.server.MinecraftServer;
import net.mystcraft.gametest.AllLevelsProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stands in for another mod's injection into getAllLevels; it must still run beside Mystcraft's. */
@Mixin(MinecraftServer.class)
public abstract class AllLevelsProbeMixin {
    @Inject(method = "getAllLevels", at = @At("RETURN"))
    private void mystcraftGametest$probe(CallbackInfoReturnable<Iterable<?>> callback) {
        AllLevelsProbe.record();
    }
}
