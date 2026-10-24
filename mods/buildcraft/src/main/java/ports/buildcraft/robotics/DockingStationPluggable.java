package ports.buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.flow.PowerFlow;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * The docking station, written for this port from BuildCraft 7's documented behaviour: a pipe attachment a robot
 * calls home and docks at. A station on a kinesis pipe charges the robot docked at it from the pipe's power. A
 * station reaches the inventories beside its pipe and, on an item pipe, the pipe itself: robots unload there and
 * carriers load there, as the gate actions on the pipe allow. Gates on the pipe read whether a robot is docked,
 * linked or on its way, and set what the stations on that pipe offer each tick (see {@code statements}).
 */
public class DockingStationPluggable extends PipePluggable {
    private static final AABB[] BOXES = faceBoxes(0, 4, 4, 12);
    /** How many MJ a tick a station passes into the docked robot; the kinesis pipe usually limits it first. */
    public static final long CHARGE_PER_TICK = 100 * Power.MJ;
    /** Gate actions hold for this many ticks after the gate last activated them. */
    private static final long ACTION_HOLD = 2;
    private static final long RESERVATION_TICKS = 100;
    /** How fast unloaded items set off into the station's pipe, in blocks a tick. */
    private static final double UNLOAD_SPEED = 0.08;

    @Nullable
    private UUID linked;
    @Nullable
    private UUID docked;
    @Nullable
    private UUID reservedBy;
    private long reservedAt;
    private boolean clientLinked, clientDocked;

    /** An action tick for an action no gate has activated yet. */
    private static final long NEVER = Long.MIN_VALUE;
    private long provideTick = NEVER, acceptTick = NEVER, dropTick = NEVER;
    private long forbidTick = NEVER, areaTick = NEVER, wakeTick = NEVER;
    private final List<ItemStack> provideFilters = new ArrayList<>(), acceptFilters = new ArrayList<>();
    private ItemStack areaMap = ItemStack.EMPTY;
    /** The gate's own parameter stack last handed to {@link #actionWorkInArea}, compared by identity. */
    @Nullable
    private ItemStack areaSource;
    /** {@link #areaMap} decoded, so robots asking every tick do not rebuild a zone. */
    @Nullable
    private WorkArea area;
    /** Who configured the gate that gave {@link #area}; only robots they may direct work there. */
    @Nullable
    private GateLogic.Editor areaEditor;

    public DockingStationPluggable(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
        super(definition, holder, side);
    }

    public StationRef ref() {
        return new StationRef(holder.getBlockPos(), side);
    }

    public Level level() {
        return holder.getLevel();
    }

