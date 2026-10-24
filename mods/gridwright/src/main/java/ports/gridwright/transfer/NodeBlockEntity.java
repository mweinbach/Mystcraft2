package ports.gridwright.transfer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A node's state and behaviour. Every {@link #INTERVAL} ticks (sooner with speed upgrades) it makes
 * one operation: a transfer node moves from the inventory or tank it faces to the next target on its
 * pipe network that accepts, a retrieval node the other way round, taking turns through the targets.
 * With a world-interaction upgrade and nothing to serve at its front, a node acts on that block
 * instead: an item transfer node mines it, an item retrieval node places a block from the network,
 * a fluid transfer node pumps a source and a fluid retrieval node pours one, each only where the
 * loader's block-break event lets the node's owner change that block. The energy transfer node moves
 * energy every tick instead, and any node can be set to work only with, or only without, a redstone signal.
 */
public class NodeBlockEntity extends GridBlockEntity implements MenuProvider {

    public static final int UPGRADE_SLOTS = 4;
    public static final int FILTER_SLOT = 4;
    public static final int SLOTS = 5;
    /** Ticks per operation without speed upgrades; each speed upgrade adds one operation per interval. */
    public static final int INTERVAL = 20;
    public static final int MAX_OPERATIONS_PER_TICK = 4;
    public static final int DATA_COUNT = 4;
    private static final int NETWORK_REFRESH_TICKS = 100;
    /** What world interaction mines with: harvests what a diamond pickaxe harvests, without enchantments. */
    private static final ItemStack TOOL = new ItemStack(Items.DIAMOND_PICKAXE);

    /** Whether the node's upgrades are powered, as shown on its screen. */
    public enum Power { NONE, POWERED, UNPOWERED, NO_OWNER }

    /** When a redstone signal lets the node work (phase 3). */
    public enum Redstone {
        IGNORED, WITH_SIGNAL, WITHOUT_SIGNAL;

        public boolean allows(boolean signal) {
            return this == IGNORED || (this == WITH_SIGNAL) == signal;
        }

        public Redstone next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final SimpleContainer inventory = new SimpleContainer(SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }
    };
    private int progress;
    private int cursor;
    /** Whether it asked for power last tick, so the grid totals it reads include its own demand. */
    private boolean active;
    private Power power = Power.NONE;
    private long operations;
    private Redstone redstone = Redstone.IGNORED;
    @Nullable private ItemFilter filter;
    @Nullable private List<PipeNetwork.Target> targets;
    private long targetsEpoch;
    private long targetsTick;
    @Nullable private Direction targetsFront;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.min(0xFFFF, Math.round(upgradeCost() * 10));
                case 1 -> power.ordinal();
                case 2 -> targets == null ? 0 : Math.min(0xFFFF, targets.size());
                case 3 -> redstone.ordinal();
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

    public NodeBlockEntity(BlockPos pos, BlockState state) {
        super(TransferRegistry.NODE_ENTITY.get(), pos, state);
        inventory.addListener(container -> {
            filter = null;
            setChanged();
        });
    }

