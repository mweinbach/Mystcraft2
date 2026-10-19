package net.mystcraft.minecraft.binder;

import java.util.ArrayList;
import java.util.List;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.BinderRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Classic ordered-page binding; allocation of the Age happens only on first linking. */
public final class BookBinderBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_PAGES = 256;
    public static final int INVENTORY_SIZE = MAX_PAGES + 2;
    private String pendingTitle = "";
    public final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE) {
        @Override public void setChanged() { super.setChanged(); BookBinderBlockEntity.this.setChanged(); }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot == 0 && isCover(stack); }
    };
    public final ContainerData titleData = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? pendingTitle.length() : index <= pendingTitle.length() ? pendingTitle.charAt(index - 1) : 0; }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 22; }
    };

    public BookBinderBlockEntity(BlockPos pos, BlockState state) { super(BinderRegistry.BOOK_BINDER_ENTITY.get(), pos, state); }
    public static boolean isCover(ItemStack stack) { return stack.is(Items.LEATHER) || stack.getItem() instanceof PageCollectionItem collection && collection.ordered() && PageCollectionItem.pages(stack).isEmpty(); }
    public static boolean isPanel(ItemStack stack) { return stack.is(MystcraftItems.LINKING_PANEL.get()) || PageData.isLinkPanel(stack); }
    public static boolean isPage(ItemStack stack) { return stack.is(MystcraftItems.LINKING_PANEL.get()) || stack.is(AgeRegistry.PAGE.get()) || stack.is(Items.PAPER); }
    public int pageCount() {
        int count = 0;
        while (count < MAX_PAGES && !inventory.getItem(count + 2).isEmpty()) count++;
        return count;
    }
    public String pendingTitle() { return pendingTitle; }
    public void clearTitle() { pendingTitle = ""; setChanged(); }
    public void appendTitle(char ch) {
        if (pendingTitle.length() < 21 && !Character.isISOControl(ch)) { pendingTitle += ch; setChanged(); }
    }
    public boolean canBind() {
        if (!inventory.getItem(1).isEmpty()) return false;
        if (!isCover(inventory.getItem(0)) || pendingTitle.isEmpty() || pageCount() == 0 || !isPanel(inventory.getItem(2))) return false;
        for (int index = 1; index < pageCount(); index++) if (isPanel(inventory.getItem(index + 2))) return false;
        return true;
    }
    public ItemStack preview() {
        if (!inventory.getItem(1).isEmpty()) return inventory.getItem(1).copy();
        if (!canBind()) return ItemStack.EMPTY;
        return AgeBookData.create(pendingTitle, payloads());
    }
    private List<CompoundTag> payloads() {
        List<CompoundTag> pages = new ArrayList<>();
        for (int i = 0; i < pageCount(); i++) {
            ItemStack page = inventory.getItem(i + 2);
            pages.add(PageData.payload(page));
        }
        return pages;
    }
    /** Construct first, then consume; a failed book construction never removes inputs. */
    public boolean bindToOutput(Player author) {
        if (level == null || level.isClientSide || !canBind()) return false;
        ItemStack book = AgeBookData.create(pendingTitle, payloads());
        if (book.isEmpty()) return false;
        var authors = new net.minecraft.nbt.ListTag();
        authors.add(net.minecraft.nbt.StringTag.valueOf(author.getGameProfile().getName()));
        book.getOrCreateTag().put("Authors", authors);
        inventory.setItem(1, book);
        inventory.getItem(0).shrink(1);
        for (int i = 2; i < INVENTORY_SIZE; i++) inventory.setItem(i, ItemStack.EMPTY);
        pendingTitle = "";
        setChanged();
        return true;
    }
    /** Inserts individual pages preserving list order, duplicates and all payload NBT. */
    public int insertPages(ItemStack held, int index, boolean single) {
        int count = pageCount();
        if (!isPage(held) || index < 0 || index > count) return 0;
        int inserted = Math.min(single ? 1 : held.getCount(), MAX_PAGES - count);
        for (int i = count - 1; i >= index; i--) inventory.setItem(i + inserted + 2, inventory.getItem(i + 2));
        for (int i = 0; i < inserted; i++) {
            ItemStack page = held.is(Items.PAPER) ? new ItemStack(AgeRegistry.PAGE.get()) : held.copy();
            page.setCount(1);
            if (held.is(Items.PAPER) && held.hasTag()) page.setTag(held.getTag().copy());
            inventory.setItem(index + i + 2, page);
        }
        held.shrink(inserted);
        setChanged();
        return inserted;
    }
    public boolean transferFolder(ItemStack folder, int index) {
        if (!(folder.getItem() instanceof PageCollectionItem collection) || !collection.ordered() || index < 0 || index > pageCount()) return false;
        List<CompoundTag> contents = new ArrayList<>(PageCollectionItem.pages(folder));
        if (contents.size() > MAX_PAGES) return false;
        if (contents.isEmpty()) {
            PageCollectionItem.setPages(folder, payloads());
            for (int i = 2; i < INVENTORY_SIZE; i++) inventory.setItem(i, ItemStack.EMPTY);
        } else {
            int accepted = Math.min(MAX_PAGES - pageCount(), contents.size());
            for (int i = 0; i < accepted; i++) {
                ItemStack page = new ItemStack(AgeRegistry.PAGE.get());
                page.setTag(contents.get(i).copy());
                insertPages(page, index + i, true);
            }
            PageCollectionItem.setPages(folder, new ArrayList<>(contents.subList(accepted, contents.size())));
        }
        setChanged();
        return true;
    }
    public ItemStack removePage(int index) {
        int count = pageCount();
        if (index < 0 || index >= count) return ItemStack.EMPTY;
        ItemStack page = inventory.getItem(index + 2);
        for (int i = index; i < count - 1; i++) inventory.setItem(i + 2, inventory.getItem(i + 3));
        inventory.setItem(count + 1, ItemStack.EMPTY);
        setChanged();
        return page;
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        Containers.dropContents(level, worldPosition, inventory);
        inventory.clearContent();
        setChanged();
    }
    @Override public Component getDisplayName() { return Component.translatable("block.mystcraft.book_binder"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new BookBinderMenu(id, inventory, this); }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Title", pendingTitle);
        ListTag stacks = new ListTag();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).isEmpty()) continue;
            CompoundTag entry = inventory.getItem(i).save(new CompoundTag());
            entry.putInt("Slot", i);
            stacks.add(entry);
        }
        tag.put("Items", stacks);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        pendingTitle = tag.getString("Title");
        if (pendingTitle.length() > 21) pendingTitle = pendingTitle.substring(0, 21);
        inventory.clearContent();
        ListTag stacks = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < stacks.size(); i++) {
            CompoundTag entry = stacks.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < INVENTORY_SIZE) inventory.setItem(slot, ItemStack.of(entry));
        }
    }
}
