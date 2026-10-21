/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.extensions.IForgeItem;

/**
 * Enchantment lookups go through the stack, so module enchantments stored beside the vanilla tag are visible. The
 * enchanting table asks {@code canApplyAtEnchantingTable} instead of the enchantment category alone.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @Inject(method = "getItemEnchantmentLevel", at = @At("HEAD"), cancellable = true)
    private static void mekanism$level(Enchantment enchantment, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(stack.getEnchantmentLevel(enchantment));
    }

    @Inject(method = "runIterationOnItem", at = @At("HEAD"), cancellable = true)
    private static void mekanism$allEnchantments(EnchantmentHelper.EnchantmentVisitor visitor, ItemStack stack, CallbackInfo ci) {
        if (!stack.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> entry : stack.getAllEnchantments().entrySet()) {
                visitor.accept(entry.getKey(), entry.getValue());
            }
        }
        ci.cancel();
    }

    @WrapOperation(method = "getAvailableEnchantmentResults", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/enchantment/EnchantmentCategory;canEnchant(Lnet/minecraft/world/item/Item;)Z"))
    private static boolean mekanism$enchantingTable(EnchantmentCategory category, Item item, Operation<Boolean> original,
          @Local Enchantment enchantment, @Local ItemStack stack) {
        return ((IForgeItem) stack.getItem()).canApplyAtEnchantingTable(stack, enchantment);
    }
}
