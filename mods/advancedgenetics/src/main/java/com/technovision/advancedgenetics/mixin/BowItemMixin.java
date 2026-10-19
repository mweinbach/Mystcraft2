package com.technovision.advancedgenetics.mixin;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BowItem.class)
public class BowItemMixin {

    /**
     * Allow using bow without any arrows if player has "Infinity" gene. The flag also stops plain
     * arrows being consumed, as the Infinity enchantment does.
     */
    @ModifyVariable(method = "releaseUsing", at = @At("STORE"), ordinal = 0)
    private boolean advancedgenetics$infinityGene(boolean infinite, ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        return infinite || (user instanceof Player player && ComponentRegistry.getGenetics(player).hasGene(Genes.INFINITY));
    }
}
