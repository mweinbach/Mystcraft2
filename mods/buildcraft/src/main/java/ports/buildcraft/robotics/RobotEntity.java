package ports.buildcraft.robotics;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;
import ports.buildcraft.robotics.board.RobotBoard;
import ports.buildcraft.robotics.board.RobotBoardType;
import ports.buildcraft.silicon.gate.GateLogic;

/**
 * The robot, written for this port from BuildCraft 7's documented behaviour. A robot is a small flying machine that
 * lives at a docking station, runs on MJ from its battery and does the job its redstone board gives it. It flies in
 * straight lines through anything (as a ghost would; upstream path-found through air), spends energy on flying and
 * on breaking blocks, and when its battery runs low it breaks off its job, docks at a station on a kinesis pipe and
 * waits until it is nearly full. It breaks blocks as its owner, the player who deployed it, so protection mods see
 * that player (see {@link BlockProtection}).
 *
 * <p>Players install a board by using it on the robot (the old board comes back), and take the robot back as an item,
 * keeping its board and energy, by sneak-using it with an empty hand or a wrench; its cargo and tool drop.
 */
public class RobotEntity extends Entity {
    public static final float SIZE = 0.5F;
    public static final long CAPACITY = 10_000 * Power.MJ;
    /** Below this a robot stops work to recharge. */
    public static final long LOW_ENERGY = CAPACITY / 10;
    /** A recharging robot goes back to work once it holds this much. */
    public static final long RESUME_ENERGY = CAPACITY * 95 / 100;
    /** Blocks a tick. */
    public static final double SPEED = 0.25;
    /** MJ (in micro-MJ) per block flown. */
    public static final long FLY_COST = Power.MJ;
    public static final int INVENTORY_SIZE = 4;
    /** How far a robot looks for a station to charge at. */
    public static final double CHARGE_RANGE = 64;

    private static final EntityDataAccessor<String> DATA_BOARD = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> DATA_HELD = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_WORK = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_SLEEPING = SynchedEntityData.defineId(RobotEntity.class, EntityDataSerializers.BOOLEAN);

    private final MjBattery battery = new MjBattery(CAPACITY);
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private ItemStack tool = ItemStack.EMPTY;
    @Nullable
    private RobotBoardType boardType;
    @Nullable
    private RobotBoard board;
    @Nullable
    private StationRef home;
    @Nullable
    private StationRef dockedAt;
    @Nullable
    private StationRef chargeTarget;
    private GameProfile owner = BlockProtection.NULL_PROFILE;
    private int sleepTicks;
    private boolean recharging;
    @Nullable
    private BlockPos breakPos;
    private int breakProgress, breakTotal;

