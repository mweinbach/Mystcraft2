/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when the sound engine is asked to play a sound. The sound may be replaced, or removed to play nothing.
 */
public class PlaySoundEvent extends SoundEvent {

    private final String name;
    private final SoundInstance originalSound;
    @Nullable
    private SoundInstance sound;

    public PlaySoundEvent(SoundEngine engine, SoundInstance sound) {
        super(engine);
        this.originalSound = sound;
        this.name = sound.getLocation().getPath();
        this.sound = sound;
    }

    public String getName() {
        return name;
    }

    public SoundInstance getOriginalSound() {
        return originalSound;
    }

    @Nullable
    public SoundInstance getSound() {
        return sound;
    }

    public void setSound(@Nullable SoundInstance newSound) {
        sound = newSound;
    }
}
