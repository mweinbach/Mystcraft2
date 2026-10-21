package ports.buildcraft.silicon.plug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ports.buildcraft.BuildCraft;

/**
 * Upstream {@code FacadeStateManager}: which block states may be copied onto a facade. A block is refused when it
 * is in {@code buildcraft:facade_blacklist} (upstream's IMC disable list and the old facade blacklist), when it is
 * a fluid, or, other than glass, when a state has a block entity, is not a model or is not a full cube. States
 * that share an item keep the properties that differ between them, which the item's tooltip lists.
 */
public final class FacadeStateManager {
    public static final TagKey<Block> BLACKLIST = TagKey.create(Registry.BLOCK_REGISTRY, BuildCraft.id("facade_blacklist"));
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<BlockState> ORDER = Comparator
            .comparing((BlockState state) -> String.valueOf(Registry.BLOCK.getKey(state.getBlock())))
            .thenComparing(BlockState::toString);

    private static final Map<BlockState, FacadeBlockStateInfo> BY_STATE = new HashMap<>();
    private static final Map<StackKey, List<FacadeBlockStateInfo>> BY_STACK = new HashMap<>();
    private static final List<FacadeBlockStateInfo> VISIBLE = new ArrayList<>();
    private static FacadeBlockStateInfo defaultState = new FacadeBlockStateInfo(Blocks.AIR.defaultBlockState(), ItemStack.EMPTY, Set.of());
    private static Set<Block> scannedBlacklist = Set.of();
    private static Set<Block> override;
    private static boolean scanned;

    private FacadeStateManager() {}

    public static FacadeBlockStateInfo defaultState() {
        return defaultState;
    }

    /** The info for {@code state}, or the air info when that state is not a facade. */
    public static FacadeBlockStateInfo info(BlockState state) {
        refresh();
        FacadeBlockStateInfo info = BY_STATE.get(state);
        return info != null ? info : defaultState;
    }

    public static boolean isValid(BlockState state) {
        refresh();
        return BY_STATE.containsKey(state);
    }

    /** Every visible state whose required item matches {@code stack}, hollow and full both crafted from it. */
    public static List<FacadeBlockStateInfo> infosFor(ItemStack stack) {
        refresh();
        List<FacadeBlockStateInfo> infos = BY_STACK.get(StackKey.of(stack));
        return infos == null ? List.of() : infos;
    }

    public static List<FacadeBlockStateInfo> visible() {
        refresh();
        return VISIBLE;
    }

    public static void refresh() {
        if (override != null) {
            if (!scanned) scan(override);
            return;
        }
        Set<Block> blacklist = new HashSet<>();
        Registry.BLOCK.getTagOrEmpty(BLACKLIST).forEach(holder -> blacklist.add(holder.value()));
        if (scanned && blacklist.equals(scannedBlacklist)) return;
        scan(blacklist);
    }

    /**
     * Scans as if {@code blacklist} were the facade tag, until {@link #clearRescan()}. GameTests use this to check
     * that a tagged full cube is refused without shipping that block in the tag.
     */
    public static void rescan(Set<Block> blacklist) {
        override = Set.copyOf(blacklist);
        scan(override);
    }

    /** Drops a {@link #rescan} override and reads {@code buildcraft:facade_blacklist} again. */
    public static void clearRescan() {
        override = null;
        scanned = false;
        refresh();
    }

    private static void scan(Set<Block> blacklist) {
        BY_STATE.clear();
        BY_STACK.clear();
        VISIBLE.clear();
        defaultState = new FacadeBlockStateInfo(Blocks.AIR.defaultBlockState(), ItemStack.EMPTY, Set.of());
        List<Block> blocks = new ArrayList<>();
        Registry.BLOCK.forEach(blocks::add);
        blocks.sort(Comparator.comparing(block -> String.valueOf(Registry.BLOCK.getKey(block))));
        for (Block block : blocks) {
            try {
                scanBlock(block, blacklist);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping {} as a facade because scanning it failed", Registry.BLOCK.getKey(block), e);
            }
        }
        List<BlockState> states = new ArrayList<>(BY_STATE.keySet());
        states.sort(ORDER);
        for (BlockState state : states) {
            FacadeBlockStateInfo info = BY_STATE.get(state);
            if (info.visible) VISIBLE.add(info);
        }
        for (List<FacadeBlockStateInfo> infos : BY_STACK.values()) {
            infos.sort(Comparator.comparing(info -> info.state, ORDER));
        }
        scannedBlacklist = Set.copyOf(blacklist);
        scanned = true;
    }

    private static void scanBlock(Block block, Set<Block> blacklist) {
        if (blacklist.contains(block)) return;
        if (block instanceof LiquidBlock) return;
        boolean glass = block instanceof AbstractGlassBlock;
        Map<BlockState, ItemStack> used = new HashMap<>();
        Map<StackKey, Map<Property<?>, Comparable<?>>> varying = new HashMap<>();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (!glass && !stateAllowed(state)) continue;
            ItemStack required;
            try {
                required = block.getCloneItemStack(new FacadeBlockStateInfo.SingleBlockView(state), BlockPos.ZERO, state);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping facade state {} because its item threw", state, e);
                continue;
            }
            if (required.isEmpty()) required = new ItemStack(block);
            if (!required.isEmpty()) {
                required = required.copy();
                required.setCount(1);
            }
            used.put(state, required);
            StackKey key = StackKey.of(required);
            Map<Property<?>, Comparable<?>> vars = varying.get(key);
            if (vars == null) {
                vars = new HashMap<>();
                for (Property<?> property : state.getProperties()) vars.put(property, state.getValue(property));
                varying.put(key, vars);
            } else {
                for (Property<?> property : state.getProperties()) {
                    if (vars.get(property) != state.getValue(property)) vars.put(property, null);
                }
            }
        }
        for (Map<Property<?>, Comparable<?>> vars : varying.values()) vars.values().removeIf(value -> value != null);
        for (Map.Entry<BlockState, ItemStack> entry : used.entrySet()) {
            BlockState state = entry.getKey();
            ItemStack stack = entry.getValue();
            Map<Property<?>, Comparable<?>> vars = varying.get(StackKey.of(stack));
            Set<Property<?>> varyingProperties = vars == null ? Set.of() : Set.copyOf(vars.keySet());
            FacadeBlockStateInfo info = new FacadeBlockStateInfo(state, stack, varyingProperties);
            BY_STATE.put(state, info);
            if (!stack.isEmpty()) BY_STACK.computeIfAbsent(StackKey.of(stack), key -> new ArrayList<>()).add(info);
        }
    }

    private static boolean stateAllowed(BlockState state) {
        if (state.hasBlockEntity()) return false;
        if (state.getRenderShape() != RenderShape.MODEL) return false;
        try {
            return Block.isShapeFullBlock(state.getCollisionShape(new FacadeBlockStateInfo.SingleBlockView(state), BlockPos.ZERO));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Item and tag, ignoring count, so two stacks of the same block item share their facade states. */
    private record StackKey(Item item, CompoundTag tag) {
        static StackKey of(ItemStack stack) {
            return new StackKey(stack.getItem(), stack.getTag() == null ? null : stack.getTag().copy());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof StackKey other)) return false;
            return item == other.item && Objects.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            return 31 * item.hashCode() + Objects.hashCode(tag);
        }
    }
}
