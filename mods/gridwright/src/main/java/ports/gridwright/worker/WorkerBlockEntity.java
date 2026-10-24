package ports.gridwright.worker;

import ports.gridwright.grid.GridManager;
import ports.gridwright.machine.EnergyBuffer;
import ports.gridwright.machine.EnergyHolder;
import ports.gridwright.machine.InventoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A machine that works on the world or a virtual one: the quarries and the mechanical user and miner.
 * Its slots and screen follow its {@link WorkerLayout}; output slots can be emptied by hoppers and
 * pipes from any face, and the other slots only accept what the machine uses.
 */
public abstract class WorkerBlockEntity extends InventoryBlockEntity implements MenuProvider {

    /** Synchronised screen values: status, GP x10, progress (per mille), energy and capacity (two halves each), a counter. */
    public static final int DATA_COUNT = 8;

    /** What a worker is doing, as shown on its screen. */
    public enum Status {
        IDLE, RUNNING, OVERLOADED, NO_OWNER, BLOCKED, INCOMPLETE, NO_BOUNDARY, OPEN_BOUNDARY, TOO_LARGE,
        NO_ENERGY, WAITING, FINISHED, PAUSED, PROTECTED, NO_RECIPE, SHORT
    }

    private final WorkerLayout layout;
    protected Status status = Status.IDLE;
    /** Whether it asked for power last tick, so the grid totals it reads include its own demand. */
    private boolean drawing;
    private double demand;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            EnergyBuffer energy = WorkerBlockEntity.this instanceof EnergyHolder holder ? holder.energy() : null;
            return switch (index) {
                case 0 -> status.ordinal();
                case 1 -> (int) Math.min(0xFFFF, Math.round(demand * 10));
                case 2 -> Math.max(0, Math.min(1000, progressPerMille()));
                case 3 -> energy == null ? 0 : (int) (energy.stored() & 0xFFFF);
                case 4 -> energy == null ? 0 : (int) ((energy.stored() >>> 16) & 0xFFFF);
                case 5 -> energy == null ? 0 : (int) (energy.capacity() & 0xFFFF);
                case 6 -> energy == null ? 0 : (int) ((energy.capacity() >>> 16) & 0xFFFF);
                case 7 -> Math.min(0xFFFF, counter());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; the client copy is a SimpleContainerData.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    protected WorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, WorkerLayout layout) {
        super(type, pos, state, layout.size());
        this.layout = layout;
    }

    public WorkerLayout layout() {
        return layout;
    }

    public Status status() {
        return status;
    }

    /** The GP this worker asked its grid for on its last tick (0 when idle). */
    public double demand() {
        return demand;
    }

    /** Progress of the current operation for the screen, 0 to 1000. */
    protected int progressPerMille() {
        return 0;
    }

    /** A machine-specific count for the screen (blocks mined, uses made). */
    protected int counter() {
        return 0;
    }

    public ContainerData data() {
        return data;
    }

    /**
     * Asks the owner's grid for {@code gp} this tick. Returns true when the grid covered this worker's
     * demand last tick, so work may happen now; otherwise sets the status (no owner, overloaded).
     */
    protected boolean requestPower(ServerLevel level, double gp) {
        String grid = gridKey(level.getServer());
        if (grid == null) {
            stop(Status.NO_OWNER);
            return false;
        }
        GridManager manager = GridManager.get(level.getServer());
        boolean powered = drawing && manager.snapshot(grid).powered();
        manager.addDemand(grid, gp);
        boolean wasDrawing = drawing;
        drawing = true;
        demand = gp;
        if (!powered) {
            status = wasDrawing ? Status.OVERLOADED : Status.RUNNING;
            return false;
        }
        status = Status.RUNNING;
        return true;
    }

    /** Stops asking for power and shows {@code reason}. */
    protected void stop(Status reason) {
        drawing = false;
        demand = 0;
        status = reason;
    }

    /** Whether every stack fits into slots {@code [from, to)} together. */
    protected boolean fits(List<ItemStack> stacks, int from, int to) {
        List<ItemStack> copy = new ArrayList<>(to - from);
        for (int slot = from; slot < to; slot++) copy.add(items.get(slot).copy());
        for (ItemStack stack : stacks) {
            if (!merge(copy, stack.copy()).isEmpty()) return false;
        }
        return true;
    }

    /** Puts {@code stack} into slots {@code [from, to)}, returning what did not fit. */
    protected ItemStack store(ItemStack stack, int from, int to) {
        ItemStack left = merge(items.subList(from, to), stack);
        setChanged();
        return left;
    }

    private ItemStack merge(List<ItemStack> slots, ItemStack stack) {
        int limit = Math.min(stack.getMaxStackSize(), getMaxStackSize());
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
                ItemStack held = slots.get(i);
                if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameTags(held, stack)) {
                    int moved = Math.min(stack.getCount(), limit - held.getCount());
                    if (moved > 0) {
                        held.grow(moved);
                        stack.shrink(moved);
                    }
                } else if (pass == 1 && held.isEmpty()) {
                    int moved = Math.min(stack.getCount(), limit);
                    slots.set(i, stack.split(moved));
                }
            }
        }
        return stack;
    }

    /** Drops mined but waiting for output space; dropped with the inventory when the block is removed. */
    public List<ItemStack> pendingDrops() {
        return List.of();
    }

    /** Saves drops waiting for output space. */
    protected static ListTag saveStacks(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) list.add(stack.save(new CompoundTag()));
        return list;
    }

    protected static List<ItemStack> loadStacks(ListTag list) {
        List<ItemStack> stacks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return layout().accepts(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return layout().allSlots();
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return layout().isOutput(slot);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WorkerMenu(containerId, inventory, this, this, data, layout());
    }
}
