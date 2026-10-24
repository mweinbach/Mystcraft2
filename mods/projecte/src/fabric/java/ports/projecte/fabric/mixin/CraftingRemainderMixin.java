/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import moze_intel.projecte.gameObjs.items.EvertideAmulet;
import moze_intel.projecte.gameObjs.items.PhilosophersStone;
import moze_intel.projecte.gameObjs.items.VolcaniteAmulet;
import moze_intel.projecte.gameObjs.items.rings.Arcana;
import moze_intel.projecte.gameObjs.items.rings.Zero;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeItem;

/** Fabric API's recipe-remainder hook answered by the ProjectE items that stay in the grid when crafted with. */
@Mixin({PhilosophersStone.class, VolcaniteAmulet.class, EvertideAmulet.class, Arcana.class, Zero.class})
public abstract class CraftingRemainderMixin implements FabricItem {

    @Override
    public ItemStack getRecipeRemainder(ItemStack stack) {
        IForgeItem item = (IForgeItem) this;
        return item.projecte$hasCraftingRemainingItem(stack) ? item.projecte$getCraftingRemainingItem(stack) : ItemStack.EMPTY;
    }
}