    private long now() {
        return holder.getLevel() == null ? 0 : holder.getLevel().getGameTime();
    }

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.ordinal()];
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return new ItemStack(BCRobotics.DOCKING_STATION_ITEM.get());
    }

    // Robots.

    @Nullable
    private RobotEntity robot(@Nullable UUID id) {
        if (id == null || !(holder.getLevel() instanceof ServerLevel server)) return null;
        return server.getEntity(id) instanceof RobotEntity robot && robot.isAlive() ? robot : null;
    }

    /** The robot that calls this station home, if it is loaded. */
    @Nullable
    public RobotEntity linkedRobot() {
        RobotEntity robot = robot(linked);
        return robot != null && ref().equals(robot.home()) ? robot : null;
    }

    public boolean isLinked() {
        return holder.getLevel() != null && holder.getLevel().isClientSide ? clientLinked : linked != null;
    }

    /** The robot docked here now, if any. */
    @Nullable
    public RobotEntity dockedRobot() {
        RobotEntity robot = robot(docked);
        return robot != null && ref().equals(robot.dockedAt()) ? robot : null;
    }

    public boolean hasDockedRobot() {
        return holder.getLevel() != null && holder.getLevel().isClientSide ? clientDocked : dockedRobot() != null;
    }

    /** True while a robot other than the docked one has set off for this station. */
    public boolean isReserved() {
        if (reservedBy == null || now() - reservedAt > RESERVATION_TICKS) return false;
        RobotEntity robot = robot(reservedBy);
        return robot != null && !ref().equals(robot.dockedAt());
    }

    /** Whether {@code robot} may dock here: nobody else is docked or on the way, and no gate forbids it. */
    public boolean isFreeFor(RobotEntity robot) {
        if (isForbiddenFor(robot)) return false;
        RobotEntity other = dockedRobot();
        if (other != null && other != robot) return false;
        return !isReserved() || robot.getUUID().equals(reservedBy);
    }

    /** A gate's "Forbid Robot" keeps every robot but the one that lives here away. */
    public boolean isForbiddenFor(RobotEntity robot) {
        return isForbidden() && !robot.getUUID().equals(linked);
    }

    void reserve(RobotEntity robot) {
        reservedBy = robot.getUUID();
        reservedAt = now();
    }

    void link(@Nullable RobotEntity robot) {
        UUID id = robot == null ? null : robot.getUUID();
        if (java.util.Objects.equals(id, linked)) return;
        linked = id;
        holder.setChanged();
        queueSync();
    }

    void dock(RobotEntity robot) {
        if (robot.getUUID().equals(reservedBy)) reservedBy = null;
        if (robot.getUUID().equals(docked)) return;
        docked = robot.getUUID();
        queueSync();
    }

    @Nullable
    UUID linkedRobotId() {
        return linked;
    }

    void undock(RobotEntity robot) {
        if (!robot.getUUID().equals(docked)) return;
        docked = null;
        queueSync();
    }

    /** The robot a gate's "Wake Up" asked for: the docked or linked robot stops sleeping. */
    void wakeRobots() {
        RobotEntity robot = dockedRobot();
        if (robot != null) robot.wakeUp();
        robot = linkedRobot();
        if (robot != null) robot.wakeUp();
    }

    // Energy.

    public boolean canCharge() {
        return holder.flow() instanceof PowerFlow;
    }

    @Nullable
    @Override
    public EnergyPort powerSink() {
        if (!canCharge()) return null;
        RobotEntity robot = dockedRobot();
        if (robot == null || robot.battery().isFull()) return null;
        return new EnergyPort() {
            @Override
            public long receive(long amount, boolean simulate) {
                long accepted = Math.min(amount, Power.toUnits(Math.min(robot.battery().space(), CHARGE_PER_TICK)));
                if (accepted <= 0) return 0;
                if (!simulate) robot.battery().addPower(Power.fromUnits(accepted));
                return accepted;
            }

            @Override public long extract(long amount, boolean simulate) { return 0; }
            @Override public long stored() { return Power.toUnits(robot.battery().stored()); }
            @Override public long capacity() { return Power.toUnits(robot.battery().capacity()); }
            @Override public boolean canReceive() { return true; }
            @Override public boolean canExtract() { return false; }
        };
    }

    // Gate actions, held for a tick after each activation.

    private boolean held(long tick) {
        return tick != NEVER && now() - tick <= ACTION_HOLD;
    }

    public void actionProvide(List<ItemStack> filters) {
        provideTick = now();
        provideFilters.clear();
        provideFilters.addAll(filters);
    }

    public void actionAccept(List<ItemStack> filters) {
        acceptTick = now();
        acceptFilters.clear();
        acceptFilters.addAll(filters);
    }

    public void actionDropInPipe() {
        dropTick = now();
    }

    public void actionForbid() {
        forbidTick = now();
    }

    /** @param editor who configured the gate, see {@link #gateArea(RobotEntity)} */
    public void actionWorkInArea(ItemStack map, @Nullable GateLogic.Editor editor) {
        areaTick = now();
        areaEditor = editor;
        // A gate hands over the same parameter stack every tick; only compare its NBT when that stack changes.
        if (map == areaSource) return;
        areaSource = map;
        if (ItemStack.matches(map, areaMap)) return;
        areaMap = map.copy();
        area = WorkArea.fromMap(areaMap, holder.getBlockPos());
    }

    public void actionWakeUp() {
        if (!held(wakeTick)) wakeRobots();
        wakeTick = now();
    }

    public boolean isProviding() {
        return held(provideTick);
    }

    public boolean isAccepting() {
        return held(acceptTick);
    }

    public boolean isDroppingInPipe() {
        return held(dropTick) && holder.flow() instanceof ItemFlow;
    }

    public boolean isForbidden() {
        return held(forbidTick);
    }

    /**
     * The area a gate gives the robots that live here, or null for their default area. A robot breaks blocks as its
     * owner, so it only takes an area from a gate that its owner or an operator configured (or any gate if nobody
     * owns it); otherwise anyone who could reach the pipe could send it to work where only its owner may.
     */
    @Nullable
    public WorkArea gateArea(RobotEntity robot) {
        return held(areaTick) && robot.mayBeDirectedBy(areaEditor) ? area : null;
    }

    private static boolean passes(List<ItemStack> filters, ItemStack stack) {
        boolean any = false;
        for (ItemStack filter : filters) {
            if (filter.isEmpty()) continue;
            any = true;
            if (ItemStack.isSameItemSameTags(filter, stack)) return true;
        }
        return !any;
    }

    // Items.

    /** The item faces of the blocks beside the pipe, other than pipes and the station's own side. */
    public List<ItemPort> inventories() {
        List<ItemPort> ports = new ArrayList<>();
        Level level = holder.getLevel();
        if (level == null) return ports;
        for (Direction face : Direction.values()) {
            if (face == side) continue;
            BlockPos pos = holder.getBlockPos().relative(face);
            if (level.getBlockEntity(pos) instanceof PipeBlockEntity) continue;
            ItemPort port = Ports.items(level, pos, face.getOpposite());
            if (port != null) ports.add(port);
        }
        return ports;
    }

    /** True if robots may unload {@code stack} here; a robot's home always takes what it brings back. */
    public boolean accepts(RobotEntity robot, ItemStack stack) {
        if (robot.getUUID().equals(linked)) return true;
        return isDroppingInPipe() || isAccepting() && passes(acceptFilters, stack);
    }

    /**
     * Hands a stack from a docked robot to the station: into the pipe when it is an item pipe that leads somewhere,
     * then into the inventories beside the pipe. Returns what did not fit. A gate's "Drop Items In Pipe" sends
     * everything into the pipe.
     */
    public ItemStack unload(RobotEntity robot, ItemStack stack) {
        if (stack.isEmpty() || !accepts(robot, stack)) return stack;
        if (holder.flow() instanceof ItemFlow items && (isDroppingInPipe() || leadsSomewhere())) {
            items.insertItemsForce(stack.copy(), side, null, UNLOAD_SPEED);
            return ItemStack.EMPTY;
        }
        ItemStack left = stack.copy();
        for (ItemPort port : inventories()) {
            left = port.insert(left, false);
            if (left.isEmpty()) break;
        }
        return left;
    }

    private boolean leadsSomewhere() {
        for (Direction face : Direction.values()) {
            if (face != side && holder.isConnected(face)) return true;
        }
        return false;
    }

    /** True if a gate lets robots take items here and the inventories beside the pipe hold one that passes {@code filter}. */
    public boolean canProvide(Predicate<ItemStack> filter) {
        if (!isProviding()) return false;
        for (ItemPort port : inventories()) {
            if (!port.extract(stack -> passes(provideFilters, stack) && filter.test(stack), 1, true).isEmpty()) return true;
        }
        return false;
    }

    /** Takes up to {@code max} items that pass both the gate's filters and {@code filter}, from one inventory beside the pipe. */
    public ItemStack provide(Predicate<ItemStack> filter, int max) {
        if (!isProviding()) return ItemStack.EMPTY;
        return extract(stack -> passes(provideFilters, stack) && filter.test(stack), max);
    }

    /** Takes up to {@code max} items that pass {@code filter} from one inventory beside the pipe, gate or not. */
    public ItemStack extract(Predicate<ItemStack> filter, int max) {
        for (ItemPort port : inventories()) {
            ItemStack taken = port.extract(filter, max, false);
            if (!taken.isEmpty()) return taken;
        }
        return ItemStack.EMPTY;
    }

    // Placement of robots.

    @Override
    public InteractionResult onActivate(Player player, InteractionHand hand, Vec3 hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof RobotItem)) return InteractionResult.PASS;
        if (holder.getLevel() instanceof ServerLevel server) applyPendingUnlink(server);
        // A station stays its robot's home while that robot is away, even in unloaded chunks.
        if (isLinked() || hasDockedRobot()) return InteractionResult.FAIL;
        if (holder.getLevel().isClientSide) return InteractionResult.SUCCESS;
        RobotEntity robot = RobotItem.createRobot((ServerLevel) holder.getLevel(), held, ref());
        robot.setOwner(player.getGameProfile());
        holder.getLevel().addFreshEntity(robot);
        if (!player.getAbilities().instabuild) held.shrink(1);
        return InteractionResult.CONSUME;
    }

    /** Drops the link to a robot that was picked up, killed or rehomed while this station was unloaded. */
    private void applyPendingUnlink(ServerLevel level) {
        UUID robot = PendingUnlinks.get(level).take(ref());
        if (robot != null && robot.equals(linked)) link(null);
    }

    // Lifecycle and sync.

    @Override
    public void onTick() {
        Level level = holder.getLevel();
        if (level == null || level.isClientSide) return;
        RobotWorld.get(level).addStation(ref());
        applyPendingUnlink((ServerLevel) level);
    }

    @Override
    public void onRemove() {
        Level level = holder.getLevel();
        if (level == null || level.isClientSide) return;
        RobotWorld.get(level).removeStation(ref());
        RobotEntity robot = dockedRobot();
        if (robot != null) robot.stationRemoved(ref());
        robot = linkedRobot();
        if (robot != null) robot.stationRemoved(ref());
    }

    @Override
    public void save(CompoundTag tag) {
        if (linked != null) tag.putUUID("linked", linked);
    }

    @Override
    public void load(CompoundTag tag) {
        linked = tag.hasUUID("linked") ? tag.getUUID("linked") : null;
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putBoolean("linked", linked != null);
        tag.putBoolean("docked", dockedRobot() != null);
    }

    @Override
    public void readClient(CompoundTag tag) {
        clientLinked = tag.getBoolean("linked");
        clientDocked = tag.getBoolean("docked");
    }
}
