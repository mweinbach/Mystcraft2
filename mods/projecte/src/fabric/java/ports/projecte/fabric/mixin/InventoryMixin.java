/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.common.extensions.IForgeItem;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    @Final
    public Player player;
    @Shadow
    public int selected;
    @Shadow
    @Final
    public NonNullList<ItemStack> items;
    @Shadow
    @Final
    public NonNullList<ItemStack> armor;

    /**
     * The second pass keeps a stack the item says a pick must not replace, enchanted or not.
     *
     * @author Mekanism port
     * @reason Pick actions honor {@code isNotReplaceableByPickAction}
     */
    @Overwrite
    public int getSuitableHotbarSlot() {
        for (int i = 0; i < 9; ++i) {
            int slot = (this.selected + i) % 9;
            if (this.items.get(slot).isEmpty()) {
                return slot;
            }
        }
        for (int i = 0; i < 9; ++i) {
            int slot = (this.selected + i) % 9;
            ItemStack stack = this.items.get(slot);
            if (!((IForgeItem) stack.getItem()).isNotReplaceableByPickAction(stack, this.player, slot)) {
                return slot;
            }
        }
        return this.selected;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void projecte$armorTick(CallbackInfo ci) {
        Level level = this.player.level;
        for (ItemStack stack : this.armor) {
            ((IForgeItem) stack.getItem()).onArmorTick(stack, level, this.player);
        }
    }
}
