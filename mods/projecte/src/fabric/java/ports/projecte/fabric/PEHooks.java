/*
 * ProjectE 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric;

/**
 * Where a ProjectE mixin replaces a vanilla call with a Forge-style hook, it does so only for ProjectE's own blocks and
 * items. Everything else keeps the vanilla call, so another Forge API layer in the pack (Mekanism's port wraps several
 * of the same calls) still sees it and its own items keep their behaviour.
 */
public final class PEHooks {

    private PEHooks() {
    }

    /** Whether the block, item or entity is one of ProjectE's, and so may override ProjectE's Forge-style hooks. */
    public static boolean ours(Object object) {
        return object != null && object.getClass().getName().startsWith("moze_intel.projecte.");
    }
}