    public static boolean accepts(int slot, ItemStack stack) {
        return slot == FILTER_SLOT ? stack.getItem() instanceof FilterItem : slot < UPGRADE_SLOTS && stack.getItem() instanceof UpgradeItem;
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public NodeKind kind() {
        return getBlockState().getBlock() instanceof NodeBlock node ? node.kind() : NodeKind.ITEM_TRANSFER;
    }

    public Power power() {
        return power;
    }

    public Redstone redstone() {
        return redstone;
    }

    /** Switches to the next redstone mode, as the screen's button does. */
    public void cycleRedstone() {
        redstone = redstone.next();
        setChanged();
    }

    /** Successful operations since the node was placed or loaded. */
    public long operations() {
        return operations;
    }

    /** Counted upgrades of {@code kind}; extra copies beyond {@link UpgradeKind#maxCounted()} do nothing. */
    public int upgrades(UpgradeKind kind) {
        int count = 0;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == kind) count += stack.getCount();
        }
        return Math.min(kind.maxCounted(), count);
    }

    /** Grid Power the installed upgrades ask for. */
    public double upgradeCost() {
        double cost = 0;
        for (UpgradeKind kind : UpgradeKind.values()) cost += upgrades(kind) * kind.gp();
        return cost;
    }

    public ItemFilter filter() {
        if (filter == null) filter = ItemFilter.of(inventory.getItem(FILTER_SLOT));
        return filter;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NodeBlockEntity node) {
        if (level instanceof ServerLevel server) node.tick(server, state);
    }

    private void tick(ServerLevel level, BlockState state) {
        if (!redstone.allows(level.hasNeighborSignal(worldPosition))) {
            // Switched off: no work and no Grid Power drawn for upgrades.
            draw(level, 0);
            return;
        }
        boolean powered = draw(level, upgradeCost());
        if (kind().energy()) {
            if (moveEnergy(level, state.getValue(NodeBlock.FACING), powered)) operations++;
            return;
        }
        int speed = powered ? upgrades(UpgradeKind.SPEED) : 0;
        boolean stack = powered && upgrades(UpgradeKind.STACK) > 0;
        boolean world = powered && upgrades(UpgradeKind.WORLD_INTERACTION) > 0;
        progress += 1 + speed;
        if (progress < INTERVAL) return;
        int count = Math.min(MAX_OPERATIONS_PER_TICK, progress / INTERVAL);
        progress %= INTERVAL;
        Direction front = state.getValue(NodeBlock.FACING);
        for (int i = 0; i < count; i++) {
            if (!operate(level, front, stack, world)) {
                // Nothing to do: wait a full interval before looking again.
                progress = 0;
                break;
            }
            operations++;
        }
    }

    /** Asks the owner's grid for {@code cost}; returns whether the upgrades may work this tick. */
    private boolean draw(ServerLevel level, double cost) {
        if (cost <= 0) {
            active = false;
            power = Power.NONE;
            return false;
        }
        String grid = gridKey(level.getServer());
        if (grid == null) {
            active = false;
            power = Power.NO_OWNER;
            return false;
        }
        GridManager manager = GridManager.get(level.getServer());
        boolean powered = active && manager.snapshot(grid).powered();
        manager.addDemand(grid, cost);
        active = true;
        power = powered ? Power.POWERED : Power.UNPOWERED;
        return powered;
    }

    private boolean operate(ServerLevel level, Direction front, boolean stack, boolean world) {
        BlockPos target = worldPosition.relative(front);
        Direction face = front.getOpposite();
        NodeKind kind = kind();
        List<PipeNetwork.Target> network = network(level, front);
        if (kind.fluid()) {
            if (TransferPlatform.hasFluids(level, target, face)) {
                return moveFluids(level, kind, target, face, network, stack ? NodeKind.FLUID_STACKED : NodeKind.FLUID);
            }
            if (!world) return false;
            return kind.retrieval() ? pour(level, target, network) : pump(level, target, network);
        }
        if (TransferPlatform.hasItems(level, target, face)) {
            return moveItems(level, kind, target, face, network, stack ? NodeKind.ITEMS_STACKED : NodeKind.ITEMS);
        }
        if (!world) return false;
        return kind.retrieval() ? place(level, target, front, network) : mine(level, target, network);
    }

    /**
     * Moves energy from the store the node faces to the stores on its network, every tick: up to
     * {@link NodeKind#ENERGY} per tick plus as much again per powered speed upgrade, times
     * {@link NodeKind#ENERGY_STACK_FACTOR} with a powered stack upgrade, taking turns through the targets.
     * Only what the targets accept is taken from the source.
     */
    private boolean moveEnergy(ServerLevel level, Direction front, boolean powered) {
        long rate = NodeKind.ENERGY * (1 + (powered ? upgrades(UpgradeKind.SPEED) : 0));
        if (powered && upgrades(UpgradeKind.STACK) > 0) rate *= NodeKind.ENERGY_STACK_FACTOR;
        BlockPos source = worldPosition.relative(front);
        Direction face = front.getOpposite();
        long available = TransferPlatform.extractEnergy(level, source, face, rate, true);
        if (available <= 0) return false;
        List<PipeNetwork.Target> network = network(level, front);
        long moved = 0;
        for (int k = 0; k < network.size() && moved < available; k++) {
            int index = (cursor + k) % network.size();
            PipeNetwork.Target other = network.get(index);
            long accepted = TransferPlatform.insertEnergy(level, other.pos(), other.face(), available - moved, true);
            if (accepted <= 0) continue;
            long taken = TransferPlatform.extractEnergy(level, source, face, accepted, false);
            if (taken <= 0) break;
            long given = TransferPlatform.insertEnergy(level, other.pos(), other.face(), taken, false);
            // A target that took less than it promised: offer the rest back to the source.
            if (given < taken) TransferPlatform.insertEnergy(level, source, face, taken - given, false);
            moved += given;
            cursor = (index + 1) % network.size();
        }
        return moved > 0;
    }

    private List<PipeNetwork.Target> network(ServerLevel level, Direction front) {
        long epoch = PipeNetwork.epoch();
        if (targets == null || targetsEpoch != epoch || targetsFront != front || level.getGameTime() - targetsTick >= NETWORK_REFRESH_TICKS) {
            targets = PipeNetwork.targets(level, worldPosition, front, kind().resource());
            targetsEpoch = epoch;
            targetsTick = level.getGameTime();
            targetsFront = front;
        }
        return targets;
    }

    private boolean moveItems(ServerLevel level, NodeKind kind, BlockPos target, Direction face, List<PipeNetwork.Target> network, int max) {
        ItemFilter filter = filter();
        for (int k = 0; k < network.size(); k++) {
            int index = (cursor + k) % network.size();
            PipeNetwork.Target other = network.get(index);
            int moved = kind.retrieval()
                    ? Transfers.items(level, other.pos(), other.face(), target, face, filter::test, max, worldPosition)
                    : Transfers.items(level, target, face, other.pos(), other.face(), filter::test, max, worldPosition);
            if (moved > 0) {
                cursor = (index + 1) % network.size();
                return true;
            }
        }
        return false;
    }

    private boolean moveFluids(ServerLevel level, NodeKind kind, BlockPos target, Direction face, List<PipeNetwork.Target> network, long max) {
        ItemFilter filter = filter();
        for (int k = 0; k < network.size(); k++) {
            int index = (cursor + k) % network.size();
            PipeNetwork.Target other = network.get(index);
            long moved = kind.retrieval()
                    ? Transfers.fluids(level, other.pos(), other.face(), target, face, filter::testFluid, max)
                    : Transfers.fluids(level, target, face, other.pos(), other.face(), filter::testFluid, max);
            if (moved > 0) {
                cursor = (index + 1) % network.size();
                return true;
            }
        }
        return false;
    }

    /** Breaks the block in front into the network, if every drop fits and the owner may break it. */
    private boolean mine(ServerLevel level, BlockPos target, List<PipeNetwork.Target> network) {
        BlockState state = level.getBlockState(target);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        if (state.getDestroySpeed(level, target) < 0) return false;
        if (state.requiresCorrectToolForDrops() && !TOOL.isCorrectToolForDrops(state)) return false;
        ItemFilter filter = filter();
        if (filter != ItemFilter.NONE && !filter.test(new ItemStack(state.getBlock()))) return false;
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, target, null, null, TOOL.copy()));
        drops.removeIf(ItemStack::isEmpty);
        if (drops.isEmpty() || network.isEmpty()) return false;
        // Together, not one by one: wheat and its seeds must not both count on one free slot.
        if (!TransferPlatform.fitsAll(level, network, drops)) return false;
        if (!mayChange(level, target)) return false;
        level.destroyBlock(target, false);
        for (ItemStack drop : drops) {
            ItemStack left = Transfers.deliver(level, network, drop);
            if (!left.isEmpty()) Block.popResource(level, worldPosition, left);
        }
        return true;
    }

    /**
     * Places a block item from the network at the front, if the space is free and the owner may build.
     * A kind that will not place here (a sapling over stone) is skipped for the rest of the operation,
     * so it cannot hide placeable kinds stored after it.
     */
    private boolean place(ServerLevel level, BlockPos target, Direction front, List<PipeNetwork.Target> network) {
        if (network.isEmpty() || !level.getBlockState(target).getMaterial().isReplaceable()) return false;
        if (!mayChange(level, target)) return false;
        ItemFilter filter = filter();
        List<ItemStack> rejected = new ArrayList<>();
        Predicate<ItemStack> wanted = stack -> stack.getItem() instanceof BlockItem && filter.test(stack)
                && rejected.stream().noneMatch(kind -> ItemStack.isSameItemSameTags(kind, stack));
        for (int k = 0; k < network.size(); k++) {
            int index = (cursor + k) % network.size();
            PipeNetwork.Target other = network.get(index);
            while (true) {
                ItemStack probe = TransferPlatform.extractItems(level, other.pos(), other.face(), wanted, 1, true);
                if (probe.isEmpty()) break;
                // Take the item first so a block is never placed without its item leaving the network.
                ItemStack taken = TransferPlatform.extractItems(level, other.pos(), other.face(),
                        stack -> ItemStack.isSameItemSameTags(stack, probe), 1, false);
                if (taken.isEmpty()) {
                    rejected.add(probe);
                    continue;
                }
                // A playerless context that still answers "which way is the placer looking": the node's front.
                BlockPlaceContext context = new DirectionalPlaceContext(level, target, front, taken.copy(), front.getOpposite());
                if (tryPlace((BlockItem) taken.getItem(), context)) {
                    cursor = (index + 1) % network.size();
                    return true;
                }
                rejected.add(taken.copy());
                ItemStack left = TransferPlatform.insertItems(level, other.pos(), other.face(), taken, false);
                if (!left.isEmpty()) Block.popResource(level, worldPosition, left);
            }
        }
        return false;
    }

    /**
     * Places through the item, as a dispenser would. Some blocks assume a player is placing them; one
     * that throws counts as placed only if it got as far as setting the block, so the caller returns
     * the item otherwise instead of crashing the tick, and never both places it and returns it.
     */
    private static boolean tryPlace(BlockItem item, BlockPlaceContext context) {
        try {
            return item.place(context).consumesAction();
        } catch (RuntimeException e) {
            return !context.getLevel().getBlockState(context.getClickedPos()).getMaterial().isReplaceable();
        }
    }

    /** Pumps a fluid source at the front into the first target that takes a whole bucket. */
    private boolean pump(ServerLevel level, BlockPos target, List<PipeNetwork.Target> network) {
        FluidState fluidState = level.getFluidState(target);
        BlockState state = level.getBlockState(target);
        if (!fluidState.isSource() || !(state.getBlock() instanceof BucketPickup pickup)) return false;
        Fluid fluid = fluidState.getType();
        if (!filter().testFluid(fluid)) return false;
        PipeNetwork.Target destination = null;
        for (PipeNetwork.Target other : network) {
            if (TransferPlatform.insertFluid(level, other.pos(), other.face(), fluid, NodeKind.FLUID_STACKED, true) == NodeKind.FLUID_STACKED) {
                destination = other;
                break;
            }
        }
        if (destination == null || !mayChange(level, target)) return false;
        if (pickup.pickupBlock(level, target, state).isEmpty()) return false;
        TransferPlatform.insertFluid(level, destination.pos(), destination.face(), fluid, NodeKind.FLUID_STACKED, false);
        return true;
    }

    /**
     * Pours a bucket of fluid from the network as a source block at the front. A fluid kind a storage
     * cannot give a whole bucket of is skipped for that storage only, so it cannot hide a full tank of
     * another kind stored after it.
     */
    private boolean pour(ServerLevel level, BlockPos target, List<PipeNetwork.Target> network) {
        BlockState existing = level.getBlockState(target);
        if (network.isEmpty() || !existing.isAir() && !(existing.getMaterial().isReplaceable() && existing.getFluidState().isEmpty())) return false;
        if (!mayChange(level, target)) return false;
        ItemFilter filter = filter();
        boolean ultraWarm = level.dimensionType().ultraWarm();
        for (int k = 0; k < network.size(); k++) {
            int index = (cursor + k) % network.size();
            PipeNetwork.Target other = network.get(index);
            List<Fluid> partial = new ArrayList<>();
            Predicate<Fluid> wanted = fluid -> filter.testFluid(fluid) && pourable(fluid)
                    && !(ultraWarm && fluid.is(FluidTags.WATER)) && partial.stream().noneMatch(fluid::isSame);
            while (true) {
                FluidAmount probe = TransferPlatform.extractFluid(level, other.pos(), other.face(), wanted, NodeKind.FLUID_STACKED, true);
                if (probe.isEmpty()) break;
                if (probe.millibuckets() < NodeKind.FLUID_STACKED) {
                    partial.add(probe.fluid());
                    continue;
                }
                FluidAmount taken = TransferPlatform.extractFluid(level, other.pos(), other.face(), fluid -> fluid.isSame(probe.fluid()),
                        NodeKind.FLUID_STACKED, false);
                if (taken.millibuckets() < NodeKind.FLUID_STACKED) {
                    if (!taken.isEmpty()) TransferPlatform.insertFluid(level, other.pos(), other.face(), taken.fluid(), taken.millibuckets(), false);
                    partial.add(probe.fluid());
                    continue;
                }
                if (!existing.isAir()) level.destroyBlock(target, true);
                if (!level.setBlock(target, probe.fluid().defaultFluidState().createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE)) {
                    TransferPlatform.insertFluid(level, other.pos(), other.face(), taken.fluid(), taken.millibuckets(), false);
                    return false;
                }
                cursor = (index + 1) % network.size();
                return true;
            }
        }
        return false;
    }

    private static boolean pourable(Fluid fluid) {
        FluidState state = fluid.defaultFluidState();
        return state.isSource() && !state.createLegacyBlock().isAir();
    }

    /** World interaction needs a buildable target and an owner whom the world border, spawn protection and claim mods allow there. */
    private boolean mayChange(ServerLevel level, BlockPos target) {
        if (owner() == null || !level.isInWorldBounds(target) || !level.getWorldBorder().isWithinBounds(target)) return false;
        String name = ownerName().isEmpty() ? "[Gridwright]" : ownerName();
        return TransferPlatform.mayBreak(level, target, new GameProfile(owner(), name));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new NodeMenu(containerId, playerInventory, inventory, data, this::stillValid, this::cycleRedstone);
    }

    private boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOTS; slot++) items.set(slot, inventory.getItem(slot));
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Progress", progress);
        tag.putInt("Cursor", cursor);
        if (redstone != Redstone.IGNORED) tag.putInt("Redstone", redstone.ordinal());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        for (int slot = 0; slot < SLOTS; slot++) inventory.setItem(slot, items.get(slot));
        progress = Math.max(0, tag.getInt("Progress")) % INTERVAL;
        cursor = Math.max(0, tag.getInt("Cursor"));
        Redstone[] modes = Redstone.values();
        redstone = modes[Math.floorMod(tag.getInt("Redstone"), modes.length)];
        filter = null;
        targets = null;
    }
}
