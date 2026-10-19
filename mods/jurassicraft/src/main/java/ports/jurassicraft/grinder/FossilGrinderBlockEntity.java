package ports.jurassicraft.grinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/** A 12-slot, server-ticked grinder with the same 200-tick cycle as upstream. */
public final class FossilGrinderBlockEntity extends BlockEntity implements WorldlyContainer, net.minecraft.world.MenuProvider {
    public static final int INPUT_COUNT = 6;
    public static final int SLOT_COUNT = 12;
    public static final int PROCESS_TICKS = 200;
    private static final int[] INPUTS = {0, 1, 2, 3, 4, 5};
    private static final int[] OUTPUTS = {6, 7, 8, 9, 10, 11};
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int progress;
    private int activeSlot = -1;
    private ItemStack activeInput = ItemStack.EMPTY;
    @Nullable private Component customName;
    private LazyOptional<IItemHandler> inputCap = LazyOptional.empty();
    private LazyOptional<IItemHandler> outputCap = LazyOptional.empty();
    private LazyOptional<IItemHandler> unsidedCap = LazyOptional.empty();

    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? progress : index == 1 ? PROCESS_TICKS : 0; }
        @Override public void set(int index, int value) { if (index == 0) progress = value; }
        @Override public int getCount() { return 2; }
    };

    public FossilGrinderBlockEntity(BlockPos pos, BlockState state) {
        super(FossilGrinderRegistry.FOSSIL_GRINDER_ENTITY.get(), pos, state);
        createCapabilities();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FossilGrinderBlockEntity grinder) {
        if (level.isClientSide) return;
        int input = grinder.firstInput();
        if (input < 0 || !grinder.hasEmptyOutput()) {
            if (grinder.progress != 0 || grinder.activeSlot != -1) {
                grinder.progress = 0;
                grinder.activeSlot = -1;
                grinder.activeInput = ItemStack.EMPTY;
                grinder.setChanged();
            }
            return;
        }
        ItemStack current = grinder.items.get(input);
        if (input != grinder.activeSlot || !ItemStack.isSameItemSameTags(current, grinder.activeInput)) {
            grinder.progress = 0;
            grinder.activeSlot = input;
            grinder.activeInput = current.copy();
            grinder.activeInput.setCount(1);
        }
        grinder.progress++;
        if (grinder.progress >= PROCESS_TICKS) {
            ItemStack output = FossilGrinderRecipes.grind(grinder.items.get(input), level.random);
            if (!output.isEmpty()) {
                int target = grinder.outputFor(output);
                if (target >= 0) {
                    if (grinder.items.get(target).isEmpty()) grinder.items.set(target, output);
                    else grinder.items.get(target).grow(output.getCount());
                    grinder.removeItem(input, 1);
                }
            }
            grinder.progress = 0;
        }
        grinder.setChanged();
    }

    private int firstInput() {
        for (int slot : INPUTS) if (FossilGrinderRecipes.accepts(items.get(slot))) return slot;
        return -1;
    }

    private boolean hasEmptyOutput() {
        for (int slot : OUTPUTS) if (items.get(slot).isEmpty()) return true;
        return false;
    }

    private int outputFor(ItemStack output) {
        for (int slot : OUTPUTS) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty() || (ItemStack.isSameItemSameTags(existing, output)
                    && existing.getCount() + output.getCount() <= existing.getMaxStackSize())) return slot;
        }
        return -1;
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < INPUT_COUNT && FossilGrinderRecipes.accepts(stack);
    }
    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.DOWN ? OUTPUTS : INPUTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return slot >= INPUT_COUNT; }

    @Override public Component getDisplayName() {
        return customName != null ? customName : Component.translatable("container.jurassicraft.fossil_grinder");
    }
    public void setCustomName(Component name) { customName = name; setChanged(); }
    @Override @Nullable public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FossilGrinderMenu(id, inventory, this, data);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("ProcessTime", progress);
        tag.putInt("ActiveSlot", activeSlot);
        if (!activeInput.isEmpty()) tag.put("ActiveInput", activeInput.save(new CompoundTag()));
        if (customName != null) tag.putString("CustomName", Component.Serializer.toJson(customName));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        progress = Math.max(0, Math.min(PROCESS_TICKS - 1, tag.getInt("ProcessTime")));
        activeSlot = tag.contains("ActiveSlot") ? tag.getInt("ActiveSlot") : -1;
        activeInput = tag.contains("ActiveInput", 10) ? ItemStack.of(tag.getCompound("ActiveInput")) : ItemStack.EMPTY;
        customName = tag.contains("CustomName", 8) ? Component.Serializer.fromJson(tag.getString("CustomName")) : null;
    }

    private void createCapabilities() {
        inputCap = LazyOptional.of(() -> new SidedInvWrapper(this, Direction.UP));
        outputCap = LazyOptional.of(() -> new SidedInvWrapper(this, Direction.DOWN));
        unsidedCap = LazyOptional.of(() -> new CombinedInvWrapper(new SidedInvWrapper(this, Direction.UP), new SidedInvWrapper(this, Direction.DOWN)));
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return (side == null ? unsidedCap : side == Direction.DOWN ? outputCap : inputCap).cast();
        }
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        inputCap.invalidate(); outputCap.invalidate(); unsidedCap.invalidate();
    }
    @Override public void reviveCaps() { super.reviveCaps(); createCapabilities(); }
}
