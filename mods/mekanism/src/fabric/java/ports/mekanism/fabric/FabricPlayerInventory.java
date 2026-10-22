/*
 * Mekanism 1.19.2 port - Fabric adapters.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import mekanism.common.Mekanism;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.common.capabilities.ICapabilityProvider;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.event.AttachCapabilitiesEvent;
import ports.mekanism.lib.eventbus.api.SubscribeEvent;
import ports.mekanism.lib.fml.common.Mod;
import ports.mekanism.lib.items.IItemHandler;
import ports.mekanism.lib.items.ItemHandlerHelper;

/**
 * Forge exposes a player's inventory as an item handler. The chargepad reads that handler. The stacks are the live
 * inventory slots, so an energy write stays on the item the player is holding, and the handler is not serialized:
 * the inventory already is.
 */
@Mod.EventBusSubscriber(modid = Mekanism.MODID)
public final class FabricPlayerInventory {

    private static final ResourceLocation KEY = new ResourceLocation(Mekanism.MODID, "player_inventory");

    private FabricPlayerInventory() {
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player player)) {
            return;
        }
        Provider provider = new Provider(player);
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    /**
     * The optional is created again after it is invalidated. A dimension change removes the player and then revives
     * that same instance: the capability store becomes valid again, and a {@link LazyOptional} does not.
     */
    private static final class Provider implements ICapabilityProvider {

        private final Player player;
        private LazyOptional<IItemHandler> handler;

        private Provider(Player player) {
            this.player = player;
            this.handler = inventory();
        }

        private LazyOptional<IItemHandler> inventory() {
            return LazyOptional.of(() -> new PlayerInventoryHandler(player.getInventory()));
        }

        private void invalidate() {
            handler.invalidate();
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (!handler.isPresent()) {
                handler = inventory();
            }
            return ForgeCapabilities.ITEM_HANDLER.orEmpty(capability, handler);
        }
    }

    /**
     * Main inventory, then armor, then the offhand, the order Forge's combined player wrapper uses.
     */
    private static final class PlayerInventoryHandler implements IItemHandler {

        private final Inventory inventory;

        private PlayerInventoryHandler(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getSlots() {
            return inventory.getContainerSize();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getItem(slot);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack existing = getStackInSlot(slot);
            int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
            if (!existing.isEmpty()) {
                if (!ItemStack.isSameItemSameTags(existing, stack)) {
                    return stack;
                }
                limit -= existing.getCount();
            }
            if (limit <= 0) {
                return stack;
            }
            boolean leftover = stack.getCount() > limit;
            if (!simulate) {
                if (existing.isEmpty()) {
                    inventory.setItem(slot, leftover ? ItemHandlerHelper.copyStackWithSize(stack, limit) : stack);
                } else {
                    existing.grow(leftover ? limit : stack.getCount());
                }
            }
            return leftover ? ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, existing.getCount());
            ItemStack extracted = ItemHandlerHelper.copyStackWithSize(existing, taken);
            if (!simulate) {
                if (existing.getCount() <= taken) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                } else {
                    existing.shrink(taken);
                }
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot >= Inventory.INVENTORY_SIZE && slot < Inventory.SLOT_OFFHAND) {
                return 1;
            }
            return inventory.getMaxStackSize();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot >= 0 && slot < getSlots();
        }
    }
}
