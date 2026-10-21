package ports.buildcraft.silicon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.lib.SlotRange;

/**
 * Upstream {@code TileAssemblyTable}: lists every assembly recipe its twelve slots could make. Saved recipes stay
 * listed and take turns being made whenever there are enough ingredients, each costing its recipe's laser power.
 * Upstream ordered its list by the sum of two string comparisons; this orders it by recipe id, then output.
 */
public class AssemblyTableBlockEntity extends LaserTableBlockEntity {
    public static final int SLOTS = 12;

    private final SlotRange inv = new SlotRange(this, 0, SLOTS);
    private final TreeMap<Instruction, EnumAssemblyRecipeState> recipesStates = new TreeMap<>();

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(BCSilicon.ASSEMBLY_TABLE_TYPE.get(), pos, state, SLOTS);
    }

    /** A recipe and one of its outputs. */
    public static final class Instruction implements Comparable<Instruction> {
        public final ResourceLocation recipeId;
        public final ItemStack output;
        private final String outputKey;

        Instruction(ResourceLocation recipeId, ItemStack output) {
            this.recipeId = recipeId;
            this.output = output.copy();
            this.outputKey = output.save(new CompoundTag()).toString();
        }

        @Override
        public int compareTo(Instruction other) {
            int byRecipe = recipeId.compareTo(other.recipeId);
            return byRecipe != 0 ? byRecipe : outputKey.compareTo(other.outputKey);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Instruction other && compareTo(other) == 0;
        }

        @Override
        public int hashCode() {
            return recipeId.hashCode() * 31 + outputKey.hashCode();
        }
    }

    @Override protected boolean canInsert(int slot) { return true; }
    @Override protected boolean canExtract(int slot) { return true; }

    private Optional<AssemblyRecipe> recipe(ResourceLocation id) {
        return level.getRecipeManager().byKey(id).filter(AssemblyRecipe.class::isInstance).map(AssemblyRecipe.class::cast);
    }

    /** The output the current recipe makes for a listed instruction, if a reload has not removed or changed it. */
    private Optional<ItemStack> currentOutput(AssemblyRecipe recipe, Instruction instruction) {
        ItemStack produced = recipe.produced(instruction.output);
        return produced.isEmpty() ? Optional.empty() : Optional.of(produced);
    }

    public Map<Instruction, EnumAssemblyRecipeState> recipesStates() {
        return recipesStates;
    }

    private void updateRecipes() {
        List<ItemStack> stacks = inv.stacks();
        for (AssemblyRecipe recipe : level.getRecipeManager().getAllRecipesFor(BCSilicon.ASSEMBLY_TYPE.get())) {
            for (ItemStack out : recipe.getOutputs(stacks)) {
                recipesStates.putIfAbsent(new Instruction(recipe.getId(), out), EnumAssemblyRecipeState.POSSIBLE);
            }
        }

        boolean findActive = false;
        for (Iterator<Map.Entry<Instruction, EnumAssemblyRecipeState>> iterator = recipesStates.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Instruction, EnumAssemblyRecipeState> entry = iterator.next();
            Instruction instruction = entry.getKey();
            Optional<AssemblyRecipe> recipe = recipe(instruction.recipeId);
            if (recipe.isEmpty() || currentOutput(recipe.get(), instruction).isEmpty()) {
                iterator.remove();
                continue;
            }
            EnumAssemblyRecipeState state = entry.getValue();
            boolean enough = extract(inv, recipe.get().getInputsFor(instruction.output), true, false);
            if (state == EnumAssemblyRecipeState.POSSIBLE) {
                if (!enough) {
                    iterator.remove();
                    continue;
                }
            } else if (enough) {
                if (state == EnumAssemblyRecipeState.SAVED) state = EnumAssemblyRecipeState.SAVED_ENOUGH;
            } else {
                state = EnumAssemblyRecipeState.SAVED;
            }
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) findActive = true;
            entry.setValue(state);
        }
        if (!findActive) {
            for (Map.Entry<Instruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
                if (entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH) {
                    entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
                    break;
                }
            }
        }
    }

    @Nullable
    private Instruction getActiveRecipe() {
        for (Map.Entry<Instruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
            if (entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) return entry.getKey();
        }
        return null;
    }

    /** Upstream {@code activateNextRecipe}: hands the active flag to the next saved recipe with enough, wrapping. */
    private void activateNextRecipe() {
        Instruction activeRecipe = getActiveRecipe();
        if (activeRecipe == null) return;
        long enoughCount = recipesStates.values().stream()
                .filter(state -> state == EnumAssemblyRecipeState.SAVED_ENOUGH || state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE).count();
        if (enoughCount <= 1) return;
        int index = 0;
        int activeIndex = 0;
        boolean isActiveLast = false;
        for (Map.Entry<Instruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
            EnumAssemblyRecipeState state = entry.getValue();
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH) isActiveLast = false;
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH);
                activeIndex = index;
                isActiveLast = true;
            }
            index++;
        }
        index = 0;
        for (Map.Entry<Instruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
            if (entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH && !entry.getKey().recipeId.equals(activeRecipe.recipeId)
                    && (index > activeIndex || isActiveLast)) {
                entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
                break;
            }
            index++;
        }
    }

    /** Upstream's GUI click: a listed recipe toggles between possible and saved. */
    public void toggle(int index) {
        if (index < 0 || index >= recipesStates.size()) return;
        Instruction instruction = new ArrayList<>(recipesStates.keySet()).get(index);
        EnumAssemblyRecipeState state = recipesStates.get(instruction);
        recipesStates.put(instruction, state == EnumAssemblyRecipeState.POSSIBLE ? EnumAssemblyRecipeState.SAVED : EnumAssemblyRecipeState.POSSIBLE);
        setChanged();
    }

    @Override
    public long getTarget() {
        Instruction instruction = getActiveRecipe();
        if (instruction == null || level == null) return 0;
        return recipe(instruction.recipeId).map(recipe -> recipe.getRequiredMicroJoulesFor(instruction.output)).orElse(0L);
    }

    @Override
    void serverTick() {
        super.serverTick();
        updateRecipes();
        long target = getTarget();
        if (target > 0 && power >= target) {
            Instruction instruction = getActiveRecipe();
            AssemblyRecipe recipe = recipe(instruction.recipeId).orElseThrow();
            ItemStack output = currentOutput(recipe, instruction).orElseThrow();
            extract(inv, recipe.getInputsFor(output), false, false);
            InventoryUtil.addToBestAcceptor(level, worldPosition, null, output.copy());
            power -= target;
            activateNextRecipe();
        }
        setChanged();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AssemblyTableMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        recipesStates.forEach((instruction, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("recipe", instruction.recipeId.toString());
            entry.put("output", instruction.output.save(new CompoundTag()));
            entry.putInt("state", state.ordinal());
            list.add(entry);
        });
        tag.put("recipes_states", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        recipesStates.clear();
        ListTag list = tag.getList("recipes_states", Tag.TAG_COMPOUND);
        EnumAssemblyRecipeState[] states = EnumAssemblyRecipeState.values();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("recipe"));
            int state = entry.getInt("state");
            if (id == null || !entry.contains("output") || state < 0 || state >= states.length) continue;
            recipesStates.put(new Instruction(id, ItemStack.of(entry.getCompound("output"))), states[state]);
        }
    }
}
