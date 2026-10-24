/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Methods Forge adds to {@link Tier}, injected into it on Fabric. Vanilla tiers have no tag; Mekanism materials
 * override this with the block tag that needs that tier. Fabric mining still uses {@link Tier#getLevel()}.
 */
public interface IForgeTier {

    @Nullable
    default TagKey<Block> projecte$getTag() {
        return null;
    }
}
