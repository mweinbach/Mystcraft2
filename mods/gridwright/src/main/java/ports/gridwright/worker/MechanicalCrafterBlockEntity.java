package ports.gridwright.worker;

import ports.gridwright.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * An autocrafter on Grid Power. Its 3x3 grid is both the pattern and the ingredient store: lay the
 * ingredients out as a crafting table wants them, then let hoppers or pipes top the grid up. Every
 * {@link #INTERVAL} ticks, while every slot can spare an ingredient, it takes one from each slot,
 * crafts, and puts the result and any leftovers (buckets, bottles) in its output. It needs {@link #GP}
 * Grid Power while it has something to craft, and pauses on a redstone signal.
 *
 * <p>The last item of a stackable ingredient always stays in its slot, so the layout is never lost.
 * Unstackable ingredients (milk buckets, tools) are used up completely; the crafter remembers the
 * layout that last made a recipe, so it knows which slot each one goes back into.
 *
 * <p>Pipes and hoppers may only add an ingredient to a slot that holds it (or, when empty, is
 * remembered to want it), and only to the emptiest such slot, so an ingredient used in several places
 * fills evenly.
 */
public class MechanicalCrafterBlockEntity extends WorkerBlockEntity {

    public static final double GP = 4;
    public static final int INTERVAL = 10;
    private static final int GRID = 9;

    private int cooldown;
    private int crafts;
    /** The layout that last made a recipe, one of each ingredient; empty slots are {@link ItemStack#EMPTY}. */
    private final ItemStack[] pattern = emptyPattern();
    /** The grid contents the cached recipe was found for, so the recipe book is searched only when they change. */
    @Nullable private List<Item> cachedKey;
    @Nullable private CraftingRecipe cachedRecipe;

    public MechanicalCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_CRAFTER.get(), pos, state, WorkerLayout.MECHANICAL_CRAFTER);
    }

    @Override
    protected int progressPerMille() {
        return cooldown * 1000 / INTERVAL;
    }

    @Override
    protected int counter() {
        return crafts;
    }

    public int crafts() {
        return crafts;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof MechanicalCrafterBlockEntity crafter) crafter.tick(server);
    }

    private void tick(ServerLevel level) {
        if (level.hasNeighborSignal(worldPosition)) {
            stop(Status.PAUSED);
            return;
        }
        CraftingContainer grid = grid(false);
        CraftingRecipe recipe = recipe(level, grid);
        if (recipe != null) {
            remember(grid);
        } else {
            // Waiting for unstackable ingredients: look again with the remembered ones in place.
            grid = grid(true);
            recipe = recipe(level, grid);
        }
        if (recipe == null) {
            cooldown = 0;
            stop(Status.NO_RECIPE);
            return;
        }
        if (!stocked()) {
            cooldown = 0;
            stop(Status.SHORT);
            return;
        }
        ItemStack result = recipe.assemble(grid);
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result);
        for (ItemStack left : recipe.getRemainingItems(grid)) if (!left.isEmpty()) outputs.add(left);
        if (result.isEmpty() || !fits(outputs, layout().firstOutput(), layout().size())) {
            stop(Status.BLOCKED);
            return;
        }
        if (!requestPower(level, GP)) return;
        if (++cooldown < INTERVAL) return;
        cooldown = 0;
        int first = first();
        for (int i = 0; i < GRID; i++) {
            if (!items.get(first + i).isEmpty()) items.get(first + i).shrink(1);
        }
        for (ItemStack output : outputs) store(output, layout().firstOutput(), layout().size());
        crafts++;
        setChanged();
    }

    private int first() {
        return layout().first(WorkerLayout.Role.INPUT);
    }

    /**
     * Whether every slot can spare its ingredient: an unstackable one just has to be there, a stackable
     * one needs a second item so the last stays as the pattern.
     */
    private boolean stocked() {
        int first = first();
        for (int i = 0; i < GRID; i++) {
            ItemStack stack = items.get(first + i);
            if (stack.isEmpty()) {
                if (!pattern[i].isEmpty()) return false;
            } else if (stack.getCount() < (stack.getMaxStackSize() == 1 ? 1 : 2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The grid as a crafting table would see it, one of each item. With {@code remembered}, an empty slot
     * whose remembered ingredient is unstackable shows that ingredient, as if it were already back.
     */
    private CraftingContainer grid(boolean remembered) {
        CraftingContainer grid = new CraftingContainer(new NoMenu(), 3, 3);
        int first = first();
        for (int i = 0; i < GRID; i++) {
            ItemStack stack = items.get(first + i);
            if (stack.isEmpty() && remembered && pattern[i].getMaxStackSize() == 1) stack = pattern[i];
            ItemStack copy = stack.copy();
            if (!copy.isEmpty()) copy.setCount(1);
            grid.setItem(i, copy);
        }
        return grid;
    }

    private void remember(CraftingContainer grid) {
        boolean changed = false;
        for (int i = 0; i < GRID; i++) {
            ItemStack now = grid.getItem(i);
            if (!ItemStack.matches(pattern[i], now)) {
                pattern[i] = now.copy();
                changed = true;
            }
        }
        if (changed) super.setChanged();
    }

    @Nullable
    private CraftingRecipe recipe(ServerLevel level, CraftingContainer grid) {
        List<Item> key = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) key.add(grid.getItem(i).getItem());
        if (key.equals(cachedKey)) {
            if (cachedRecipe == null) return null;
            if (cachedRecipe.matches(grid, level)) return cachedRecipe;
        }
        Optional<CraftingRecipe> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        cachedKey = key;
        cachedRecipe = found.orElse(null);
        return cachedRecipe;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // A found recipe is re-checked before every craft; a failed search is retried after any change,
        // since the same items with different NBT can match where they did not.
        if (cachedRecipe == null) cachedKey = null;
    }

    /** A player emptying the whole grid also forgets the remembered layout. */
    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = super.removeItem(slot, amount);
        forgetIfEmptied(slot);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = super.removeItemNoUpdate(slot);
        forgetIfEmptied(slot);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        forgetIfEmptied(slot);
    }

    /**
     * Forgets the layout once its grid is empty, unless the layout is all unstackable ingredients: those
     * empty the grid on every craft (and in a rolled-back pipe transfer), so only a player clears them.
     */
    private void forgetIfEmptied(int slot) {
        int first = first();
        if (slot < first || slot >= first + GRID) return;
        for (int i = 0; i < GRID; i++) if (!items.get(first + i).isEmpty()) return;
        if (Arrays.stream(pattern).anyMatch(stack -> stack.getMaxStackSize() > 1)) Arrays.fill(pattern, ItemStack.EMPTY);
    }

    /**
     * Pipes and hoppers top up the grid: only a slot that holds this item (or is empty and remembered to
     * want it), only the emptiest of those, and never the output.
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        int first = first();
        int index = slot - first;
        if (index < 0 || index >= GRID) return false;
        ItemStack wanted = wanted(index);
        if (wanted.isEmpty() || !ItemStack.isSameItemSameTags(wanted, stack)) return false;
        int held = items.get(slot).getCount();
        if (held >= Math.min(stack.getMaxStackSize(), getMaxStackSize())) return false;
        for (int other = 0; other < GRID; other++) {
            if (other != index && ItemStack.isSameItemSameTags(wanted(other), wanted) && items.get(first + other).getCount() < held) {
                return false;
            }
        }
        return true;
    }

    /** What slot {@code index} of the grid takes: what it holds, or what it is remembered to want. */
    private ItemStack wanted(int index) {
        ItemStack held = items.get(first() + index);
        return held.isEmpty() ? pattern[index] : held;
    }

    /** Hoppers and pipes take only crafted items, never the ingredients. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return layout().isOutput(slot);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Cooldown", cooldown);
        tag.putInt("Crafts", crafts);
        ListTag saved = new ListTag();
        for (ItemStack stack : pattern) saved.add(stack.save(new CompoundTag()));
        tag.put("Pattern", saved);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cooldown = tag.getInt("Cooldown");
        crafts = tag.getInt("Crafts");
        ListTag saved = tag.getList("Pattern", Tag.TAG_COMPOUND);
        for (int i = 0; i < GRID; i++) pattern[i] = i < saved.size() ? ItemStack.of(saved.getCompound(i)) : ItemStack.EMPTY;
        cachedKey = null;
        cachedRecipe = null;
    }

    private static ItemStack[] emptyPattern() {
        ItemStack[] pattern = new ItemStack[GRID];
        Arrays.fill(pattern, ItemStack.EMPTY);
        return pattern;
    }

    /** The menu a {@link CraftingContainer} insists on; nothing ever opens it. */
    private static final class NoMenu extends AbstractContainerMenu {
        NoMenu() {
            super(null, -1);
        }

        @Override
        public void slotsChanged(Container container) { }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }
    }
}
