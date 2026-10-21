/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import ports.mekanism.lib.common.extensions.IForgeItem;

/**
 * Answers Fabric API's item hooks with the matching Forge methods, so items overriding the Forge methods behave the
 * same on Fabric.
 */
@Mixin(Item.class)
public abstract class ItemMixin implements IForgeItem, FabricItem {

    @Override
    public boolean allowNbtUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return shouldCauseReequipAnimation(oldStack, newStack, false);
    }

    @Override
    public boolean allowContinuingBlockBreaking(Player player, ItemStack oldStack, ItemStack newStack) {
        return !shouldCauseBlockBreakReset(oldStack, newStack);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlot slot) {
        return getAttributeModifiers(slot, stack);
    }

    @Override
    public ItemStack getRecipeRemainder(ItemStack stack) {
        return hasCraftingRemainingItem(stack) ? getCraftingRemainingItem(stack) : ItemStack.EMPTY;
    }
}
