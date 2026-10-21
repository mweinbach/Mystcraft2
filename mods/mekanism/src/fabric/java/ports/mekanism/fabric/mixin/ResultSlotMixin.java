/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.common.ForgeHooks;
import ports.mekanism.lib.event.ForgeEventFactory;

/**
 * Forge fires {@code ItemCraftedEvent} after {@code ItemStack.onCraftedBy}, and publishes the crafting player around
 * the remainder lookup so recipes can see who took the result.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow
    @Final
    private Player player;

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;I)V",
          shift = At.Shift.AFTER))
    private void mekanism$itemCrafted(ItemStack stack, CallbackInfo ci) {
        ForgeEventFactory.firePlayerCraftingEvent(this.player, stack, this.craftSlots);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "onTake", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRemainingItemsFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;"))
    private NonNullList<ItemStack> mekanism$craftingPlayer(RecipeManager recipes, RecipeType type, Container container, Level level) {
        ForgeHooks.setCraftingPlayer(this.player);
        try {
            return recipes.getRemainingItemsFor(type, container, level);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
    }
}
