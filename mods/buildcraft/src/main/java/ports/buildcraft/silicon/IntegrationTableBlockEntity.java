package ports.buildcraft.silicon;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.SlotRange;

/**
 * Upstream {@code TileIntegrationTable}: integrates the stacks around its centre slot into the centre stack,
 * putting the result in its result slot. Upstream let automation insert into the result slot but not take from
 * it; here automation takes results out and cannot insert them.
 */
public class IntegrationTableBlockEntity extends LaserTableBlockEntity {
    public static final int TARGET_SLOT = 0;
    public static final int TO_INTEGRATE_START = 1;
    public static final int TO_INTEGRATE_SLOTS = 8;
    public static final int RESULT_SLOT = TO_INTEGRATE_START + TO_INTEGRATE_SLOTS;
    public static final int SLOTS = RESULT_SLOT + 1;

    private final SlotRange toIntegrate = new SlotRange(this, TO_INTEGRATE_START, TO_INTEGRATE_SLOTS);
    @Nullable private ResourceLocation recipeId;

    public IntegrationTableBlockEntity(BlockPos pos, BlockState state) {
        super(BCSilicon.INTEGRATION_TABLE_TYPE.get(), pos, state, SLOTS);
    }

    @Override protected boolean canInsert(int slot) { return slot != RESULT_SLOT; }
    @Override protected boolean canExtract(int slot) { return true; }

    @Nullable
    private IntegrationRecipe recipe() {
        if (recipeId == null || level == null) return null;
        return level.getRecipeManager().byKey(recipeId).filter(IntegrationRecipe.class::isInstance).map(IntegrationRecipe.class::cast).orElse(null);
    }

    private boolean extract(IngredientStack item, List<IngredientStack> items, boolean simulate) {
        ItemStack targetStack = getItem(TARGET_SLOT);
        if (!item.contains(targetStack)) return false;
        if (!extract(toIntegrate, items, simulate, true)) return false;
        if (!simulate) {
            ItemStack left = targetStack.copy();
            left.shrink(item.count());
            setItem(TARGET_SLOT, left.isEmpty() ? ItemStack.EMPTY : left);
        }
        return true;
    }

    private boolean isSpaceEnough(ItemStack stack) {
        ItemStack output = getItem(RESULT_SLOT);
        return output.isEmpty() || (ItemStack.isSameItemSameTags(stack, output) && stack.getCount() + output.getCount() <= stack.getMaxStackSize());
    }

    /** Upstream {@code updateRecipe}: keeps the current recipe while it can still be made, else finds the first that can. */
    private void updateRecipe() {
        IntegrationRecipe recipe = recipe();
        if (recipe != null) {
            ItemStack output = getOutput();
            if (!output.isEmpty() && extract(recipe.getCenterStack(), recipe.getRequirements(output), true)) return;
        }
        ItemStack target = getItem(TARGET_SLOT);
        Optional<IntegrationRecipe> found = level.getRecipeManager().getAllRecipesFor(BCSilicon.INTEGRATION_TYPE.get()).stream()
                .sorted(Comparator.comparing(IntegrationRecipe::getId))
                .filter(candidate -> !candidate.getOutput(target, toIntegrate.stacks()).isEmpty())
                .findFirst();
        recipeId = found.map(IntegrationRecipe::getId).orElse(null);
    }

    /** What the current recipe would make, shown in the GUI's preview slot. */
    public ItemStack getOutput() {
        IntegrationRecipe recipe = recipe();
        return recipe != null ? recipe.getOutput(getItem(TARGET_SLOT), toIntegrate.stacks()) : ItemStack.EMPTY;
    }

    @Override
    public long getTarget() {
        IntegrationRecipe recipe = recipe();
        if (recipe == null) return 0;
        ItemStack output = getOutput();
        return isSpaceEnough(output) ? recipe.getRequiredMicroJoules(output) : 0;
    }

    @Override
    void serverTick() {
        super.serverTick();
        updateRecipe();
        long target = getTarget();
        if (target > 0 && power >= target) {
            IntegrationRecipe recipe = recipe();
            ItemStack output = getOutput();
            extract(recipe.getCenterStack(), recipe.getRequirements(output), false);
            ItemStack result = getItem(RESULT_SLOT);
            if (result.isEmpty()) {
                result = output.copy();
            } else {
                result = result.copy();
                result.grow(output.getCount());
            }
            setItem(RESULT_SLOT, result);
            power -= target;
        }
        setChanged();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new IntegrationTableMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        recipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }
}
