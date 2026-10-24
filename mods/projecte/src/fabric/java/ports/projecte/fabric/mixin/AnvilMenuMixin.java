/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import ports.projecte.fabric.PEHooks;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.EnchantedBookItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.common.extensions.IForgeItem;

/**
 * An enchanted book that the item rejects through {@code isBookEnchantable} leaves the anvil result empty, as Forge's
 * anvil patch does after it applies the book. Anvil events stay unwired.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Shadow
    @Final
    private DataSlot cost;

    @Inject(method = "createResult", at = @At("TAIL"))
    private void projecte$bookEnchantable(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        ItemStack book = menu.getSlot(1).getItem();
        boolean enchantedBook = book.is(Items.ENCHANTED_BOOK) && !EnchantedBookItem.getEnchantments(book).isEmpty();
        ItemStack result = menu.getSlot(2).getItem();
        if (enchantedBook && !result.isEmpty() && PEHooks.ours(result.getItem()) && !((IForgeItem) result.getItem()).projecte$isBookEnchantable(result, book)) {
            menu.getSlot(2).set(ItemStack.EMPTY);
            this.cost.set(0);
        }
    }
}
