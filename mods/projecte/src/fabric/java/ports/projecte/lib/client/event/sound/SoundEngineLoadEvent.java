/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.event.sound;

import net.minecraft.client.sounds.SoundEngine;

/**
 * Fired on the mod buses each time the sound engine (re)loads its sound library.
 */
public class SoundEngineLoadEvent extends SoundEvent {

    public SoundEngineLoadEvent(SoundEngine engine) {
        super(engine);
    }
}
