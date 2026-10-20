package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Upstream {@code WorkbenchCrafting}: a crafting grid that reads the blueprint while it is being matched, then
 * crafts by moving one exact copy of each blueprint item out of the materials into the grid. Upstream left
 * ingredient matching unfinished, so materials must equal the blueprint items, tags included.
 */
public final class WorkbenchCrafting extends CraftingContainer {
    private static final AbstractContainerMenu NO_MENU = new AbstractContainerMenu(null, -1) {
        @Override public boolean stillValid(Player player) { return false; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public void slotsChanged(Container container) {}
    };

    private final SlotRange blueprint;
    private final SlotRange materials;
    private final SlotRange results;
    private boolean blueprintDirty = true;
    private boolean materialsDirty = true;
    private boolean cachedHasRequirements;
    @Nullable private CraftingRecipe currentRecipe;
    private ItemStack assumedResult = ItemStack.EMPTY;

    public WorkbenchCrafting(int width, int height, SlotRange blueprint, SlotRange materials, SlotRange results) {
        super(NO_MENU, width, height);
        this.blueprint = blueprint;
        this.materials = materials;
        this.results = results;
    }

    @Override
    public ItemStack getItem(int index) {
        return blueprintDirty ? blueprint.get(index) : super.getItem(index);
    }

    public ItemStack getAssumedResult() {
        return assumedResult;
    }

    public void onBlueprintChanged() {
        blueprintDirty = true;
    }

    public void onMaterialsChanged() {
        materialsDirty = true;
    }

    /** Matches the blueprint again if it changed; true if it did. */
    public boolean tick(Level level) {
        if (!blueprintDirty) return false;
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, this, level);
        currentRecipe = recipe.orElse(null);
        assumedResult = currentRecipe == null ? ItemStack.EMPTY : currentRecipe.assemble(this);
        blueprintDirty = false;
        return true;
    }

    public boolean canCraft() {
        if (currentRecipe == null || blueprintDirty) return false;
        if (!results.canFullyAccept(assumedResult)) return false;
        if (materialsDirty) {
            materialsDirty = false;
            cachedHasRequirements = hasExactStacks();
        }
        return cachedHasRequirements;
    }

    public boolean craft(Level level, BlockPos pos) {
        if (blueprintDirty || currentRecipe == null) return false;
        clearGrid();
        for (int s = 0; s < getContainerSize(); s++) {
            ItemStack wanted = blueprint.get(s);
            if (wanted.isEmpty()) continue;
            ItemStack stack = materials.extract(wanted, 1, 1, false);
            if (stack.isEmpty()) {
                clearGrid();
                return false;
            }
            setItem(s, stack);
        }
        // Some recipes, such as vanilla fireworks, keep what matches found for assemble.
        if (!currentRecipe.matches(this, level)) return false;
        ItemStack result = currentRecipe.assemble(this);
        if (result.isEmpty()) {
            clearGrid();
            return false;
        }
        InventoryUtil.addToBestAcceptor(level, pos, null, results.insert(result, false));
        NonNullList<ItemStack> remainingStacks = currentRecipe.getRemainingItems(this);
        for (int s = 0; s < remainingStacks.size(); s++) {
            ItemStack inSlot = getItem(s);
            ItemStack remaining = remainingStacks.get(s);
            if (!inSlot.isEmpty()) {
                removeItem(s, 1);
                inSlot = getItem(s);
            }
            if (remaining.isEmpty()) continue;
            if (inSlot.isEmpty()) {
                setItem(s, remaining);
            } else if (ItemStack.isSameItemSameTags(inSlot, remaining)) {
                remaining.grow(inSlot.getCount());
                setItem(s, remaining);
            } else {
                InventoryUtil.addToBestAcceptor(level, pos, null, materials.insert(remaining, false));
            }
        }
        for (int s = 0; s < getContainerSize(); s++) {
            ItemStack inSlot = removeItemNoUpdate(s);
            if (!inSlot.isEmpty()) InventoryUtil.addToBestAcceptor(level, pos, null, materials.insert(inSlot, false));
        }
        return true;
    }

    /** Upstream {@code hasExactStacks}: the materials hold every blueprint item, as many times as the blueprint has it. */
    private boolean hasExactStacks() {
        List<ItemStack> required = new ArrayList<>();
        for (int s = 0; s < getContainerSize(); s++) {
            ItemStack wanted = blueprint.get(s);
            if (wanted.isEmpty()) continue;
            ItemStack found = null;
            for (ItemStack stack : required) {
                if (ItemStack.isSameItemSameTags(stack, wanted)) found = stack;
            }
            if (found == null) {
                required.add(wanted.copy());
            } else {
                found.grow(wanted.getCount());
            }
        }
        for (ItemStack stack : required) {
            ItemStack inInventory = materials.extract(stack, stack.getCount(), stack.getCount(), true);
            if (inInventory.isEmpty() || inInventory.getCount() != stack.getCount()) return false;
        }
        return true;
    }

    private void clearGrid() {
        for (int s = 0; s < getContainerSize(); s++) {
            ItemStack inSlot = super.getItem(s);
            if (inSlot.isEmpty()) continue;
            ItemStack leftover = materials.insert(inSlot, false);
            removeItem(s, inSlot.getCount() - leftover.getCount());
            if (!leftover.isEmpty()) return;
        }
    }
}
