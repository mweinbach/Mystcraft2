package net.mystcraft.fabric.mixin;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.mystcraft.minecraft.linking.DimensionIdentityCarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player identity belongs to its saved dimension UUID, independently of a recycled dimension key. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionIdentityMixin implements DimensionIdentityCarrier {
    @Unique private UUID mystcraft$dimensionIdentity;
    @Unique private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> mystcraft$respawnOrigin;
    @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> mystcraft$getRespawnOrigin() { return mystcraft$respawnOrigin; }
    @Override public void mystcraft$setRespawnOrigin(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> origin) { mystcraft$respawnOrigin = origin; }
    @Override public UUID mystcraft$getDimensionIdentity() { return mystcraft$dimensionIdentity; }
    @Override public void mystcraft$setDimensionIdentity(UUID identity) { mystcraft$dimensionIdentity = identity; }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mystcraft$loadDimensionIdentity(CompoundTag tag, CallbackInfo callback) {
        mystcraft$dimensionIdentity = tag.hasUUID("MystcraftDimensionUUID") ? tag.getUUID("MystcraftDimensionUUID") : null;
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mystcraft$saveDimensionIdentity(CompoundTag tag, CallbackInfo callback) {
        if (mystcraft$dimensionIdentity != null) tag.putUUID("MystcraftDimensionUUID", mystcraft$dimensionIdentity);
        else tag.remove("MystcraftDimensionUUID");
    }
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void mystcraft$copyDimensionIdentity(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo callback) {
        mystcraft$dimensionIdentity = ((DimensionIdentityCarrier) (Object) oldPlayer).mystcraft$getDimensionIdentity();
        mystcraft$respawnOrigin = oldPlayer.level.dimension();
    }
}
