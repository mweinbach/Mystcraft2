/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

import ports.mekanism.lib.client.settings.IKeyConflictContext;
import ports.mekanism.lib.client.settings.KeyConflictContext;
import ports.mekanism.lib.client.settings.KeyModifier;

/**
 * Injected into {@code KeyMapping}. Vanilla mappings are universal and unmodified; {@code ForgeKeyMapping} carries a
 * context and modifier.
 */
public interface IForgeKeyMapping {

    default IKeyConflictContext getKeyConflictContext() {
        return KeyConflictContext.UNIVERSAL;
    }

    default KeyModifier getKeyModifier() {
        return KeyModifier.NONE;
    }

    default boolean isConflictContextAndModifierActive() {
        return getKeyConflictContext().isActive() && getKeyModifier().isActive(getKeyConflictContext());
    }
}
