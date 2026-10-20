package ports.buildcraft.transport.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.lib.Injectable;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.transport.ItemEntry;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.behaviour.PipeBehaviour;

/**
 * Upstream {@code PipeFlowItems}. Items travel to the pipe centre, pick an exit through the
 * behaviour's side check, split and speed hooks, then travel out into the next pipe or inventory.
 */
public final class ItemFlow extends PipeFlow implements Injectable {
    public static final double EXTRACT_SPEED = 0.08;
    /** Upstream {@code PipeBehaviourStone.SPEED_DELTA}, the default slowdown. */
    private static final double DEFAULT_SPEED_DELTA = 0.008;
    private static final int MAX_CLIENT_ITEMS = 64;

    private final DelayedList<TravellingItem> items = new DelayedList<>();
    private final List<ClientItem> clientItems = new ArrayList<>();
    @Nullable
    private ListTag pendingLoad;

    public ItemFlow(PipeBlockEntity pipe) {
        super(pipe);
    }

    private Level level() {
        return pipe.getLevel();
    }

    private long now() {
        return level().getGameTime();
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof ItemFlow;
    }

    @Override
    public boolean canConnectTile(Direction face, BlockEntity tile) {
        boolean blockedPipe = tile instanceof PipeBlockEntity other && other.isBlocked(face.getOpposite());
        if (tile instanceof Injectable && !blockedPipe) return true;
        return Ports.items(level(), tile.getBlockPos(), face.getOpposite()) != null;
    }

    // Extraction (wooden pipes).

    /** @return the number of items extracted from the inventory on {@code from}. */
    public int tryExtractItems(int count, Direction from, Predicate<ItemStack> filter, boolean simulate) {
        if (from == null || count <= 0) return 0;
        ItemPort port = Ports.items(level(), pipe.getBlockPos().relative(from), from.getOpposite());
        if (port == null) return 0;
        ItemStack possible = port.extract(filter, count, true);
        if (possible.isEmpty()) return 0;
        count = Math.min(count, Math.min(possible.getCount(), possible.getMaxStackSize()));
        ItemStack stack = port.extract(filter, count, simulate);
        if (stack.isEmpty()) return 0;
        if (!simulate) insertItemEvents(stack, EXTRACT_SPEED, from);
        return stack.getCount();
    }

    // Ticking.

    @Override
    public void serverTick() {
        long currentTime = now();
        for (TravellingItem item : items.advance()) {
            if (item.tickFinished > currentTime) {
                items.add((int) (item.tickFinished - currentTime), item);
                continue;
            }
            if (item.toCenter) onItemReachCenter(item);
            else onItemReachEnd(item);
        }
    }

    private void onItemReachCenter(TravellingItem item) {
        PipeBehaviour behaviour = pipe.behaviour();
        ItemStack stack = behaviour.onItemReachCenter(item.stack, item.side);
        if (stack.isEmpty()) return;
        SideCheck sideCheck = new SideCheck(stack, item.side);
        sideCheck.disallow(item.side);
        for (Direction face : Direction.values()) {
            if (item.tried.contains(face) || !pipe.isConnected(face)) sideCheck.disallow(face);
        }
        behaviour.itemSideCheck(sideCheck);
        List<EnumSet<Direction>> order = sideCheck.order();
        if (order.isEmpty()) {
            if (behaviour.canBounce()) {
                order = List.of(EnumSet.of(item.side));
            } else {
                dropItem(item.stack, item.side.getOpposite(), item.speed);
                return;
            }
        }
        List<ItemEntry> entries = new ArrayList<>();
        entries.add(new ItemEntry(stack, item.side));
        behaviour.split(order, entries, level().random);
        long now = now();
        for (ItemEntry entry : entries) {
            if (entry.stack.isEmpty()) continue;
            double newSpeed = nextSpeed(behaviour, item.speed);
            List<Direction> destinations = entry.to;
            if (destinations == null || destinations.isEmpty()) destinations = randomOrder(order);
            if (destinations.isEmpty()) {
                dropItem(entry.stack, item.side.getOpposite(), newSpeed);
                continue;
            }
            TravellingItem newItem = new TravellingItem(entry.stack);
            newItem.tried.addAll(item.tried);
            newItem.toCenter = false;
            newItem.side = destinations.get(0);
            newItem.speed = newSpeed;
            newItem.genTimings(now, pipeLength(newItem.side));
            items.add(newItem.timeToDest, newItem);
            onItemsChanged();
        }
    }

