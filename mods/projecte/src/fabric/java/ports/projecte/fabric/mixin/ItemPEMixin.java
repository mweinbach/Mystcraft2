/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import moze_intel.projecte.gameObjs.items.ItemPE;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeItem;

/** Fabric API's NBT-update animation hook answered by ProjectE's {@code shouldCauseReequipAnimation} override. */
@Mixin(ItemPE.class)
public abstract class ItemPEMixin implements FabricItem {

    @Override
    public boolean allowNbtUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return ((IForgeItem) this).projecte$shouldCauseReequipAnimation(oldStack, newStack, false);
    }
}
