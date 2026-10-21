package ports.buildcraft.silicon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.AverageLong;
import ports.buildcraft.lib.HasWork;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SlotRange;

/**
 * Upstream {@code TileLaserTableBase}: collects laser power up to the current job's target, averaging what it
 * received over the last 120 ticks for its power ledger.
 */
public abstract class LaserTableBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, LaserTarget, HasWork {
    private static final long MJ_FLOW_ROUND = Power.MJ / 10;

    protected final NonNullList<ItemStack> items;
    private final int[] automationSlots;
    private final AverageLong avgPower = new AverageLong(120);
    protected long power;

    protected LaserTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        items = NonNullList.withSize(size, ItemStack.EMPTY);
        automationSlots = IntStream.range(0, size).filter(slot -> !isPhantom(slot)).toArray();
    }

    public abstract long getTarget();

    @Override
    public boolean hasWork() {
        return getTarget() > 0;
    }

    public long power() {
        return power;
    }

    /** Phantom slots only hold a pattern: automation cannot reach them and they never drop. */
    protected boolean isPhantom(int slot) {
        return false;
    }

    protected abstract boolean canInsert(int slot);

    protected abstract boolean canExtract(int slot);

    /** Called after a slot's contents are replaced. */
    protected void onSlotChanged(int slot) {}

    @Override
    public long getRequiredLaserPower() {
        return getTarget() - power;
    }

    @Override
    public long receiveLaserPower(long microJoules) {
        long received = Math.min(microJoules, getRequiredLaserPower());
        power += received;
        avgPower.push(received);
        return microJoules - received;
    }

    void serverTick() {
        avgPower.tick();
        if (getTarget() <= 0) {
            power = 0;
            avgPower.clear();
        }
    }

    /** Upstream's GUI average: the received power per tick, rounded to the nearest tenth of an MJ. */
    public long averagePowerRounded() {
        long average = Math.round(avgPower.getAverage());
        long div = average / MJ_FLOW_ROUND;
        long mod = average % MJ_FLOW_ROUND;
        return (div + (mod > MJ_FLOW_ROUND / 2 ? 1 : 0)) * MJ_FLOW_ROUND;
    }

    /**
     * Upstream {@code extract}: takes each ingredient's count from whichever slots match it. While simulating,
     * slots are not reduced, so two ingredients may count the same stack. When {@code precise}, every non-empty
     * slot must have matched an ingredient.
     */
    protected static boolean extract(SlotRange inv, List<IngredientStack> items, boolean simulate, boolean precise) {
        long remainingStacks = inv.stacks().stream().filter(stack -> !stack.isEmpty()).count();
        for (IngredientStack definition : items) {
            int remaining = definition.count();
            for (int i = 0; i < inv.size() && remaining > 0; i++) {
                ItemStack slotStack = inv.get(i);
                if (slotStack.isEmpty() || !definition.matches(slotStack)) continue;
                int spend = Math.min(remaining, slotStack.getCount());
                remaining -= spend;
                if (!simulate) {
                    ItemStack left = slotStack.copy();
                    left.shrink(spend);
                    inv.set(i, left.isEmpty() ? ItemStack.EMPTY : left);
                }
            }
            if (remaining != 0) return false;
            remainingStacks--;
        }
        return !precise || remainingStacks == 0;
    }

    public List<ItemStack> drops() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < items.size(); slot++) {
            if (!isPhantom(slot) && !items.get(slot).isEmpty()) drops.add(items.get(slot));
        }
        return drops;
    }

    // Container

    @Override public int getContainerSize() { return items.size(); }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) slotChanged(slot);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        slotChanged(slot);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        slotChanged(slot);
    }

    private void slotChanged(int slot) {
        onSlotChanged(slot);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) setItem(slot, ItemStack.EMPTY);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return canInsert(slot);
    }

    @Override public int[] getSlotsForFace(Direction side) { return automationSlots; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canInsert(slot); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return canExtract(slot); }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    // Persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putLong("power", power);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.replaceAll(stack -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        power = tag.getLong("power");
    }
}
