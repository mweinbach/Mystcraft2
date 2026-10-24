/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common;

import net.minecraft.core.Registry;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import ports.projecte.lib.common.extensions.IForgeTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;

/**
 * Forge sorts custom harvest tiers against vanilla ones. Here the order is vanilla's five tiers followed by the
 * registered tiers in registration order (each is registered after the previous one, as ProjectE's dark and red
 * matter are). A block needs the highest tier whose tag holds it, vanilla's {@code needs_*_tool} tags included.
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

    /** Whether a tool of {@code tier} harvests {@code state}. */
    public static boolean isCorrectTierForDrops(Tier tier, BlockState state) {
        return rank(tier) >= requiredRank(state);
    }

    private static int rank(Tier tier) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).tier() == tier) {
                return VANILLA_RANKS + i;
            }
        }
        // Unregistered tiers rank by mining level: wood and gold 0, stone 1, iron 2, diamond 3, netherite 4.
        return Math.min(tier.getLevel(), VANILLA_RANKS - 1);
    }

    private static int requiredRank(BlockState state) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            TagKey<Block> tag = ((IForgeTier) ENTRIES.get(i).tier()).projecte$getTag();
            if (tag != null && state.is(tag)) {
                return VANILLA_RANKS + i;
            }
        }
        if (state.is(NEEDS_NETHERITE_TOOL)) {
            return 4;
        } else if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        } else if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return 2;
        } else if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return 1;
        }
        return 0;
    }

    private static final int VANILLA_RANKS = 5;
    private static final TagKey<Block> NEEDS_NETHERITE_TOOL = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("forge", "needs_netherite_tool"));
}