    public RobotEntity(EntityType<? extends RobotEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_BOARD, "");
        entityData.define(DATA_HELD, ItemStack.EMPTY);
        entityData.define(DATA_WORK, Optional.empty());
        entityData.define(DATA_SLEEPING, false);
    }

    // Accessors.

    public MjBattery battery() { return battery; }
    public SimpleContainer inventory() { return inventory; }
    public ItemStack tool() { return tool; }
    @Nullable public StationRef home() { return home; }
    @Nullable public StationRef dockedAt() { return dockedAt; }
    @Nullable public RobotBoard board() { return board; }
    public GameProfile owner() { return owner; }
    public boolean isRecharging() { return recharging; }
    public boolean isSleeping() { return level.isClientSide ? entityData.get(DATA_SLEEPING) : sleepTicks > 0; }

    /** The board, read from synced data on clients. */
    @Nullable
    public RobotBoardType boardType() {
        return level.isClientSide ? RobotBoardType.byId(entityData.get(DATA_BOARD)) : boardType;
    }

    /** What the robot shows beneath it: its tool, or the first stack it carries. */
    public ItemStack heldForDisplay() {
        return entityData.get(DATA_HELD);
    }

    /** The block the robot is working on, for its laser. */
    public Optional<BlockPos> workTarget() {
        return entityData.get(DATA_WORK);
    }

    public void setOwner(GameProfile profile) {
        owner = profile == null || profile.getId() == null ? BlockProtection.NULL_PROFILE : profile;
    }

    public void setTool(ItemStack stack) {
        tool = stack;
    }

    public void setBoard(@Nullable RobotBoardType type) {
        if (board != null) board.reset();
        releaseClaims();
        boardType = type;
        board = type == null ? null : type.create(this);
        entityData.set(DATA_BOARD, type == null ? "" : type.id);
        sleepTicks = 0;
    }

    public void setHome(@Nullable StationRef ref) {
        if (home != null && !home.equals(ref)) {
            DockingStationPluggable old = home.resolve(level);
            if (old != null) {
                if (getUUID().equals(old.linkedRobotId())) old.link(null);
            } else if (level instanceof ServerLevel server && !server.isLoaded(home.pos())) {
                // The old home cannot be told now; it drops the link when it next loads.
                PendingUnlinks.get(server).add(home, getUUID());
            }
        }
        home = ref;
        if (ref != null) {
            DockingStationPluggable station = ref.resolve(level);
            if (station != null) station.link(this);
        }
    }

    /** Puts a robot that is not yet in the level at {@code ref}, docked there and calling it home. */
    void deployAt(StationRef ref) {
        Vec3 dock = ref.dockPosition();
        setPos(dock.x, dock.y, dock.z);
        dockedAt = ref;
        setHome(ref);
        DockingStationPluggable station = ref.resolve(level);
        if (station != null) station.dock(this);
    }

    /** Called by a station that is going away. */
    void stationRemoved(StationRef ref) {
        if (ref.equals(dockedAt)) dockedAt = null;
        if (ref.equals(home)) home = null;
        if (ref.equals(chargeTarget)) chargeTarget = null;
        if (board != null) board.reset();
        releaseClaims();
    }

    public void releaseClaims() {
        if (!level.isClientSide) RobotWorld.get(level).releaseAll(this);
    }

    /**
     * Where the robot works: the area a gate's "Work in Area" gives its home station, otherwise a cube of
     * {@link WorkArea#DEFAULT_RADIUS} around the station (or around the robot if it has lost its home).
     */
    public WorkArea workArea() {
        DockingStationPluggable station = home == null ? null : home.resolve(level);
        WorkArea area = station == null ? null : station.gateArea(this);
        if (area != null) return area;
        return WorkArea.around(home != null ? home.pos() : blockPosition(), WorkArea.DEFAULT_RADIUS);
    }

    // Sleep.

    public void sleep(int ticks) {
        sleepTicks = Math.max(sleepTicks, ticks);
    }

    public void wakeUp() {
        sleepTicks = 0;
    }

    // Ticking.

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;
        serverTick();
        entityData.set(DATA_SLEEPING, sleepTicks > 0 || battery.stored() == 0 && !recharging);
        entityData.set(DATA_HELD, displayStack());
        entityData.set(DATA_WORK, Optional.ofNullable(breakPos));
    }

    private ItemStack displayStack() {
        if (!tool.isEmpty()) return tool;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) return inventory.getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    private void serverTick() {
        if (dockedAt != null) {
            DockingStationPluggable station = level.isLoaded(dockedAt.pos()) ? dockedAt.resolve(level) : null;
            if (station == null) {
                if (level.isLoaded(dockedAt.pos())) dockedAt = null;
            } else {
                station.dock(this);
                Vec3 dock = dockedAt.dockPosition();
                if (position().distanceToSqr(dock) > 1.0E-6) setPos(dock.x, dock.y, dock.z);
                setDeltaMovement(Vec3.ZERO);
            }
        }
        if (home != null && level.isLoaded(home.pos()) && home.resolve(level) == null) home = null;
        if (home != null) {
            DockingStationPluggable station = home.resolve(level);
            if (station != null && !getUUID().equals(station.linkedRobotId())) {
                if (station.linkedRobotId() == null) station.link(this);
                else home = null;
            }
        }
        if (recharging) {
            tickRecharge();
            return;
        }
        if (battery.stored() < LOW_ENERGY) {
            startRecharge();
            tickRecharge();
            return;
        }
        if (sleepTicks > 0) {
            sleepTicks--;
            return;
        }
        if (board != null) board.tick();
        else if (home != null && goDock(home) != Progress.WORKING) sleep(100);
    }

    private void startRecharge() {
        if (board != null) board.reset();
        cancelBreak();
        releaseClaims();
        recharging = true;
        chargeTarget = null;
        sleepTicks = 0;
    }

    private void tickRecharge() {
        if (battery.stored() >= RESUME_ENERGY) {
            recharging = false;
            chargeTarget = null;
            return;
        }
        if (dockedAt != null && dockedAt.equals(chargeTarget)) return;
        DockingStationPluggable target = chargeTarget == null ? null : chargeTarget.resolve(level);
        if (target == null || !target.canCharge() || !target.isFreeFor(this)) {
            chargeTarget = findChargingStation();
            if (chargeTarget == null) return;
        }
        if (goDock(chargeTarget) == Progress.FAILED) chargeTarget = null;
    }

    @Nullable
    private StationRef findChargingStation() {
        if (home != null) {
            DockingStationPluggable station = home.resolve(level);
            if (station != null && station.canCharge() && station.isFreeFor(this)) return home;
        }
        List<DockingStationPluggable> stations = RobotWorld.get(level).stationsNear(level, position(), CHARGE_RANGE,
                station -> station.canCharge() && station.isFreeFor(this));
        return stations.isEmpty() ? null : stations.get(0).ref();
    }

    // Movement.

    /**
     * Flies one step towards {@code destination}, paying {@link #FLY_COST} per block; true once there. A robot without
     * the energy for the step stays put, except that one on its way to recharge drifts on at half speed for free so
     * that an empty robot is never stranded.
     */
    public boolean flyTowards(Vec3 destination) {
        undock();
        Vec3 here = position();
        Vec3 delta = destination.subtract(here);
        double distance = delta.length();
        double speed = SPEED;
        long cost = (long) (FLY_COST * Math.min(distance, speed));
        if (battery.stored() >= cost) {
            battery.extractPower(cost, cost);
        } else if (recharging) {
            speed = SPEED / 2;
        } else {
            return false;
        }
        if (distance <= speed) {
            setPos(destination.x, destination.y, destination.z);
            setDeltaMovement(Vec3.ZERO);
            return true;
        }
        Vec3 step = delta.scale(speed / distance);
        setPos(here.x + step.x, here.y + step.y, here.z + step.z);
        setDeltaMovement(step);
        if (step.horizontalDistanceSqr() > 1.0E-6) setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F);
        return false;
    }

    /** Flies to {@code ref} and docks there. Fails if the station has gone or another robot holds it. */
    public Progress goDock(StationRef ref) {
        DockingStationPluggable station = ref.resolve(level);
        if (station == null || !station.isFreeFor(this)) return Progress.FAILED;
        if (ref.equals(dockedAt)) return Progress.DONE;
        station.reserve(this);
        if (!flyTowards(ref.dockPosition())) return Progress.WORKING;
        dockedAt = ref;
        station.dock(this);
        return Progress.DONE;
    }

    public void undock() {
        if (dockedAt == null) return;
        DockingStationPluggable station = dockedAt.resolve(level);
        if (station != null) station.undock(this);
        dockedAt = null;
    }

    /** The station the robot is docked at, if it is still there. */
    @Nullable
    public DockingStationPluggable dockedStation() {
        return dockedAt == null ? null : dockedAt.resolve(level);
    }

    // Work.

    /**
     * Breaks the block at {@code pos} over several ticks as its owner, with the robot's tool, and keeps the drops
     * (what does not fit falls). Fails at once if protection refuses the owner or the robot has no tool that may
     * harvest the block; protection is asked again as the block breaks. Each tick costs a share of half the quarry's
     * power for that block ({@link BlockUtil#computeBlockBreakPower}); a robot short of it waits.
     */
    public Progress breakBlock(BlockPos pos) {
        ServerLevel server = (ServerLevel) level;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            cancelBreak();
            return Progress.DONE;
        }
        if (!pos.equals(breakPos)) {
            cancelBreak();
            if (!mayBreak(pos)) return Progress.FAILED;
            breakPos = pos.immutable();
            breakProgress = 0;
            breakTotal = breakTicks(state, pos);
        }
        long perTick = Math.max(1, BlockUtil.computeBlockBreakPower(level, pos) / 2 / breakTotal);
        if (battery.extractPower(perTick, perTick) == 0) return Progress.WORKING;
        breakProgress++;
        server.destroyBlockProgress(getId(), pos, Math.min(9, breakProgress * 10 / breakTotal));
        if (breakProgress < breakTotal) return Progress.WORKING;
        ServerPlayer actor = actor(pos);
        if (!BlockProtection.mayBreak(server, pos, actor)) {
            cancelBreak();
            return Progress.FAILED;
        }
        BlockEntity tile = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, server, pos, tile, actor, tool);
        level.destroyBlock(pos, false, this);
        for (ItemStack drop : drops) {
            ItemStack left = addToInventory(drop);
            if (!left.isEmpty()) Block.popResource(level, pos, left);
        }
        if (tool.isDamageableItem() && tool.hurt(1, random, actor)) tool = ItemStack.EMPTY;
        cancelBreak();
        return Progress.DONE;
    }

    /**
     * Whether the robot could break the block at {@code pos} now: it is breakable, the robot's tool may harvest it,
     * and protection lets the owner break it. Boards ask this while choosing targets so that the robot never sets
     * off for a block it may not touch; {@link #breakBlock} asks again before and as the block breaks.
     */
    public boolean mayBreak(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (BlockUtil.isUnbreakableBlock(level, pos) || state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) return false;
        return BlockProtection.mayBreak((ServerLevel) level, pos, actor(pos));
    }

    private ServerPlayer actor(BlockPos pos) {
        ServerPlayer player = BlockProtection.fakePlayer((ServerLevel) level, owner, blockPosition());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
        return player;
    }

    private int breakTicks(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        float speed = tool.isEmpty() ? 1 : Math.max(1, tool.getDestroySpeed(state));
        return Math.max(4, Mth.ceil(hardness * 30 / speed));
    }

    public void cancelBreak() {
        if (breakPos != null && level instanceof ServerLevel server) server.destroyBlockProgress(getId(), breakPos, -1);
        breakPos = null;
        breakProgress = 0;
    }

    // Inventory.

    /** @return what did not fit */
    public ItemStack addToInventory(ItemStack stack) {
        return inventory.addItem(stack);
    }

    public boolean canHold(ItemStack stack) {
        return inventory.canAddItem(stack);
    }

    public boolean isInventoryEmpty() {
        return inventory.isEmpty();
    }

    /** True when no slot is free. */
    public boolean isInventoryFull() {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Hands one stack to the station it is docked at; {@link Progress#DONE} once nothing is left or the station takes
     * nothing more.
     */
    public Progress unloadOne() {
        DockingStationPluggable station = dockedStation();
        if (station == null) return Progress.FAILED;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            ItemStack left = station.unload(this, stack);
            if (left.getCount() != stack.getCount()) {
                inventory.setItem(slot, left);
                return isInventoryEmpty() ? Progress.DONE : Progress.WORKING;
            }
        }
        return Progress.DONE;
    }

    // Interaction.

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean board = held.getItem() instanceof RedstoneBoardItem item && item.type != null;
        boolean pickUp = player.isShiftKeyDown() && (held.isEmpty() || Wrenches.isWrench(held));
        if (!board && !pickUp) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!mayControl(player)) return InteractionResult.FAIL;
        if (board) {
            RobotBoardType old = boardType;
            setBoard(((RedstoneBoardItem) held.getItem()).type);
            if (!player.getAbilities().instabuild) held.shrink(1);
            if (old != null) give(player, new ItemStack(BCRobotics.BOARDS.get(old).get()));
        } else {
            give(player, RobotItem.fromRobot(this));
            dropContents();
            setHome(null);
            undock();
            discard();
        }
        return InteractionResult.CONSUME;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /**
     * Whether {@code player} may reprogram or pick up this robot: its owner, an operator, or anyone if nobody owns
     * it. The robot breaks blocks with its owner's permissions, so nobody else may change what it does.
     */
    public boolean mayControl(Player player) {
        UUID id = owner.getId();
        if (id == null || id.equals(BlockProtection.NULL_PROFILE.getId()) || player.hasPermissions(2)) return true;
        return id.equals(player.getGameProfile().getId());
    }

    /**
     * Whether a gate configured by {@code editor} may direct this robot (see {@link DockingStationPluggable#gateArea}):
     * the same people as {@link #mayControl}, as they were when they last configured the gate.
     */
    public boolean mayBeDirectedBy(@Nullable GateLogic.Editor editor) {
        UUID id = owner.getId();
        if (id == null || id.equals(BlockProtection.NULL_PROFILE.getId())) return true;
        return editor != null && (editor.operator() || id.equals(editor.id()));
    }

    /** Drops the robot's cargo and tool where it is. */
    public void dropContents() {
        Containers.dropContents(level, blockPosition(), inventory);
        if (!tool.isEmpty()) Containers.dropItemStack(level, getX(), getY(), getZ(), tool);
        tool = ItemStack.EMPTY;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level.isClientSide) {
            cancelBreak();
            releaseClaims();
            if (reason.shouldDestroy()) {
                if (reason == RemovalReason.KILLED) dropContents();
                undock();
                setHome(null);
            }
        }
        super.remove(reason);
    }

    // Saving.

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setBoard(RobotBoardType.byId(tag.getString("board")));
        battery.load(tag.getCompound("battery"));
        inventory.fromTag(tag.getList("inventory", Tag.TAG_COMPOUND));
        tool = ItemStack.of(tag.getCompound("tool"));
        home = tag.contains("home", Tag.TAG_COMPOUND) ? StationRef.load(tag.getCompound("home")) : null;
        dockedAt = tag.contains("docked", Tag.TAG_COMPOUND) ? StationRef.load(tag.getCompound("docked")) : null;
        owner = tag.contains("owner", Tag.TAG_COMPOUND) ? NbtUtils.readGameProfile(tag.getCompound("owner")) : null;
        if (owner == null) owner = BlockProtection.NULL_PROFILE;
        sleepTicks = tag.getInt("sleep");
        recharging = tag.getBoolean("recharging");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("board", boardType == null ? "" : boardType.id);
        tag.put("battery", battery.save());
        ListTag items = inventory.createTag();
        tag.put("inventory", items);
        if (!tool.isEmpty()) tag.put("tool", tool.save(new CompoundTag()));
        if (home != null) tag.put("home", home.save());
        if (dockedAt != null) tag.put("docked", dockedAt.save());
        tag.put("owner", NbtUtils.writeGameProfile(new CompoundTag(), owner));
        tag.putInt("sleep", sleepTicks);
        tag.putBoolean("recharging", recharging);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** Whether {@code stack} passes {@code filter}, for boards that look for tools. */
    public boolean hasTool(Predicate<ItemStack> filter) {
        return !tool.isEmpty() && filter.test(tool);
    }
}
