package com.technovision.advancedgenetics.gametest;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

/**
 * The few loader APIs the shared GameTests observe: each loader's energy and item transfer API,
 * where player genetics are saved, and a pre-invulnerability damage probe.
 */
public interface TestPlatform {

    /** The player's saved genetics compound, or {@code null} if the save has none. */
    CompoundTag savedGenetics(CompoundTag playerSave);

    /** The machine's energy as seen by other mods through this loader's energy API. */
    EnergyAccess energy(BlockEntity blockEntity, Direction side);

    /** The machine's inventory as seen by hoppers and pipes through this loader's item API. */
    ItemAccess items(BlockEntity blockEntity, Direction side);

    /** Counts damage attempts on {@code target} matching {@code filter} while {@code action} runs. */
    int countDamage(LivingEntity target, Predicate<DamageSource> filter, Runnable action);

    interface EnergyAccess {
        int stored();
        int capacity();
        /** Returns the energy accepted. */
        int insert(int amount, boolean simulate);
    }

    interface ItemAccess {
        /** Inserts into any slot the face allows and returns what did not fit. */
        ItemStack insert(ItemStack stack);
        /** Extracts from the face's {@code slot}-th slot. */
        ItemStack extract(int slot, int amount, boolean simulate);
    }

    final class Holder {
        private static TestPlatform platform;

        private Holder() { }

        public static synchronized void install(TestPlatform implementation) {
            if (platform == null) platform = implementation;
        }

        static TestPlatform get() {
            if (platform == null) throw new IllegalStateException("No GameTest platform installed");
            return platform;
        }
    }
}
