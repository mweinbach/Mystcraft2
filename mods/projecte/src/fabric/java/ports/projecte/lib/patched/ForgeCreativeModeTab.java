/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.patched;

import java.util.Arrays;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Forge's {@code CreativeModeTab(String)}, which appends the tab to {@link CreativeModeTab#TABS}. Fabric API's
 * creative inventory pages through tabs past the vanilla twelve.
 */
public abstract class ForgeCreativeModeTab extends CreativeModeTab {

    protected ForgeCreativeModeTab(String label) {
        super(nextId(), label);
    }

    private static synchronized int nextId() {
        CreativeModeTab.TABS = Arrays.copyOf(CreativeModeTab.TABS, CreativeModeTab.TABS.length + 1);
        return CreativeModeTab.TABS.length - 1;
    }
}
