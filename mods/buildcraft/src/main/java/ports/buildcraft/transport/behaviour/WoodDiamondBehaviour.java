package ports.buildcraft.transport.behaviour;

import java.util.Arrays;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.platform.ItemFluids;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.transport.DiamondWoodPipeMenu;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.FluidFlow;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.pluggable.PluggableItem;

/**
 * Upstream {@code PipeBehaviourWoodDiamond}: a wooden pipe with nine phantom filter slots. In whitelist mode it
 * pulls only matching items (anything when every slot is empty), in blacklist mode only items that match no
 * filter, and in round robin mode the item in the current filter slot, stepping to the next filled slot after
 * each extraction. Each extraction moves one item, as upstream. The fluid variant reads the filters as fluid
 * containers and has no round robin mode.
 */
public class WoodDiamondBehaviour extends WoodBehaviour {
    public static final int FILTER_COUNT = 9;

    public enum FilterMode {
        WHITE_LIST,
        BLACK_LIST,
        ROUND_ROBIN;

        public static FilterMode get(int index) {
            return switch (index) {
                case 1 -> BLACK_LIST;
                case 2 -> ROUND_ROBIN;
                default -> WHITE_LIST;
            };
        }
    }

    public final SimpleContainer filters = new SimpleContainer(FILTER_COUNT) {
        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (!loading) onSlotChanged(slot, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            pipe.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !pipe.isRemoved();
        }
    };
    private boolean loading;
    private FilterMode filterMode = FilterMode.WHITE_LIST;
    private int currentFilter;
    private boolean filterValid;

    public WoodDiamondBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    public FilterMode filterMode() {
        return filterMode;
    }

    public int currentFilter() {
        return currentFilter;
    }

    public boolean filterValid() {
        return filterValid;
    }

    /** True for the item pipe; upstream's fluid pipe has no round robin mode. */
    public boolean allowsRoundRobin() {
        return pipe.flow() instanceof ItemFlow;
    }

    public void setFilterMode(FilterMode mode) {
        if (mode == FilterMode.ROUND_ROBIN && !allowsRoundRobin()) return;
        if (filterMode == mode) return;
        filterMode = mode;
        pipe.setChanged();
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        ItemStack held = player.getItemInHand(hand);
        if (Wrenches.isWrench(held)) return super.onUse(player, hand, part);
        if (held.getItem() instanceof PluggableItem) return InteractionResult.PASS;
        boolean items = allowsRoundRobin();
        Platform.get().openMenu(player, new SimpleMenuProvider(
                (id, inventory, p) -> new DiamondWoodPipeMenu(id, inventory, pipe.getBlockPos(), this),
                pipe.getBlockState().getBlock().getName()), buffer -> {
                    buffer.writeBlockPos(pipe.getBlockPos());
                    buffer.writeBoolean(items);
                });
        return InteractionResult.SUCCESS;
    }

    private void onSlotChanged(int slot, ItemStack after) {
        if (!after.isEmpty()) {
            if (!filterValid) {
                currentFilter = slot;
                filterValid = true;
            }
        } else if (slot == currentFilter) {
            advanceFilter();
        }
    }

    private boolean hasFilters() {
        return !filters.isEmpty();
    }

    private boolean matchesAny(ItemStack stack) {
        for (int i = 0; i < FILTER_COUNT; i++) {
            ItemStack filter = filters.getItem(i);
            if (!filter.isEmpty() && ItemStack.isSameItemSameTags(filter, stack)) return true;
        }
        return false;
    }

    private Predicate<ItemStack> stackFilter() {
        return switch (filterMode) {
            case WHITE_LIST -> hasFilters() ? this::matchesAny : stack -> true;
            case BLACK_LIST -> stack -> !matchesAny(stack);
            case ROUND_ROBIN -> stack -> {
                ItemStack filter = filters.getItem(currentFilter);
                return !filter.isEmpty() && ItemStack.isSameItemSameTags(filter, stack);
            };
        };
    }

    @Override
    protected int extractItems(ItemFlow flow, Direction dir, int count, boolean simulate) {
        if (filters.getItem(currentFilter).isEmpty()) advanceFilter();
        int extracted = flow.tryExtractItems(1, dir, null, stackFilter(), simulate);
        if (extracted > 0 && filterMode == FilterMode.ROUND_ROBIN && !simulate) advanceFilter();
        return extracted;
    }

    @Override
    protected FluidAmount extractFluid(FluidFlow flow, Direction dir, int millibuckets, boolean simulate) {
        if (filters.getItem(currentFilter).isEmpty()) advanceFilter();
        switch (filterMode) {
            case WHITE_LIST -> {
                if (!hasFilters()) return flow.tryExtractFluid(millibuckets, dir, simulate);
                FluidAmount extracted = flow.tryExtractFluidAdv(millibuckets, dir, this::matchesAnyFluid, simulate);
                if (!extracted.isEmpty()) return extracted;
                for (int i = 0; i < FILTER_COUNT; i++) {
                    Fluid fluid = containedFluid(filters.getItem(i));
                    if (fluid == null) continue;
                    extracted = flow.tryExtractFluid(millibuckets, dir, fluid, simulate);
                    if (!extracted.isEmpty()) return extracted;
                }
                return FluidAmount.EMPTY;
            }
            case BLACK_LIST -> {
                return flow.tryExtractFluidAdv(millibuckets, dir, fluid -> !matchesAnyFluid(fluid), simulate);
            }
            default -> {
                return FluidAmount.EMPTY;
            }
        }
    }

    /** Upstream {@code ArrayFluidFilter}: a fluid matches when any filter container holds it. */
    private boolean matchesAnyFluid(Fluid fluid) {
        for (int i = 0; i < FILTER_COUNT; i++) {
            Fluid contained = containedFluid(filters.getItem(i));
            if (contained != null && contained.isSame(fluid)) return true;
        }
        return false;
    }

    @Nullable
    private static Fluid containedFluid(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ItemFluids fluids = Platform.get().itemFluids(stack);
        if (fluids == null) return null;
        FluidAmount contained = fluids.drain(Integer.MAX_VALUE, true);
        return contained.isEmpty() ? null : contained.fluid();
    }

    private void advanceFilter() {
        int lastFilter = currentFilter;
        filterValid = false;
        while (true) {
            currentFilter++;
            if (currentFilter >= FILTER_COUNT) currentFilter = 0;
            if (!filters.getItem(currentFilter).isEmpty()) {
                filterValid = true;
                break;
            }
            if (currentFilter == lastFilter) break;
        }
        if (lastFilter != currentFilter) pipe.setChanged();
    }

    @Override
    public void save(CompoundTag tag) {
        super.save(tag);
        ListTag list = new ListTag();
        for (int i = 0; i < FILTER_COUNT; i++) {
            ItemStack stack = filters.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = stack.save(new CompoundTag());
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        tag.put("filters", list);
        tag.putByte("mode", (byte) filterMode.ordinal());
        tag.putByte("currentFilter", (byte) currentFilter);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ListTag list = tag.getList("filters", Tag.TAG_COMPOUND);
        ItemStack[] loaded = new ItemStack[FILTER_COUNT];
        Arrays.fill(loaded, ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < FILTER_COUNT) loaded[slot] = ItemStack.of(entry);
        }
        loading = true;
        for (int i = 0; i < FILTER_COUNT; i++) filters.setItem(i, loaded[i]);
        loading = false;
        filterMode = FilterMode.get(tag.getByte("mode"));
        currentFilter = (tag.getByte("currentFilter") & 0xFF) % FILTER_COUNT;
        filterValid = hasFilters();
    }
}
