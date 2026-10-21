/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The looping alarm of one reactor. It stops by itself once its reactor no longer asks for it.
 */
final class AlarmSound
        extends AbstractTickableSoundInstance {

    AlarmSound(final ReactorEffects reactor, final BlockPos position, final float volume) {

        super(MachineEffects.ALARM, SoundSource.BLOCKS, RandomSource.create());
        this._reactor = reactor;
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = position.getX() + 0.5;
        this.y = position.getY() + 0.5;
        this.z = position.getZ() + 0.5;
    }

    @Override
    public void tick() {

        if (!this._reactor.isAlarmSounding()) {
            this.stop();
        }
    }

    private final ReactorEffects _reactor;
}
