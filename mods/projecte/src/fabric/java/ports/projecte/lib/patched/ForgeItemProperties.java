/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.patched;

import java.util.HashMap;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Forge's public {@code ItemProperties.register}, which takes an unclamped property function.
 */
public final class ForgeItemProperties {

    private ForgeItemProperties() {
    }

    public static void register(Item item, ResourceLocation name, ItemPropertyFunction property) {
        ItemProperties.PROPERTIES.computeIfAbsent(item, ignored -> new HashMap<>()).put(name, property);
    }
}