    private static double nextSpeed(PipeBehaviour behaviour, double speed) {
        PipeBehaviour.SpeedTarget target = behaviour.speedTarget();
        if (target != null) {
            if (speed < target.target()) return Math.min(target.target(), speed + target.maxDelta());
            if (speed > target.target()) return Math.max(target.target(), speed - target.maxDelta());
            return speed;
        }
        return speed > 0.03 ? Math.max(0.03, speed - DEFAULT_SPEED_DELTA) : speed;
    }

    private static List<Direction> randomOrder(List<EnumSet<Direction>> order) {
        List<Direction> all = new ArrayList<>();
        for (EnumSet<Direction> set : order) {
            List<Direction> faces = new ArrayList<>(set);
            Collections.shuffle(faces);
            all.addAll(faces);
        }
        return all;
    }

    private void onItemReachEnd(TravellingItem item) {
        ItemStack excess = item.stack;
        if (excess.isEmpty()) return;
        if (pipe.isConnected(item.side)) {
            Direction opposite = item.side.getOpposite();
            BlockEntity tile = pipe.neighbourTile(item.side);
            if (pipe.connectedType(item.side) == PipeBlockEntity.ConnectedType.PIPE) {
                if (tile instanceof PipeBlockEntity other && other.flow() instanceof ItemFlow otherFlow) {
                    excess = otherFlow.injectItem(excess.copy(), true, opposite, item.speed);
                }
            } else if (tile != null) {
                if (tile instanceof Injectable injectable && injectable.canInjectItems(opposite)) {
                    excess = injectable.injectItem(excess.copy(), true, opposite, item.speed);
                }
                if (!excess.isEmpty()) {
                    ItemPort port = Ports.items(level(), tile.getBlockPos(), opposite);
                    if (port != null) excess = port.insert(excess, false);
                }
            }
        }
        if (excess.isEmpty()) {
            onItemsChanged();
            return;
        }
        item.tried.add(item.side);
        item.toCenter = true;
        item.stack = excess;
        item.genTimings(now(), pipeLength(item.side));
        items.add(item.timeToDest, item);
        onItemsChanged();
    }

    private void dropItem(ItemStack stack, Direction motion, double speed) {
        if (stack.isEmpty()) return;
        BlockPos pos = pipe.getBlockPos();
        double x = pos.getX() + 0.5 + motion.getStepX() * 0.5;
        double y = pos.getY() + 0.5 + motion.getStepY() * 0.5;
        double z = pos.getZ() + 0.5 + motion.getStepZ() * 0.5;
        speed = (speed + 0.01) * 2;
        ItemEntity entity = new ItemEntity(level(), x, y, z, stack);
        entity.setDeltaMovement(motion.getStepX() * speed, motion.getStepY() * speed, motion.getStepZ() * speed);
        level().addFreshEntity(entity);
        onItemsChanged();
    }

    // Insertion.

    @Override
    public boolean canInjectItems(Direction from) {
        return pipe.isConnected(from);
    }

    @Override
    public ItemStack injectItem(ItemStack stack, boolean doAdd, Direction from, double speed) {
        if (level().isClientSide) throw new IllegalStateException("Cannot inject items on the client side!");
        if (stack.isEmpty() || !canInjectItems(from)) return stack;
        if (doAdd) insertItemEvents(stack.copy(), Math.max(0.01, speed), from);
        return ItemStack.EMPTY;
    }

