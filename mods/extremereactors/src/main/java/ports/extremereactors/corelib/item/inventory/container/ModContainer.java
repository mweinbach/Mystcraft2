/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container;

import com.google.common.collect.ImmutableList;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.event.Event;
import ports.extremereactors.corelib.event.IEvent;
import ports.extremereactors.corelib.internal.Lib;
import ports.extremereactors.corelib.item.inventory.container.data.IContainerData;
import ports.extremereactors.corelib.item.inventory.container.slot.ItemHandlerSlot;
import ports.extremereactors.corelib.item.inventory.container.slot.SlotTemplate;
import ports.extremereactors.corelib.item.inventory.container.slot.type.SlotType;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.corelib.network.messages.ContainerDataMessage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A container whose slots come from a {@link ContainerFactory} layout and whose extra values are kept in sync
 * through {@link IContainerData} entries.
 * <p>
 * Register the inventories the layout names with {@link #addInventory}, then call {@link #createSlots()}.
 * Container data must be added in the same order on both sides.
 */
public class ModContainer
        extends AbstractContainerMenu {

    public static final String INVENTORYNAME_PLAYER_INVENTORY = "playerinventory";

    /** Raised on the client after the container data received from the server is applied. */
    public final IEvent<Runnable> DataUpdated = new Event<>();

    protected ModContainer(final ContainerFactory factory, @Nullable final MenuType<?> type, final int windowId,
                           final Inventory playerInventory) {

        super(type, windowId);
        this._factory = factory;
        this._player = playerInventory.player;
        this._handlers = new HashMap<>();
        this._containers = new HashMap<>();
        this._slotTypes = new ArrayList<>();
        this._slotInventories = new ArrayList<>();
        this._data = new ArrayList<>();
    }

    public Player getPlayer() {
        return this._player;
    }

    public void addInventory(final String name, final IItemHandler handler) {
        this._handlers.put(name, handler);
    }

    public void addInventory(final String name, final Container container) {
        this._containers.put(name, container);
    }

    /** Create the slots of the layout. Every inventory the layout names must have been added. */
    public void createSlots() {

        for (final ContainerFactory.SlotEntry entry : this._factory.getSlots()) {

            final IItemHandler handler = this._handlers.get(entry.inventoryName());
            final Slot slot;

            if (null != handler) {

                slot = new ItemHandlerSlot(handler, entry.index(), entry.template(), entry.x(), entry.y());

            } else {

                final Container container = this._containers.get(entry.inventoryName());

                if (null == container) {
                    throw new IllegalStateException("Unknown inventory " + entry.inventoryName() + " in container layout");
                }

                slot = new TemplateSlot(container, entry.index(), entry.template(), entry.x(), entry.y());
            }

            this.addSlot(slot);
            this._slotTypes.add(entry.template().getType());
            this._slotInventories.add(entry.inventoryName());
        }
    }

    /**
     * The indices, in this container, of the slots of an inventory, ordered by their index in the inventory.
     *
     * @param type only the slots of this type, or all of them if null
     */
    public List<Integer> getSlotIndices(final String inventoryName, @Nullable final SlotType type) {
        return IntStream.range(0, this._slotInventories.size())
                .filter(i -> inventoryName.equals(this._slotInventories.get(i)))
                .filter(i -> null == type || type == this._slotTypes.get(i))
                .boxed()
                .sorted(Comparator.comparingInt(i -> this.slots.get(i).getContainerSlot()))
                .toList();
    }

    /**
     * Move a slot of this container. Slot positions are fixed, so the slot is replaced by a copy at the new position;
     * screens only do this on their client side container.
     */
    public void repositionSlot(final int index, final int x, final int y) {

        final Slot slot = this.slots.get(index);

        if (slot.x == x && slot.y == y) {
            return;
        }

        final Slot copy;

        if (slot instanceof ItemHandlerSlot handlerSlot) {
            copy = handlerSlot.copyAt(x, y);
        } else if (slot instanceof TemplateSlot templateSlot) {
            copy = templateSlot.copyAt(x, y);
        } else {
            return;
        }

        copy.index = index;
        this.slots.set(index, copy);
    }

    public void addContainerData(final IContainerData data) {
        this._data.add(data);
    }

    public List<IContainerData> getContainerData() {
        return ImmutableList.copyOf(this._data);
    }

    /** Apply a data update sent by the server. */
    public void onContainerDataUpdate(final FriendlyByteBuf buffer) {

        while (buffer.isReadable()) {

            final int index = buffer.readVarInt();

            if (index < 0 || index >= this._data.size()) {
                break;
            }

            this._data.get(index).decode(buffer);
        }

        this.DataUpdated.raise(Runnable::run);
    }

    //region AbstractContainerMenu

    @Override
    public boolean stillValid(final Player player) {
        return true;
    }

    @Override
    public void broadcastChanges() {

        super.broadcastChanges();

        if (this._data.isEmpty() || !(this._player instanceof ServerPlayer player)) {
            return;
        }

        FriendlyByteBuf buffer = null;

        for (int index = 0; index < this._data.size(); ++index) {

            final IContainerData data = this._data.get(index);

            if (data.updateFromSource()) {

                if (null == buffer) {
                    buffer = new FriendlyByteBuf(Unpooled.buffer());
                }

                buffer.writeVarInt(index);
                data.encode(buffer);
            }
        }

        if (null != buffer) {
            Lib.network().sendTo(new ContainerDataMessage(this.containerId, buffer), player);
        }
    }

    /**
     * Shift-click: stacks in machine slots go to the player inventory (hotbar first), stacks in the player
     * inventory go to the machine slots that accept them, or else between the main inventory and the hotbar.
     */
    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {

        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        final Slot source = this.slots.get(index);

        if (!source.hasItem() || !source.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        final SlotType sourceType = this._slotTypes.get(index);
        final ItemStack original = source.getItem().copy();

        if (source instanceof ItemHandlerSlot handlerSlot) {

            final int extractable = handlerSlot.getItemHandler()
                    .extractItem(handlerSlot.getContainerSlot(), original.getCount(), true).getCount();

            if (extractable <= 0) {
                return ItemStack.EMPTY;
            }

            original.setCount(extractable);
        }

        ItemStack moving = original.copy();

        if (sourceType.isPlayerSlot()) {

            moving = this.moveTo(moving, type -> type.acceptsPlayerItems() && !type.isPlayerSlot());

            if (moving.getCount() == original.getCount()) {
                moving = this.moveTo(moving, SlotType.PlayerInventory == sourceType ?
                        type -> SlotType.PlayerHotBar == type : type -> SlotType.PlayerInventory == type);
            }

        } else {

            moving = this.moveTo(moving, type -> SlotType.PlayerHotBar == type);
            moving = this.moveTo(moving, type -> SlotType.PlayerInventory == type);
        }

        final int moved = original.getCount() - moving.getCount();

        if (moved <= 0) {
            return ItemStack.EMPTY;
        }

        final ItemStack taken = source.remove(moved);

        source.setChanged();
        source.onTake(player, taken);
        return ItemStack.EMPTY;
    }

    //endregion
    //region internals

    @FunctionalInterface
    private interface SlotTypeFilter {
        boolean test(SlotType type);
    }

    /** Merge into matching stacks first, then into empty slots. Only the target slots are modified. */
    private ItemStack moveTo(ItemStack stack, final SlotTypeFilter filter) {

        for (int pass = 0; pass < 2 && !stack.isEmpty(); ++pass) {

            final boolean merge = 0 == pass;

            for (int i = 0; i < this.slots.size() && !stack.isEmpty(); ++i) {

                if (!filter.test(this._slotTypes.get(i))) {
                    continue;
                }

                final Slot target = this.slots.get(i);
                final ItemStack current = target.getItem();

                if (merge == current.isEmpty() || !target.mayPlace(stack)) {
                    continue;
                }

                if (merge && !ItemStack.isSameItemSameTags(current, stack)) {
                    continue;
                }

                stack = this.insertInto(target, stack);
            }
        }

        return stack;
    }

    private ItemStack insertInto(final Slot target, final ItemStack stack) {

        if (target instanceof ItemHandlerSlot handlerSlot) {
            return handlerSlot.insert(stack, false);
        }

        final ItemStack current = target.getItem();
        final int limit = target.getMaxStackSize(stack);
        final int room = limit - (current.isEmpty() ? 0 : current.getCount());

        if (room <= 0) {
            return stack;
        }

        final int amount = Math.min(room, stack.getCount());
        final ItemStack placed = stack.copy();

        placed.setCount(amount + (current.isEmpty() ? 0 : current.getCount()));
        target.set(placed);

        final ItemStack remainder = stack.copy();

        remainder.shrink(amount);
        return remainder;
    }

    /** A vanilla container slot that follows a {@link SlotTemplate}. */
    private static final class TemplateSlot
            extends Slot {

        TemplateSlot(final Container container, final int index, final SlotTemplate template, final int x, final int y) {

            super(container, index, x, y);
            this._template = template;
        }

        TemplateSlot copyAt(final int x, final int y) {
            return new TemplateSlot(this.container, this.getContainerSlot(), this._template, x, y);
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return this._template.canPlace(this.getContainerSlot(), stack) && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(final Player player) {
            return SlotType.Static != this._template.getType() && super.mayPickup(player);
        }

        private final SlotTemplate _template;
    }

    private final ContainerFactory _factory;
    private final Player _player;
    private final Map<String, IItemHandler> _handlers;
    private final Map<String, Container> _containers;
    private final List<SlotType> _slotTypes;
    private final List<String> _slotInventories;
    private final List<IContainerData> _data;

    //endregion
}
