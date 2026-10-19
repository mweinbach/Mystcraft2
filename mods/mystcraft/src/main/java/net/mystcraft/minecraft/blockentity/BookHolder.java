package net.mystcraft.minecraft.blockentity;

import net.minecraft.world.item.ItemStack;

/** A loaded block entity whose exact stored book can authorize a linking session. */
public interface BookHolder {
    ItemStack getBook();
}
