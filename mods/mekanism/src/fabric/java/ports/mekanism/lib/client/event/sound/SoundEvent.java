/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event.sound;

import net.minecraft.client.sounds.SoundEngine;
import ports.mekanism.lib.eventbus.api.Event;

public abstract class SoundEvent extends Event {

    private final SoundEngine engine;

    protected SoundEvent(SoundEngine engine) {
        this.engine = engine;
    }

    public SoundEngine getEngine() {
        return engine;
    }
}
