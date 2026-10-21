/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.mekanism.lib.common.ForgeHooks;

/**
 * Furnace fuel time asks {@link ForgeHooks#getBurnTime}, which honors an item's {@code getBurnTime} and then Fabric's
 * fuel registry. Vanilla 1.19.2 does not keep the recipe type; Forge stores it, and so does this mixin.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @Unique
    @Nullable
    private RecipeType<? extends AbstractCookingRecipe> mekanism$recipeType;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mekanism$rememberRecipe(BlockEntityType<?> type, BlockPos pos, BlockState state,
          RecipeType<? extends AbstractCookingRecipe> recipeType, CallbackInfo ci) {
        this.mekanism$recipeType = recipeType;
    }

    @Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
    private void mekanism$burnDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ForgeHooks.getBurnTime(stack, this.mekanism$recipeType));
    }

    @Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
    private static void mekanism$isFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ForgeHooks.getBurnTime(stack, null) > 0);
    }
}
