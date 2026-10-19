package net.mystcraft.minecraft.writing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;

/** Clients select indices only. Every mutation uses the currently open, reachable desk. */
public final class WritingDeskMenu extends AbstractContainerMenu {
    public static final int SOURCE_PREV = 90, SOURCE_NEXT = 91, PAGE_PREV = 100, PAGE_NEXT = 101;
    public static final int TARGET_PREV = 110, TARGET_NEXT = 111, MOVE_UP = 120, MOVE_DOWN = 121;
    public static final int EXTRACT = 122, INSERT = 123, COPY = 124;
    public static final int SELECT_SOURCE_PAGE = 1000;
    public static final int SOURCE_TAKE = 130, SOURCE_TAKE_ALL = 131, SOURCE_INSERT = 132, SOURCE_INSERT_ONE = 133, WRITE = 134;
    public static final int TITLE_BEGIN = 135, TITLE_APPLY = 136, TITLE_CHAR = 0x10000;
    public static final int TARGET_INSERT_ALL = 139, SOURCE_APPEND = 141, BOOK_BEGIN = 142, BOOK_LINK = 143;
    private ItemStack bookIdentity = ItemStack.EMPTY, bookSnapshot = ItemStack.EMPTY;
    public static final int TAB_PREV = 137, TAB_NEXT = 138, TARGET_SELECT = 2000, TAB_INSERT = 3000;
    private String titleBuffer = "";
    private ItemStack titleIdentity = ItemStack.EMPTY;
    private final DataSlot firstTab = DataSlot.standalone();
    private final Container inventory;
    private final ContainerData ink;
    private final BlockPos position;
    private final DataSlot sourceSlot = DataSlot.standalone();
    private final DataSlot sourcePage = DataSlot.standalone();
    private final DataSlot targetPage = DataSlot.standalone();
    public WritingDeskMenu(int id, Inventory player, FriendlyByteBuf buffer) {
        this(id, player, new SimpleContainer(WritingDeskBlockEntity.SIZE), new SimpleContainerData(1), buffer.readBlockPos());
    }
    public WritingDeskMenu(int id, Inventory player, Container inventory, ContainerData ink, BlockPos position) {
        super(WritingDeskRegistry.WRITING_DESK_MENU.get(), id);
        this.inventory = inventory;
        this.ink = ink;
        this.position = position;
        checkContainerSize(inventory, WritingDeskBlockEntity.SIZE);
        checkContainerDataCount(ink, 1);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            addSlot(new Slot(inventory, i, new int[] {241,241,385,385,385}[i], new int[] {80,28,28,80,54}[i]) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return switch (index) {
                        case WritingDeskBlockEntity.TARGET -> PageCollectionItem.isCollection(stack) || WritingDeskBlockEntity.isPage(stack) || stack.getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem;
                        case WritingDeskBlockEntity.PAPER -> stack.is(Items.PAPER);
                        case WritingDeskBlockEntity.INK_VIAL -> net.mystcraft.minecraft.fluid.InkContainerAccess.containsInk(stack);
                        default -> false;
                    };
                }
                @Override public int getMaxStackSize() { return index == 0 ? 1 : 64; }
            });
        }
        for (int i = 0; i < WritingDeskBlockEntity.LIBRARY_SIZE; i++) {
            final int libraryIndex = i;
            addSlot(new Slot(inventory, WritingDeskBlockEntity.LIBRARY_START + i, 37, 34 + i % 4 * 37) {
                @Override public boolean isActive() { return libraryIndex >= firstTab.get() && libraryIndex < firstTab.get() + 4; }
                @Override public boolean mayPlace(ItemStack stack) { return WritingDeskBlockEntity.isLibraryItem(stack); }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, 9 + row * 9 + column, 241 + column * 18, 104 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 241 + column * 18, 162));
        addDataSlots(ink);
        addDataSlot(sourceSlot); addDataSlot(sourcePage); addDataSlot(targetPage); addDataSlot(firstTab);
    }
    public int firstTab() { return firstTab.get(); }
    public ItemStack sourceItem() { return inventory.getItem(WritingDeskBlockEntity.LIBRARY_START + sourceSlot.get()); }
    public ItemStack targetItem() { return inventory.getItem(0); }
    public int ink() { return ink.get(0); }
    public int sourceSlot() { return sourceSlot.get(); }
    public int sourcePageIndex() { return sourcePage.get(); }
    public int targetPageIndex() { return targetPage.get(); }
    public List<CompoundTag> sourcePages() {
        return WritingDeskBlockEntity.sourcePages(inventory.getItem(WritingDeskBlockEntity.LIBRARY_START + sourceSlot.get()));
    }
    public List<CompoundTag> targetPages() { return PageCollectionItem.pages(inventory.getItem(0)); }
    public ItemStack sourcePageStack() {
        List<CompoundTag> pages = sourcePages();
        if (sourcePage.get() < 0 || sourcePage.get() >= pages.size()) return ItemStack.EMPTY;
        return page(pages.get(sourcePage.get()));
    }
    public ItemStack targetPageStack() {
        List<CompoundTag> pages = targetPages();
        if (targetPage.get() < 0 || targetPage.get() >= pages.size()) return ItemStack.EMPTY;
        return page(pages.get(targetPage.get()));
    }
    private static ItemStack page(CompoundTag payload) {
        ItemStack page = new ItemStack(AgeRegistry.PAGE.get());
        PageData.applyPayload(page, payload);
        return page;
    }
    @Override public boolean stillValid(Player player) {
        return player.level.hasChunkAt(position) && player.level.getBlockEntity(position) instanceof WritingDeskBlockEntity desk
                && desk.inventory == inventory && player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 64;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level.isClientSide || !stillValid(player)) return false;
        if (button >= 0 && button < WritingDeskBlockEntity.LIBRARY_SIZE) {
            sourceSlot.set(button); sourcePage.set(0); return true;
        }
        if (button == BOOK_BEGIN) {
            bookIdentity = inventory.getItem(WritingDeskBlockEntity.TARGET);
            bookSnapshot = bookIdentity.copy();
            return bookIdentity.getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem;
        }
        if (button == BOOK_LINK) {
            ItemStack target = inventory.getItem(WritingDeskBlockEntity.TARGET);
            if (player.containerMenu != this || target != bookIdentity || !ItemStack.matches(target, bookSnapshot)
                    || !(target.getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem)
                    || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return false;
            bookIdentity = ItemStack.EMPTY; bookSnapshot = ItemStack.EMPTY;
            boolean linked = net.mystcraft.minecraft.linking.LinkingService.linkFromStand(serverPlayer, target);
            inventory.setChanged();
            return linked;
        }
        if (button == TITLE_BEGIN) { titleBuffer = ""; titleIdentity = inventory.getItem(0); return true; }
        if ((button & 0xffff0000) == TITLE_CHAR) {
            char c = (char) button;
            if (titleIdentity != inventory.getItem(0) || titleBuffer.length() >= 21 || Character.isISOControl(c) || c == '\u00a7') return false;
            titleBuffer += c; return true;
        }
        if (button == TITLE_APPLY) {
            ItemStack target = inventory.getItem(0);
            if (target != titleIdentity || target.isEmpty() || WritingDeskBlockEntity.isPage(target)) return false;
            String name = titleBuffer.strip();
            if (name.isEmpty()) target.resetHoverName(); else target.setHoverName(net.minecraft.network.chat.Component.literal(name));
            inventory.setChanged(); broadcastChanges(); return true;
        }
        if (button >= TARGET_SELECT && button < TARGET_SELECT + PageCollectionItem.MAX_PAGES) {
            int index = button - TARGET_SELECT;
            if (index > targetPages().size()) return false;
            targetPage.set(index); return true;
        }
        if (button >= TAB_INSERT && button < TAB_INSERT + WritingDeskBlockEntity.LIBRARY_SIZE * 2) {
            int encoded = button - TAB_INSERT;
            if (!insertInto(inventory.getItem(WritingDeskBlockEntity.LIBRARY_START + encoded / 2), 0, encoded % 2 == 1)) return false;
            inventory.setChanged(); broadcastChanges(); return true;
        }
        if (button >= SELECT_SOURCE_PAGE && button < SELECT_SOURCE_PAGE + PageCollectionItem.MAX_PAGES) {
            int selected = button - SELECT_SOURCE_PAGE;
            if (selected >= sourcePages().size()) return false;
            sourcePage.set(selected); broadcastChanges(); return true;
        }
        switch (button) {
            case TAB_PREV, TAB_NEXT -> firstTab.set(Math.max(0, Math.min(21, firstTab.get() + (button == TAB_PREV ? -1 : 1))));
            case SOURCE_TAKE, SOURCE_TAKE_ALL -> {
                if (!takeSource(button == SOURCE_TAKE_ALL)) return false;
            }
            case SOURCE_INSERT, SOURCE_INSERT_ONE -> {
                if (!insertInto(sourceItem(), sourcePage.get(), button == SOURCE_INSERT_ONE)) return false;
            }
            case SOURCE_APPEND -> sourcePage.set(sourcePages().size());
            case TARGET_INSERT_ALL -> {
                if (!insertInto(targetItem(), targetPage.get(), false)) return false;
            }
            case WRITE -> {
                if (!((WritingDeskBlockEntity) player.level.getBlockEntity(position)).writePage(sourceSlot.get(), sourcePage.get())) return false;
                ItemStack target = targetItem();
                if (net.mystcraft.minecraft.age.AgeBookData.isUnallocated(target)) {
                    var authors = target.getOrCreateTag().getList("Authors", net.minecraft.nbt.Tag.TAG_STRING);
                    String author = player.getGameProfile().getName();
                    boolean exists = false;
                    for (int i = 0; i < authors.size(); i++) if (authors.getString(i).equals(author)) exists = true;
                    if (!exists && authors.size() < 16) authors.add(net.minecraft.nbt.StringTag.valueOf(author));
                    target.getOrCreateTag().put("Authors", authors);
                }
            }
            case SOURCE_PREV, SOURCE_NEXT -> {
                sourceSlot.set(Math.floorMod(sourceSlot.get() + (button == SOURCE_PREV ? -1 : 1), WritingDeskBlockEntity.LIBRARY_SIZE));
                sourcePage.set(0);
            }
            case PAGE_PREV, PAGE_NEXT -> sourcePage.set(cycle(sourcePage.get(), sourcePages().size(), button == PAGE_PREV ? -1 : 1));
            case TARGET_PREV, TARGET_NEXT -> targetPage.set(cycle(targetPage.get(), targetPages().size(), button == TARGET_PREV ? -1 : 1));
            case MOVE_UP, MOVE_DOWN -> {
                ItemStack collection = inventory.getItem(0);
                if (!(collection.getItem() instanceof PageCollectionItem item) || !item.ordered()) return false;
                List<CompoundTag> pages = new ArrayList<>(targetPages());
                int from = targetPage.get(), to = from + (button == MOVE_UP ? -1 : 1);
                if (from < 0 || from >= pages.size() || to < 0 || to >= pages.size()) return false;
                Collections.swap(pages, from, to);
                PageCollectionItem.setPages(collection, pages);
                targetPage.set(to);
            }
            case EXTRACT -> {
                List<CompoundTag> pages = new ArrayList<>(targetPages());
                int selected = targetPage.get();
                if (selected < 0 || selected >= pages.size()) return false;
                ItemStack extracted = page(pages.get(selected)), carried = getCarried();
                if (!carried.isEmpty() && (!ItemStack.isSameItemSameTags(carried, extracted)
                        || carried.getCount() >= carried.getMaxStackSize())) return false;
                pages.remove(selected);
                PageCollectionItem.setPages(inventory.getItem(0), pages);
                if (carried.isEmpty()) setCarried(extracted); else carried.grow(1);
                targetPage.set(Math.max(0, Math.min(selected, pages.size() - 1)));
            }
            case INSERT -> {
                ItemStack collection = inventory.getItem(0), carried = getCarried();
                List<CompoundTag> pages = new ArrayList<>(targetPages());
                if (!PageCollectionItem.isCollection(collection) || !WritingDeskBlockEntity.isPage(carried)
                        || pages.size() >= PageCollectionItem.MAX_PAGES) return false;
                int selected = Math.max(0, Math.min(targetPage.get(), pages.size()));
                pages.add(selected, PageData.payload(carried));
                PageCollectionItem.setPages(collection, pages);
                carried.shrink(1);
                targetPage.set(selected);
            }
            case COPY -> {
                WritingDeskBlockEntity desk = (WritingDeskBlockEntity) player.level.getBlockEntity(position);
                if (!desk.copyPage(sourceSlot.get(), sourcePage.get())) return false;
            }
            default -> { return false; }
        }
        inventory.setChanged();
        broadcastChanges();
        return true;
    }
    private boolean insertInto(ItemStack collection, int index, boolean single) {
        ItemStack carried = getCarried();
        if (!PageCollectionItem.isCollection(collection) || !WritingDeskBlockEntity.isPage(carried)) return false;
        var pages = new ArrayList<>(PageCollectionItem.pages(collection));
        int amount = Math.min(single ? 1 : carried.getCount(), PageCollectionItem.MAX_PAGES - pages.size());
        if (amount <= 0) return false;
        int insertion = Math.max(0, Math.min(index, pages.size()));
        for (int i = 0; i < amount; i++) pages.add(insertion + i, PageData.payload(carried));
        PageCollectionItem.setPages(collection, pages); carried.shrink(amount); return true;
    }
    private boolean takeSource(boolean bulk) {
        if (!getCarried().isEmpty()) return false;
        ItemStack source = sourceItem();
        int index = sourcePage.get();
        if (WritingDeskBlockEntity.isPage(source)) {
            if (index != 0) return false;
            setCarried(source.split(1)); return true;
        }
        if (!PageCollectionItem.isCollection(source)) return false;
        var pages = new ArrayList<>(sourcePages());
        if (index < 0 || index >= pages.size()) return false;
        CompoundTag payload = pages.remove(index); ItemStack extracted = page(payload);
        if (bulk && source.getItem() instanceof PageCollectionItem item && !item.ordered()) {
            for (int i = pages.size() - 1; i >= 0 && extracted.getCount() < extracted.getMaxStackSize(); i--)
                if (pages.get(i).equals(payload)) { pages.remove(i); extracted.grow(1); }
        }
        PageCollectionItem.setPages(source, pages); setCarried(extracted);
        sourcePage.set(Math.max(0, Math.min(index, pages.size() - 1))); return true;
    }
    private static int cycle(int current, int size, int delta) { return size == 0 ? 0 : Math.floorMod(current + delta, size); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < WritingDeskBlockEntity.SIZE) {
            if (!moveItemStackTo(stack, WritingDeskBlockEntity.SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(Items.PAPER)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (net.mystcraft.minecraft.fluid.InkContainerAccess.containsInk(stack)) {
            if (!moveItemStackTo(stack, 2, 3, false)) return ItemStack.EMPTY;
        } else if (PageCollectionItem.isCollection(stack) && inventory.getItem(0).isEmpty()) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (WritingDeskBlockEntity.isLibraryItem(stack)) {
            if (!moveItemStackTo(stack, WritingDeskBlockEntity.LIBRARY_START, WritingDeskBlockEntity.SIZE, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
