package net.mystcraft.minecraft.writing;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;

/** Original 1000 mB inkwell and 50 mB copying cost; every copy pays before extraction. */
public final class WritingDeskBlockEntity extends BlockEntity implements MenuProvider,net.mystcraft.minecraft.fluid.InkTank {
    public static final int TARGET = 0, PAPER = 1, INK_VIAL = 2, BOTTLES = 3, OUTPUT = 4;
    public static final int LIBRARY_START = 5, LIBRARY_SIZE = 25, SIZE = 30;
    public static final int INK_CAPACITY = 1000, COPY_COST = 50;
    private int ink;
    public final SimpleContainer inventory = new SimpleContainer(SIZE) {
        @Override public void setChanged() { super.setChanged(); WritingDeskBlockEntity.this.setChanged(); }
    };
    public WritingDeskBlockEntity(BlockPos position, BlockState state) {
        super(WritingDeskRegistry.WRITING_DESK_ENTITY.get(), position, state);
    }
    public int ink() { return ink; }
    @Override public int inkAmount(){return ink;}
    @Override public int inkCapacity(){return INK_CAPACITY;}
    @Override public void setInkAmount(int amount,boolean dirty){ink=Math.max(0,Math.min(INK_CAPACITY,amount));if(dirty)setChanged();}
    @Override public int fillInk(int maximum,boolean execute){int accepted=Math.max(0,Math.min(maximum,INK_CAPACITY-ink));if(execute&&accepted>0)setInkAmount(ink+accepted,true);return accepted;}
    @Override public int drainInk(int maximum,boolean execute){int taken=Math.max(0,Math.min(maximum,ink));if(execute&&taken>0)setInkAmount(ink-taken,true);return taken;}
    public static boolean isPage(ItemStack stack) { return stack.is(AgeRegistry.PAGE.get()); }
    public static boolean isLibraryItem(ItemStack stack) { return isPage(stack) || PageCollectionItem.isCollection(stack) || stack.getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem; }
    public static List<CompoundTag> sourcePages(ItemStack source) {
        if (isPage(source)) return List.of(PageData.payload(source));
        if (source.getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem)
            return source.hasTag() ? PageData.readPages(source.getTag()) : List.of();
        return PageCollectionItem.pages(source);
    }
    public boolean copyPage(int libraryIndex, int pageIndex) {
        if (level == null || level.isClientSide || libraryIndex < 0 || libraryIndex >= LIBRARY_SIZE) return false;
        List<CompoundTag> source = sourcePages(inventory.getItem(LIBRARY_START + libraryIndex));
        if (pageIndex < 0 || pageIndex >= source.size() || ink < COPY_COST
                || !inventory.getItem(PAPER).is(Items.PAPER)) return false;
        ItemStack copy = new ItemStack(AgeRegistry.PAGE.get());
        PageData.applyPayload(copy, source.get(pageIndex));
        // Linking panels have no copyable symbol, just as in the original desk.
        if (PageData.symbol(copy).isEmpty() && PageData.legacySymbol(copy).isEmpty()) return false;
        ItemStack output = inventory.getItem(OUTPUT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameTags(copy, output)
                || output.getCount() >= output.getMaxStackSize())) return false;
        ink -= COPY_COST;
        inventory.getItem(PAPER).shrink(1);
        if (output.isEmpty()) inventory.setItem(OUTPUT, copy);
        else output.grow(1);
        inventory.setChanged();
        return true;
    }
    /** Right-click writing pays once; collection targets receive the page directly. */
    public boolean writePage(int libraryIndex, int pageIndex) {
        if (level == null || level.isClientSide || libraryIndex < 0 || libraryIndex >= LIBRARY_SIZE) return false;
        var source = sourcePages(inventory.getItem(LIBRARY_START + libraryIndex));
        if (pageIndex < 0 || pageIndex >= source.size() || ink < COPY_COST) return false;
        CompoundTag payload = source.get(pageIndex);
        if (PageData.symbol(payload).isEmpty() && PageData.legacySymbol(payload).isEmpty()) return false;
        ItemStack target = inventory.getItem(TARGET);
        if (target.isEmpty()) return copyPage(libraryIndex, pageIndex);
        if (PageCollectionItem.isCollection(target)) {
            var pages = new java.util.ArrayList<>(PageCollectionItem.pages(target));
            if (pages.size() >= PageCollectionItem.MAX_PAGES || !inventory.getItem(PAPER).is(Items.PAPER)) return false;
            pages.add(payload.copy()); PageCollectionItem.setPages(target, pages);
            inventory.getItem(PAPER).shrink(1);
        } else if (isPage(target) && target.getCount() == 1 && PageData.isBlank(target)) {
            CompoundTag merged = target.hasTag() ? target.getTag().copy() : new CompoundTag();
            merged.merge(payload); PageData.applyPayload(target, merged);
        } else if (net.mystcraft.minecraft.age.AgeBookData.isUnallocated(target)) {
            // ItemAgebook.writeSymbol fills the first blank only before the Age is visited.
            var pages = new java.util.ArrayList<>(PageData.readPages(target.getTag()));
            int blank = -1;
            for (int i = 1; i < pages.size(); i++) {
                CompoundTag candidate = pages.get(i);
                if (!PageData.isLinkPanel(candidate) && !candidate.contains("symbol")) { blank = i; break; }
            }
            if (blank < 0) return false;
            CompoundTag merged = pages.get(blank).copy(); merged.merge(payload); pages.set(blank, merged);
            PageData.writePages(target.getOrCreateTag(), pages);
        } else return false;
        ink -= COPY_COST; inventory.setChanged(); return true;
    }
    public static void tick(Level level, BlockPos position, BlockState state, WritingDeskBlockEntity desk) {
        ItemStack vial = desk.inventory.getItem(INK_VIAL), bottles = desk.inventory.getItem(BOTTLES);
        if(level.isClientSide||desk.ink>=INK_CAPACITY)return;
        var transfer=net.mystcraft.minecraft.fluid.InkContainerAccess.drainOne(vial,INK_CAPACITY-desk.ink,false);
        if(transfer.isEmpty()||!net.mystcraft.minecraft.fluid.InkContainerAccess.fits(bottles,transfer.get().remainder()))return;
        vial.shrink(1);desk.inventory.setItem(BOTTLES,net.mystcraft.minecraft.fluid.InkContainerAccess.merge(bottles,transfer.get().remainder()));desk.ink+=transfer.get().amount();desk.inventory.setChanged();
    }
    public void dropContents() {
        if (level != null) Containers.dropContents(level, worldPosition, inventory);
        inventory.clearContent();
    }
    @Override public Component getDisplayName() { return Component.translatable("block.mystcraft.writing_desk"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory player, Player owner) {
        ContainerData data = new ContainerData() {
            @Override public int get(int index) { return index == 0 ? ink : 0; }
            @Override public void set(int index, int value) { if (index == 0) ink = Math.max(0, Math.min(INK_CAPACITY, value)); }
            @Override public int getCount() { return 1; }
        };
        return new WritingDeskMenu(id, player, inventory, data, worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        NonNullList<ItemStack> stacks = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) stacks.set(i, inventory.getItem(i));
        ContainerHelper.saveAllItems(tag, stacks);
        tag.putInt("Ink", ink);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        NonNullList<ItemStack> stacks = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, stacks);
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, stacks.get(i));
        ink = Math.max(0, Math.min(INK_CAPACITY, tag.getInt("Ink")));
    }
}
