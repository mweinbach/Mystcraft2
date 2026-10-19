package net.mystcraft.minecraft.linking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.registry.LinkEffectsRegistry;
import net.mystcraft.integration.registry.MystcraftSounds;
import net.mystcraft.minecraft.entity.LinkBookEntity;

/** Original LinkListenerEffects precedence and fifty-particle endpoint burst. */
public final class ClassicLinkEffects {
    private ClassicLinkEffects() {}
    public static void emit(ServerLevel level, Vec3 position, Entity entity, LinkModifiers flags, boolean portal) {
        SoundEvent sound = entity instanceof ItemEntity || entity instanceof LinkBookEntity ? MystcraftSounds.POP.get()
                : flags.disarm() ? MystcraftSounds.DISARM.get() : portal ? MystcraftSounds.PORTAL.get()
                : flags.following() ? MystcraftSounds.FOLLOWING.get() : flags.intraLinking() ? MystcraftSounds.INTRA.get() : MystcraftSounds.LINK.get();
        emit(level, position, sound);
    }
    public static void emit(ServerLevel level, Vec3 position, SoundEvent sound) {
        level.playSound(null, position.x, position.y, position.z, sound, SoundSource.PLAYERS, .8F, level.random.nextFloat() * .2F + .9F);
        for (int i = 0; i < 50; i++) level.sendParticles(LinkEffectsRegistry.LINK.get(),
                position.x + level.random.nextFloat() - level.random.nextFloat(),
                position.y + level.random.nextFloat() * 2,
                position.z + level.random.nextFloat() - level.random.nextFloat(), 1, 0, 0, 0, 0);
    }
}
