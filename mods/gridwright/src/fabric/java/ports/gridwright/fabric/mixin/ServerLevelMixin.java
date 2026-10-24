package ports.gridwright.fabric.mixin;

import ports.gridwright.storage.SoundMuffling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Fabric has no sound event, so Sound Mufflers turn down server sounds here, where Forge fires
 * {@code PlayLevelSoundEvent}: a muffled sound is cancelled and replayed once at the lower volume.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    /** Set while replaying a muffled sound, so the replay is not muffled again. */
    @Unique
    private static boolean gridwright$replaying;

    @Shadow
    public abstract void playSeededSound(@Nullable Player player, double x, double y, double z, SoundEvent sound,
                                         SoundSource source, float volume, float pitch, long seed);

    @Shadow
    public abstract void playSeededSound(@Nullable Player player, Entity entity, SoundEvent sound,
                                         SoundSource source, float volume, float pitch, long seed);

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void gridwright$muffleAt(@Nullable Player player, double x, double y, double z, SoundEvent sound,
                                     SoundSource source, float volume, float pitch, long seed, CallbackInfo info) {
        if (gridwright$replaying) return;
        float muffled = SoundMuffling.volume((ServerLevel) (Object) this, x, y, z, volume);
        if (muffled == volume) return;
        info.cancel();
        gridwright$replaying = true;
        try {
            playSeededSound(player, x, y, z, sound, source, muffled, pitch, seed);
        } finally {
            gridwright$replaying = false;
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void gridwright$muffleEntity(@Nullable Player player, Entity entity, SoundEvent sound, SoundSource source,
                                         float volume, float pitch, long seed, CallbackInfo info) {
        if (gridwright$replaying) return;
        float muffled = SoundMuffling.volume((ServerLevel) (Object) this, entity.getX(), entity.getY(), entity.getZ(), volume);
        if (muffled == volume) return;
        info.cancel();
        gridwright$replaying = true;
        try {
            playSeededSound(player, entity, sound, source, muffled, pitch, seed);
        } finally {
            gridwright$replaying = false;
        }
    }
}
