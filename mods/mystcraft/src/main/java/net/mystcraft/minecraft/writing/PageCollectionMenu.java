package net.mystcraft.minecraft.writing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;

/** Portable physical-page editing bound to one exact held collection and inventory slot. */
public final class PageCollectionMenu extends AbstractContainerMenu {
    public static final int PREVIOUS = 300, NEXT = 301, INSERT = 302, EXTRACT = 303, UP = 304, DOWN = 305;
    public static final int REORDER_BASE = 1000, INSERT_ALL = 306, EXTRACT_ALL = 307, APPEND = 308;
    private final Inventory inventory;
    private final InteractionHand hand;
    private final int heldSlot;
    private final ItemStack identity;
    private final DataSlot selected = DataSlot.standalone();
    public PageCollectionMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, buffer.readEnum(InteractionHand.class)); }
    public PageCollectionMenu(int id, Inventory inventory, InteractionHand hand) {
        super(WritingDeskRegistry.PAGE_COLLECTION_MENU.get(), id);
        this.inventory = inventory; this.hand = hand;
        this.heldSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : 40;
        this.identity = inventory.getItem(heldSlot);
        // Both the visible collection and its normal hotbar position are locked. Offhand is
        // explicitly synchronized here rather than relying on held-item rendering updates.
        addSlot(lockedSlot(inventory, heldSlot, -1000, -1000));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            int slot = 9 + row * 9 + column;
            addSlot(new Slot(inventory, slot, 8 + column * 18, 135 + row * 18));
        }
        for (int column = 0; column < 9; column++)
            addSlot(column == heldSlot ? lockedSlot(inventory, column, 8 + column * 18, 193)
                    : new Slot(inventory, column, 8 + column * 18, 193));
        addDataSlot(selected);
    }
    private static Slot lockedSlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        };
    }
    public ItemStack collection() { return inventory.getItem(heldSlot); }
    public List<CompoundTag> pages() { return PageCollectionItem.pages(collection()); }
    public int selected() { return selected.get(); }
    public ItemStack pageAt(int index) {
        List<CompoundTag> pages = pages();
        if (index < 0 || index >= pages.size()) return ItemStack.EMPTY;
        ItemStack page = new ItemStack(AgeRegistry.PAGE.get());
        PageData.applyPayload(page, pages.get(index));
        return page;
    }
    @Override public boolean stillValid(Player player) {
        return player.isAlive() && !player.isSpectator() && inventory.player == player
                && (player.level.isClientSide ? player.getItemInHand(hand).getItem() == identity.getItem() : player.getItemInHand(hand) == identity)
                && collection().getCount() == 1
                && PageCollectionItem.isCollection(identity)
                && (hand != InteractionHand.MAIN_HAND || inventory.selected == heldSlot);
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || (type == ClickType.SWAP && button == heldSlot)) return;
        super.clicked(slot, button, type, player);
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level.isClientSide || !stillValid(player)) return false;
        List<CompoundTag> pages = new ArrayList<>(pages());
        if (button >= 0 && button < PageCollectionItem.MAX_PAGES) {
            if (button >= pages.size()) return false;
            selected.set(button); return true;
        }
        if (button >= REORDER_BASE && button < REORDER_BASE + PageCollectionItem.MAX_PAGES * PageCollectionItem.MAX_PAGES) {
            if (!(collection().getItem() instanceof PageCollectionItem item) || !item.ordered()) return false;
            int encoded = button - REORDER_BASE;
            int from = encoded / PageCollectionItem.MAX_PAGES, to = encoded % PageCollectionItem.MAX_PAGES;
            if (from >= pages.size() || to >= pages.size() || from == to) return false;
            CompoundTag moved = pages.remove(from); pages.add(to, moved);
            PageCollectionItem.setPages(collection(), pages); selected.set(to);
            inventory.setChanged(); broadcastChanges(); return true;
        }
        switch (button) {
            case APPEND -> selected.set(pages.size());
            case PREVIOUS, NEXT -> selected.set(pages.isEmpty() ? 0 : Math.floorMod(selected.get() + (button == PREVIOUS ? -1 : 1), pages.size()));
            case UP, DOWN -> {
                if (!(collection().getItem() instanceof PageCollectionItem item) || !item.ordered()) return false;
                int from = selected.get(), to = from + (button == UP ? -1 : 1);
                if (from < 0 || from >= pages.size() || to < 0 || to >= pages.size()) return false;
                Collections.swap(pages, from, to);
                PageCollectionItem.setPages(collection(), pages); selected.set(to);
            }
            case INSERT, INSERT_ALL -> {
                ItemStack carried = getCarried();
                if (!WritingDeskBlockEntity.isPage(carried) || pages.size() >= PageCollectionItem.MAX_PAGES) return false;
                int insertion = Math.max(0, Math.min(selected.get(), pages.size()));
                int amount = button == INSERT ? 1 : Math.min(carried.getCount(), PageCollectionItem.MAX_PAGES - pages.size());
                for (int i = 0; i < amount; i++) pages.add(insertion + i, PageData.payload(carried));
                PageCollectionItem.setPages(collection(), pages); carried.shrink(amount); selected.set(insertion);
            }
            case EXTRACT, EXTRACT_ALL -> {
                int index = selected.get();
                if (index < 0 || index >= pages.size()) return false;
                ItemStack extracted = pageAt(index), carried = getCarried();
                if (!carried.isEmpty() && (!ItemStack.isSameItemSameTags(carried, extracted) || carried.getCount() >= carried.getMaxStackSize())) return false;
                CompoundTag payload = pages.remove(index);
                int amount = 1;
                if (button == EXTRACT_ALL && collection().getItem() instanceof PageCollectionItem item && !item.ordered()) {
                    int room = extracted.getMaxStackSize() - (carried.isEmpty() ? 0 : carried.getCount());
                    for (int i = pages.size() - 1; i >= 0 && amount < room; i--)
                        if (pages.get(i).equals(payload)) { pages.remove(i); amount++; }
                }
                PageCollectionItem.setPages(collection(), pages);
                if (carried.isEmpty()) { extracted.setCount(amount); setCarried(extracted); } else carried.grow(amount);
                selected.set(Math.max(0, Math.min(index, pages.size() - 1)));
            }
            default -> { return false; }
        }
        inventory.setChanged(); broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index <= 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (WritingDeskBlockEntity.isPage(stack)) {
            List<CompoundTag> pages = new ArrayList<>(pages());
            int amount = Math.min(stack.getCount(), PageCollectionItem.MAX_PAGES - pages.size());
            if (amount <= 0) return ItemStack.EMPTY;
            CompoundTag payload = PageData.payload(stack);
            for (int i = 0; i < amount; i++) pages.add(payload.copy());
            PageCollectionItem.setPages(collection(), pages); stack.shrink(amount);
        } else if (index < 28) {
            if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        inventory.setChanged(); slot.onTake(player, stack); broadcastChanges();
        return original;
    }
}