    private void insertItemEvents(ItemStack toInsert, double speed, Direction from) {
        TravellingItem item = new TravellingItem(toInsert);
        item.side = from;
        item.toCenter = true;
        item.speed = speed;
        item.genTimings(now(), pipeLength(from));
        item.tried.add(from);
        for (List<TravellingItem> list : items.all()) {
            for (TravellingItem other : list) {
                if (other.mergeWith(item)) {
                    onItemsChanged();
                    return;
                }
            }
        }
        items.add(item.timeToDest, item);
        onItemsChanged();
    }

    public double pipeLength(@Nullable Direction side) {
        if (side == null) return 0;
        if (!pipe.isConnected(side)) return 0.25;
        return pipe.connectedType(side) == PipeBlockEntity.ConnectedType.TILE ? 0.75 : 0.5;
    }

    public boolean containsItems() {
        return items.maxDelay() > 0;
    }

    public int itemCount() {
        int count = 0;
        for (List<TravellingItem> list : items.all()) {
            for (TravellingItem item : list) count += item.stack.getCount();
        }
        return count;
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        for (List<TravellingItem> list : items.all()) {
            for (TravellingItem item : list) drops.add(item.stack);
        }
        items.clear();
    }

    private void onItemsChanged() {
        pipe.setChanged();
        pipe.queueSync();
    }

    @Nullable
    @Override
    public ItemPort itemPort(Direction side) {
        return new ItemPort() {
            @Override
            public ItemStack insert(ItemStack stack, boolean simulate) {
                if (!canInjectItems(side)) return stack;
                return injectItem(stack, !simulate, side, 0);
            }

            @Override
            public ItemStack extract(Predicate<ItemStack> filter, int max, boolean simulate) {
                return ItemStack.EMPTY;
            }
        };
    }

    // Persistence.

    @Override
    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        long now = pipe.hasLevel() ? now() : 0;
        for (List<TravellingItem> inner : items.all()) {
            for (TravellingItem item : inner) list.add(item.save(now));
        }
        tag.put("items", list);
    }

    @Override
    public void load(CompoundTag tag) {
        items.clear();
        pendingLoad = tag.getList("items", Tag.TAG_COMPOUND);
    }

    /** Item timings are relative to the world clock, which is only known once the level is set. */
    public void onLevelSet() {
        if (pendingLoad == null || !pipe.hasLevel()) return;
        long now = now();
        for (int i = 0; i < pendingLoad.size(); i++) {
            TravellingItem item = new TravellingItem(pendingLoad.getCompound(i), now);
            if (!item.stack.isEmpty()) items.add(item.currentDelay(now), item);
        }
        pendingLoad = null;
    }

    // Client rendering: the server sends every item's current segment; the client interpolates.

    @Override
    public void writeClient(CompoundTag tag) {
        ListTag list = new ListTag();
        long now = now();
        outer:
        for (List<TravellingItem> inner : items.all()) {
            for (TravellingItem item : inner) {
                if (list.size() >= MAX_CLIENT_ITEMS) break outer;
                CompoundTag entry = new CompoundTag();
                entry.put("stack", item.stack.save(new CompoundTag()));
                entry.putBoolean("toCenter", item.toCenter);
                entry.putByte("side", (byte) item.side.get3DDataValue());
                entry.putInt("elapsed", (int) (now - item.tickStarted));
                entry.putInt("duration", (int) (item.tickFinished - item.tickStarted));
                list.add(entry);
            }
        }
        tag.put("items", list);
    }

    @Override
    public void readClient(CompoundTag tag) {
        clientItems.clear();
        ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
        long now = now();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            TravellingItem item = new TravellingItem(ItemStack.of(entry.getCompound("stack")));
            item.toCenter = entry.getBoolean("toCenter");
            item.side = Direction.from3DDataValue(entry.getByte("side"));
            item.tickStarted = now - entry.getInt("elapsed");
            item.tickFinished = item.tickStarted + entry.getInt("duration");
            clientItems.add(new ClientItem(item, pipeLength(item.side)));
        }
    }

    @Override
    public void clientTick() {
        long now = now();
        clientItems.removeIf(item -> item.item().tickFinished + 2 < now);
    }

    public List<ClientItem> clientItems() {
        return clientItems;
    }

    public record ClientItem(TravellingItem item, double pipeLength) {}
}
