/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Something shears can harvest. The port's mixins make sheep, mooshrooms and snow golems implement it, as Forge does.
 */
public interface IForgeShearable {

    default boolean projecte$isShearable(ItemStack item, Level level, BlockPos pos) {
        return true;
    }

    default List<ItemStack> projecte$onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos, int fortune) {
        return Collections.emptyList();
    }
}
