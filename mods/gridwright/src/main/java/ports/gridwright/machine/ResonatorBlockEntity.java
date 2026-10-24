package ports.gridwright.machine;

import ports.gridwright.grid.GridManager;
import ports.gridwright.recipe.ResonatingRecipe;
import ports.gridwright.registry.ModBlockEntities;
import ports.gridwright.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The resonator, the first Grid Power consumer. While it has a recipe it can finish it asks its
 * owner's grid for the recipe's GP every tick, and it advances only while that grid is powered.
 */
public class ResonatorBlockEntity extends MachineBlockEntity {

    public static final int DATA_COUNT = 4;
    private static final int INPUT = 0;
    private static final int OUTPUT = 1;
    private static final int[] SLOTS = {INPUT, OUTPUT};

    /** What the resonator is doing, as shown on its screen. */
    public enum Status { IDLE, RUNNING, OVERLOADED, NO_OWNER, BLOCKED }

    @Nullable private ResonatingRecipe recipe;
    private int progress;
    /** Whether it asked for power last tick, so the grid totals it reads include its own demand. */
    private boolean active;
    private Status status = Status.IDLE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> Math.min(progress, 0xFFFF);
                case 1 -> recipe == null ? 0 : Math.min(recipe.time(), 0xFFFF);
                case 2 -> recipe == null ? 0 : (int) Math.min(0xFFFF, Math.round(recipe.gp() * 10));
                case 3 -> status.ordinal();
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

    public ResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONATOR.get(), pos, state, 2);
    }

    public int progress() {
        return progress;
    }

    public Status status() {
        return status;
    }

    @Nullable
    public ResonatingRecipe recipe() {
        return recipe;
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.RESONATOR;
    }

    @Override
    public ContainerData data() {
        return data;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof ResonatorBlockEntity resonator) resonator.tick(server);
    }

    private void tick(ServerLevel level) {
        ResonatingRecipe found = findRecipe(level);
        if (found != recipe) {
            recipe = found;
            progress = 0;
        }
        if (recipe == null || !fits(recipe.output())) {
            stop(recipe == null ? Status.IDLE : Status.BLOCKED);
            return;
        }
        String grid = gridKey(level.getServer());
        if (grid == null) {
            stop(Status.NO_OWNER);
            return;
        }
        GridManager manager = GridManager.get(level.getServer());
        boolean powered = active && manager.snapshot(grid).powered();
        manager.addDemand(grid, recipe.gp());
        boolean wasActive = active;
        active = true;
        if (!powered) {
            status = wasActive ? Status.OVERLOADED : Status.RUNNING;
            return;
        }
        status = Status.RUNNING;
        if (++progress >= recipe.time()) {
            items.get(INPUT).shrink(recipe.amount());
            ItemStack held = items.get(OUTPUT);
            if (held.isEmpty()) items.set(OUTPUT, recipe.assemble(this));
            else held.grow(recipe.output().getCount());
            progress = 0;
        }
        setChanged();
    }

    private void stop(Status reason) {
        if (progress != 0) setChanged();
        progress = 0;
        active = false;
        status = reason;
    }

    @Nullable
    private ResonatingRecipe findRecipe(ServerLevel level) {
        if (items.get(INPUT).isEmpty()) return null;
        if (recipe != null && recipe.matches(this, level)) return recipe;
        return level.getRecipeManager().getRecipeFor(ModRecipes.RESONATING.get(), this, level).orElse(null);
    }

    private boolean fits(ItemStack output) {
        ItemStack held = items.get(OUTPUT);
        return held.isEmpty() || ItemStack.isSameItemSameTags(held, output)
                && held.getCount() + output.getCount() <= Math.min(held.getMaxStackSize(), getMaxStackSize());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot != INPUT || level == null) return false;
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.RESONATING.get()).stream().anyMatch(r -> r.accepts(stack));
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = tag.getInt("Progress");
    }
}
