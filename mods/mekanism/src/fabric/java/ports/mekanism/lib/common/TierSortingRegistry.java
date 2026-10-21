/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;

/**
 * Forge sorts custom harvest tiers against vanilla ones. Fabric mining uses {@link Tier#getLevel()} from the tool,
 * so registering a tier only records the order the tools module asked for.
 */
public final class TierSortingRegistry {

    private record Entry(Tier tier, ResourceLocation name, List<Object> after, List<Object> before) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private TierSortingRegistry() {
    }

    public static Tier registerTier(Tier tier, ResourceLocation name, List<Object> after, List<Object> before) {
        ENTRIES.add(new Entry(tier, name, List.copyOf(after), List.copyOf(before)));
        return tier;
    }
}
