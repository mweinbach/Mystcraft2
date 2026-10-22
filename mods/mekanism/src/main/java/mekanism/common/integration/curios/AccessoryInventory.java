package mekanism.common.integration.curios;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * The equipped accessory slots for one loader. Curios and Trinkets each install their own.
 */
public interface AccessoryInventory {

    AccessoryInventory NONE = new AccessoryInventory() {
        @Override
        public Optional<? extends IItemHandler> inventory(LivingEntity entity) {
            return Optional.empty();
        }

        @Override
        public Optional<AccessoryResult> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
            return Optional.empty();
        }

        @Override
        public ItemStack stack(LivingEntity entity, String slotType, int index) {
            return ItemStack.EMPTY;
        }
    };

    Optional<? extends IItemHandler> inventory(LivingEntity entity);

    Optional<AccessoryResult> findFirst(LivingEntity entity, Predicate<ItemStack> filter);

    ItemStack stack(LivingEntity entity, String slotType, int index);
}
