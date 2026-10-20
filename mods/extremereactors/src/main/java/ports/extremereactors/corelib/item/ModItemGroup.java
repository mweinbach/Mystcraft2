/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import ports.extremereactors.platform.Platform;

import java.util.List;
import java.util.function.Supplier;

/**
 * Creates the creative tabs of the mod through the loader, since a tab needs a free slot in the loader-managed
 * tab list.
 */
public final class ModItemGroup {

    /**
     * @param name the tab name, as {@code namespace.path}; the tab title is the {@code itemGroup.<name>} translation
     * @param contents the stacks listed in the tab, in order
     */
    public static CreativeModeTab create(final String name, final Supplier<ItemStack> icon,
                                         final Supplier<List<ItemStack>> contents) {

        final int separator = name.indexOf('.');
        final ResourceLocation id = separator > 0 ?
                new ResourceLocation(name.substring(0, separator), name.substring(separator + 1)) :
                new ResourceLocation(Platform.MOD_ID, name);

        return Platform.backend().creativeTab(id, icon, contents);
    }

    private ModItemGroup() {
    }
}
