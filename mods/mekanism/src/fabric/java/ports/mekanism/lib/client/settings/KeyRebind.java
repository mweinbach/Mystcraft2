/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.settings;

/**
 * Carries the modifier parsed out of an options string from {@code InputConstants.getKey} to {@code KeyMapping.setKey}.
 */
public final class KeyRebind {

    public static final ThreadLocal<Loaded> LOADED_MODIFIER = new ThreadLocal<>();

    private KeyRebind() {
    }

    /**
     * @param explicit true when the saved string contained {@code :MODIFIER}. A bare key keeps the default modifier.
     */
    public record Loaded(KeyModifier modifier, boolean explicit) {
    }
}
