/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

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
import ports.mekanism.lib.common.ToolAction;
import ports.mekanism.lib.common.capabilities.AttachedCapabilities;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.ICapabilitySerializable;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.platform.Platform;

/**
 * Methods Forge adds to {@link ItemStack}, injected into it on Fabric. A stack's capabilities come from
 * {@link IForgeItem#initCapabilities}, created the first time they are asked for. Fluid and energy capabilities other
 * items do not provide that way come from the Transfer API and Team Reborn Energy, as a bucket's does on Forge.
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
        LazyOptional<T> attached = ((AttachedCapabilities.Holder) this).mekanism$capabilities().getCapability(cap, side);
        return attached.isPresent() ? attached : Platform.foreignCapability(self(), cap, side);
    }

    @Override
    default CompoundTag serializeNBT() {
        return self().save(new CompoundTag());
    }

    @Override
    default void deserializeNBT(CompoundTag nbt) {
        throw new UnsupportedOperationException("Use ItemStack.of to read an item stack");
    }

    default ItemStack getCraftingRemainingItem() {
        return self().getItem().getCraftingRemainingItem(self());
    }

    default boolean hasCraftingRemainingItem() {
        return self().getItem().hasCraftingRemainingItem(self());
    }

    default int getBurnTime(@Nullable RecipeType<?> recipeType) {
        return self().getItem().getBurnTime(self(), recipeType);
    }

    default boolean canPerformAction(ToolAction toolAction) {
        return self().getItem().canPerformAction(self(), toolAction);
    }

    default int getEnchantmentLevel(Enchantment enchantment) {
        return self().getItem().getEnchantmentLevel(self(), enchantment);
    }

    default Map<Enchantment, Integer> getAllEnchantments() {
        return self().getItem().getAllEnchantments(self());
    }

    @Nullable
    default EquipmentSlot getEquipmentSlot() {
        return self().getItem().getEquipmentSlot(self());
    }

    default boolean canEquip(EquipmentSlot armorType, Entity entity) {
        return self().getItem().canEquip(self(), armorType, entity);
    }

    default boolean isBookEnchantable(ItemStack book) {
        return self().getItem().isBookEnchantable(self(), book);
    }

    default boolean canElytraFly(LivingEntity entity) {
        return self().getItem().canElytraFly(self(), entity);
    }

    default boolean elytraFlightTick(LivingEntity entity, int flightTicks) {
        return self().getItem().elytraFlightTick(self(), entity, flightTicks);
    }

    default boolean canWalkOnPowderedSnow(LivingEntity wearer) {
        return self().getItem().canWalkOnPowderedSnow(self(), wearer);
    }

    default void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
        self().getItem().onDestroyed(itemEntity, damageSource);
    }

    @Nullable
    default FoodProperties getFoodProperties(@Nullable LivingEntity entity) {
        return self().getItem().getFoodProperties(self(), entity);
    }

    default boolean isRepairable() {
        return self().getItem().isRepairable(self());
    }
}
