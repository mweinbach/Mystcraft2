package net.mystcraft.minecraft.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.mystcraft.integration.registry.MeteorRegistry;
import net.mystcraft.minecraft.entity.MeteorEntity;

/** Original moving meteor roar: looping, volume two, no distance attenuation. */
public final class MeteorMovingSound extends AbstractTickableSoundInstance {
    private final MeteorEntity meteor;
    public MeteorMovingSound(MeteorEntity meteor) {
        super(MeteorRegistry.ROAR.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.meteor = meteor;
        looping = true; delay = 0; volume = 2; attenuation = Attenuation.NONE;
        x = meteor.getX(); y = meteor.getY(); z = meteor.getZ();
    }
    @Override public void tick() {
        if (meteor.isRemoved()) stop();
        else { x = meteor.getX(); y = meteor.getY(); z = meteor.getZ(); }
    }
}
