package com.technovision.advancedgenetics.mixin.forge;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge's BowItem asks the arrow whether it is infinite; upstream patched the bow's Infinity check instead.
 */
@Mixin(ArrowItem.class)
public class ArrowItemMixin {

    /**
     * Normal arrows are not consumed if the player has the "Infinity" gene, matching the Infinity enchantment.
     */
    @Inject(method = "isInfinite", at = @At("RETURN"), cancellable = true, remap = false)
    private void advancedgenetics$infinityGene(ItemStack stack, ItemStack bow, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && stack.is(Items.ARROW) && ComponentRegistry.getGenetics(player).hasGene(Genes.INFINITY)) {
            cir.setReturnValue(true);
        }
    }
}
