/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import ports.mekanism.lib.client.settings.IKeyConflictContext;
import ports.mekanism.lib.client.settings.KeyModifier;

/**
 * Injected into {@code KeyMapping}. The modifier, its default and the conflict context live on the key mapping mixin,
 * so a vanilla mapping can be given a context and a rebinding can change the modifier.
 */
public interface IForgeKeyMapping {

    InputConstants.Key getKey();

    void setKeyConflictContext(IKeyConflictContext keyConflictContext);

    IKeyConflictContext getKeyConflictContext();

    KeyModifier getDefaultKeyModifier();

    void setDefaultKeyModifier(KeyModifier keyModifier);

    KeyModifier getKeyModifier();

    void setKeyModifierAndCode(KeyModifier keyModifier, InputConstants.Key keyCode);

    default void setToDefault() {
        setKeyModifierAndCode(getDefaultKeyModifier(), ((KeyMapping) this).getDefaultKey());
    }

    default boolean isConflictContextAndModifierActive() {
        return getKeyConflictContext().isActive() && getKeyModifier().isActive(getKeyConflictContext());
    }

    default boolean isActiveAndMatches(InputConstants.Key keyCode) {
        return keyCode != InputConstants.UNKNOWN && keyCode.equals(getKey()) && isConflictContextAndModifierActive();
    }

    /**
     * True when one binding's key is the other's modifier and their contexts conflict.
     */
    default boolean hasKeyModifierConflict(KeyMapping other) {
        IForgeKeyMapping them = (IForgeKeyMapping) other;
        if (getKeyConflictContext().conflicts(them.getKeyConflictContext()) || them.getKeyConflictContext().conflicts(getKeyConflictContext())) {
            return getKeyModifier().matches(them.getKey()) || them.getKeyModifier().matches(getKey());
        }
        return false;
    }
}
