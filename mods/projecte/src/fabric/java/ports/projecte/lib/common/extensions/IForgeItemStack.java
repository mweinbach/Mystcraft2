/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.common.ToolAction;
import ports.projecte.lib.common.capabilities.AttachedCapabilities;
import ports.projecte.lib.common.capabilities.Capability;
import ports.projecte.lib.common.capabilities.ICapabilitySerializable;
import ports.projecte.lib.common.util.LazyOptional;
import ports.projecte.platform.Platform;

/**
 * Methods Forge adds to {@link ItemStack}, injected into it on Fabric. A stack's capabilities come from
 * {@link IForgeItem#initCapabilities}, created the first time they are asked for. Item, fluid, energy, chemical and heat
 * capabilities other items do not provide that way come from the Fabric lookups other mods register on (the Transfer
 * API and Team Reborn Energy), as a bucket's does on Forge.
 */
public interface IForgeItemStack extends ICapabilitySerializable<CompoundTag> {

    private ItemStack self() {
        return (ItemStack) this;
    }

    @NotNull
    @Override
    default <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (self().isEmpty()) {
            return LazyOptional.empty();
        }
        LazyOptional<T> attached = ((AttachedCapabilities.Holder) this).projecte$capabilities().getCapability(cap, side);
        return attached.isPresent() ? attached : Platform.foreignCapability(self(), cap, side);
    }

    @Override
    default CompoundTag projecte$serializeNBT() {
        return self().save(new CompoundTag());
    }

    @Override
    default void projecte$deserializeNBT(CompoundTag nbt) {
        throw new UnsupportedOperationException("Use ItemStack.of to read an item stack");
    }

    default ItemStack projecte$getCraftingRemainingItem() {
        return self().getItem().projecte$getCraftingRemainingItem(self());
    }

    default boolean projecte$hasCraftingRemainingItem() {
        return self().getItem().projecte$hasCraftingRemainingItem(self());
    }

    default int projecte$getBurnTime(@Nullable RecipeType<?> recipeType) {
        return self().getItem().projecte$getBurnTime(self(), recipeType);
    }

    default boolean canPerformAction(ToolAction toolAction) {
        return self().getItem().canPerformAction(self(), toolAction);
    }

    default int projecte$getEnchantmentLevel(Enchantment enchantment) {
        return self().getItem().projecte$getEnchantmentLevel(self(), enchantment);
    }

    default Map<Enchantment, Integer> projecte$getAllEnchantments() {
        return self().getItem().projecte$getAllEnchantments(self());
    }

    @Nullable
    default EquipmentSlot projecte$getEquipmentSlot() {
        return self().getItem().projecte$getEquipmentSlot(self());
    }

    default boolean projecte$canEquip(EquipmentSlot armorType, Entity entity) {
        return self().getItem().projecte$canEquip(self(), armorType, entity);
    }

    default boolean projecte$isBookEnchantable(ItemStack book) {
        return self().getItem().projecte$isBookEnchantable(self(), book);
    }

    default boolean projecte$canElytraFly(LivingEntity entity) {
        return self().getItem().projecte$canElytraFly(self(), entity);
    }

    default boolean projecte$elytraFlightTick(LivingEntity entity, int flightTicks) {
        return self().getItem().projecte$elytraFlightTick(self(), entity, flightTicks);
    }

    default boolean projecte$canWalkOnPowderedSnow(LivingEntity wearer) {
        return self().getItem().projecte$canWalkOnPowderedSnow(self(), wearer);
    }

    default void projecte$onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
        self().getItem().projecte$onDestroyed(itemEntity, damageSource);
    }

    @Nullable
    default FoodProperties projecte$getFoodProperties(@Nullable LivingEntity entity) {
        return self().getItem().projecte$getFoodProperties(self(), entity);
    }

    default boolean projecte$isRepairable() {
        return self().getItem().projecte$isRepairable(self());
    }
}
