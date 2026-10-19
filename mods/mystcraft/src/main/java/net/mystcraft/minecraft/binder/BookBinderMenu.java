package net.mystcraft.minecraft.binder;

import net.mystcraft.integration.registry.BinderRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Page/title commands use full-width container-scoped actions and server validation. */
public final class BookBinderMenu extends AbstractContainerMenu {
    public static final int TITLE_CLEAR = 1;
    public static final int TITLE_CHAR = 0x10000;
    public static final int INSERT_ALL = 0x20000;
    public static final int INSERT_ONE = 0x30000;
    public static final int REMOVE_PAGE = 0x40000;
    private static final int PLAYER_START = BookBinderBlockEntity.INVENTORY_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;
    private final BookBinderBlockEntity binder;
    private final Container inventory;
    private final ContainerData titleData;
    private final BlockPos pos;
    private final SimpleContainer result = new SimpleContainer(1);

    public BookBinderMenu(int id, Inventory player, FriendlyByteBuf buf) {
        this(id, player, null, new SimpleContainer(BookBinderBlockEntity.INVENTORY_SIZE), new SimpleContainerData(22), buf.readBlockPos());
    }
    public BookBinderMenu(int id, Inventory player, BookBinderBlockEntity binder) {
        this(id, player, binder, binder.inventory, binder.titleData, binder.getBlockPos());
    }
    private BookBinderMenu(int id, Inventory player, BookBinderBlockEntity binder, Container inventory, ContainerData titleData, BlockPos pos) {
        super(BinderRegistry.BOOK_BINDER_MENU.get(), id);
        this.binder = binder;
        this.inventory = inventory;
        this.titleData = titleData;
        this.pos = pos;
        addSlot(new Slot(inventory, 0, 8, 27) {
            @Override public boolean mayPlace(ItemStack stack) { return BookBinderBlockEntity.isCover(stack); }
        });
        addSlot(new Slot(result, 0, 152, 27) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPickup(Player player) {
                return binder == null ? hasItem() : !inventory.getItem(1).isEmpty() || binder.canBind();
            }
            @Override public ItemStack remove(int amount) {
                if (binder == null) return super.remove(amount);
                if (inventory.getItem(1).isEmpty() && !binder.bindToOutput(player.player)) return ItemStack.EMPTY;
                ItemStack taken = inventory.removeItem(1, Math.min(1, amount));
                refreshResult();
                return taken;
            }
        });
        // Hidden slots use the ordinary vanilla menu synchronizer for the complete ordered page list.
        for (int i = 2; i < BookBinderBlockEntity.INVENTORY_SIZE; i++) {
            addSlot(new Slot(inventory, i, -1000, -1000) {
                @Override public boolean isActive() { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(player, col + row * 9 + 9, 8 + col * 18, 99 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 8 + col * 18, 157));
        addDataSlots(titleData);
        refreshResult();
    }
    public int pageCount() {
        int count = 0;
        while (count < BookBinderBlockEntity.MAX_PAGES && !inventory.getItem(count + 2).isEmpty()) count++;
        return count;
    }
    public ItemStack page(int index) { return index < 0 || index >= pageCount() ? ItemStack.EMPTY : inventory.getItem(index + 2); }
    public String pendingTitle() {
        StringBuilder title = new StringBuilder();
        int length = Math.min(21, Math.max(0, titleData.get(0)));
        for (int i = 0; i < length; i++) title.append((char) titleData.get(i + 1));
        return title.toString();
    }
    private void refreshResult() {
        if (binder == null) return;
        ItemStack preview = binder.preview();
        if (!ItemStack.matches(preview, result.getItem(0))) result.setItem(0, preview);
    }
    @Override public void broadcastChanges() { refreshResult(); super.broadcastChanges(); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (binder != null && !stillValid(player)) return;
        // Page slots can only be edited by the bounded, index-checked strip operations below.
        if (slot >= 2 && slot < PLAYER_START) return;
        refreshResult();
        super.clicked(slot, button, type, player);
        refreshResult();
    }
    @Override public boolean clickMenuButton(Player player, int command) {
        if (binder == null || !stillValid(player)) return false;
        if (command == TITLE_CLEAR) binder.clearTitle();
        else if ((command & 0xffff0000) == TITLE_CHAR) binder.appendTitle((char) (command & 0xffff));
        else {
            int kind = command & 0xffff0000;
            int index = command & 0xffff;
            if (index > BookBinderBlockEntity.MAX_PAGES) return false;
            if (kind == REMOVE_PAGE) {
                if (!getCarried().isEmpty()) return false;
                setCarried(binder.removePage(index));
            } else if (kind == INSERT_ALL || kind == INSERT_ONE) {
                ItemStack held = getCarried();
                if (held.isEmpty()) return false;
                if (!binder.transferFolder(held, index)) binder.insertPages(held, index, kind == INSERT_ONE);
                if (held.isEmpty()) setCarried(ItemStack.EMPTY);
            } else return false;
        }
        refreshResult();
        broadcastChanges();
        return true;
    }
    @Override public boolean stillValid(Player player) {
        return player.level.getBlockEntity(pos) instanceof BookBinderBlockEntity current && current == binder
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (binder == null || !stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        refreshResult();
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index == 1) {
            // Books are unstackable. Preflight before materializing a preview so a full inventory cannot pay twice.
            boolean room = false;
            for (int i = PLAYER_START; i < PLAYER_END; i++) if (!slots.get(i).hasItem()) { room = true; break; }
            if (!room || !slot.mayPickup(player)) return ItemStack.EMPTY;
            ItemStack book = slot.remove(1);
            if (book.isEmpty()) return ItemStack.EMPTY;
            if (!moveItemStackTo(book, PLAYER_START, PLAYER_END, true) && !book.isEmpty()) {
                // Persistent recovery stock if a destination ever changes during transfer.
                inventory.setItem(1, book);
                refreshResult();
                return ItemStack.EMPTY;
            }
            slot.onTake(player, book);
            refreshResult();
            return original;
        }
        ItemStack stack = slot.getItem();
        if (index == 0) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (index >= PLAYER_START) {
            if (BookBinderBlockEntity.isPage(stack)) {
                if (binder.insertPages(stack, binder.pageCount(), false) == 0) return ItemStack.EMPTY;
            } else if (BookBinderBlockEntity.isCover(stack)) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else if (index < PLAYER_START + 27) {
                if (!moveItemStackTo(stack, PLAYER_START + 27, PLAYER_END, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, PLAYER_START, PLAYER_START + 27, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        refreshResult();
        return original;
    }
}
