/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired once on the mod buses during client setup; mappings go to Fabric's key binding registry.
 */
public class RegisterKeyMappingsEvent extends Event {

    public void register(KeyMapping key) {
        KeyBindingHelper.registerKeyBinding(key);
    }
}
