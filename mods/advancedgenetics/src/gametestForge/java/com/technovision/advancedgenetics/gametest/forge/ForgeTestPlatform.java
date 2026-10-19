package com.technovision.advancedgenetics.gametest.forge;

import com.technovision.advancedgenetics.gametest.TestPlatform;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Forge Energy, item handler capabilities, ForgeCaps saves and LivingAttackEvent. */
final class ForgeTestPlatform implements TestPlatform {

    @Override
    public CompoundTag savedGenetics(CompoundTag playerSave) {
        CompoundTag caps = playerSave.getCompound("ForgeCaps");
        String key = ComponentRegistry.PLAYER_GENETICS_ID.toString();
        return caps.contains(key, Tag.TAG_COMPOUND) ? caps.getCompound(key) : null;
    }

    @Override
    public EnergyAccess energy(BlockEntity blockEntity, Direction side) {
        IEnergyStorage energy = blockEntity.getCapability(ForgeCapabilities.ENERGY, side)
                .orElseThrow(() -> new GameTestAssertException("Missing energy capability on " + side));
        return new EnergyAccess() {
            @Override public int stored() { return energy.getEnergyStored(); }
            @Override public int capacity() { return energy.getMaxEnergyStored(); }
            @Override public int insert(int amount, boolean simulate) { return energy.receiveEnergy(amount, simulate); }
        };
    }

    @Override
    public ItemAccess items(BlockEntity blockEntity, Direction side) {
        IItemHandler items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Missing item handler on " + side));
        return new ItemAccess() {
            @Override public ItemStack insert(ItemStack stack) { return ItemHandlerHelper.insertItem(items, stack, false); }
            @Override public ItemStack extract(int slot, int amount, boolean simulate) { return items.extractItem(slot, amount, simulate); }
        };
    }

    @Override
    public int countDamage(LivingEntity target, Predicate<DamageSource> filter, Runnable action) {
        AtomicInteger hits = new AtomicInteger();
        Consumer<LivingAttackEvent> counter = event -> {
            if (event.getEntity() == target && filter.test(event.getSource())) hits.incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, counter);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        return hits.get();
    }
}
