package ports.buildcraft.transport.behaviour;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.BlockProtection;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.StripesPipeBlock;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.statements.ActionPipeDirection;
import ports.buildcraft.transport.statements.BCTransportStatements;
import ports.buildcraft.transport.stripes.StripesActivator;
import ports.buildcraft.transport.stripes.StripesRegistry;

/**
 * Upstream {@code PipeBehaviourStripes}. The pipe points away from its only connection (or where a gate's direction
 * action sends it, on a free face). Items that reach it with nowhere to go are handed to the {@link StripesRegistry}
 * as if a player held them against the block in front; whatever the player is left holding comes back through the
 * pipe, and items no handler can use drop as usual. Power from engines (256 MJ battery, at most 10 MJ a tick) breaks
 * the block in front, and its drops come back through the pipe. The pipe acts as its owner's fake player, and
 * breaks, places and uses items only where {@link BlockProtection} allows that player to.
 */
public class StripesBehaviour extends PipeBehaviour implements StripesActivator {
    private static final long MAX_BREAK_POWER_PER_TICK = 10 * Power.MJ;

    private final MjBattery battery = new MjBattery(256 * Power.MJ);
    private final EnergyPort energy;
    @Nullable
    private Direction direction;
    private long progress;

    public StripesBehaviour(PipeBlockEntity pipe) {
        super(pipe);
        energy = battery.receiver(true, pipe::setChanged);
    }

    @Nullable
    public Direction direction() {
        return direction;
    }

    public MjBattery battery() {
        return battery;
    }

    /** Also used by the pipe extension manager, which moves the pipe. */
    public void setDirection(@Nullable Direction newValue) {
        if (direction == newValue) return;
        direction = newValue;
        pipe.setChanged();
        pipe.refreshState();
    }

    /** The rod drawn on the open end. */
    @Override
    public BlockState modelState(BlockState state) {
        return state.hasProperty(StripesPipeBlock.DIRECTION) ? state.setValue(StripesPipeBlock.DIRECTION, StripesPipeBlock.Facing.of(direction)) : state;
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof StripesBehaviour);
    }

    @Override
    public void addInternalActions(Collection<Action.Internal> actions) {
        for (Direction face : Direction.values()) {
            if (pipe.isConnected(face)) continue;
            PipePluggable plug = pipe.getPluggable(face);
            if (plug == null || !plug.isBlocking()) actions.add(BCTransportStatements.ACTION_PIPE_DIRECTION[face.ordinal()]);
        }
    }

    @Override
    public void onActionActivate(Action action, StatementParameter[] parameters) {
        if (action instanceof ActionPipeDirection set) setDirection(set.direction);
    }

    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energy;
    }

    @Override
    public long receiveRedstonePower(long microJoules, boolean simulate) {
        long accepted = Math.min(microJoules, battery.space());
        if (!simulate && accepted > 0) {
            battery.addPower(accepted);
            pipe.setChanged();
        }
        return microJoules - accepted;
    }

    @Override
    public void onTick() {
        Level level = pipe.getLevel();
        if (!(level instanceof ServerLevel server)) return;
        if (direction == null || pipe.isConnected(direction)) {
            int sides = 0;
            Direction connected = null;
            for (Direction face : Direction.values()) {
                if (pipe.isConnected(face)) {
                    sides++;
                    connected = face;
                }
            }
            setDirection(sides == 1 ? connected.getOpposite() : null);
        }
        battery.tick();
        if (direction == null) {
            progress = 0;
            return;
        }
        BlockPos front = pipe.getBlockPos().relative(direction);
        long target = !level.isLoaded(front) || level.getBlockState(front).isAir() ? 0 : BlockUtil.computeBlockBreakPower(level, front);
        if (target <= 0) {
            progress = 0;
            return;
        }
        int breakerId = front.hashCode();
        if (progress < target) {
            long extracted = battery.extractPower(0, Math.min(target - progress, MAX_BREAK_POWER_PER_TICK));
            if (extracted > 0) pipe.setChanged();
            progress += extracted;
            if (progress > 0) level.destroyBlockProgress(breakerId, front, (int) (progress * 9 / target));
        } else {
            level.destroyBlockProgress(breakerId, front, -1);
            Direction dir = direction;
            // Upstream BlockUtil.breakBlockAndGetDrops posted the break event for the owner holding a diamond pickaxe
            // and broke nothing when it was cancelled.
            ServerPlayer player = fakePlayer(server, pipe.getBlockPos());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            if (BlockProtection.mayBreak(server, front, player)) {
                List<ItemStack> drops = BlockUtil.breakBlockAndGetDrops(server, front);
                for (ItemStack stack : drops) sendItem(stack, dir);
            }
            player.getInventory().clearContent();
            progress = 0;
        }
    }

    @Override
    public void onDrop(ItemEntity entity) {
        if (direction == null || !(pipe.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = pipe.getBlockPos();
        ServerPlayer player = fakePlayer(level, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, entity.getItem().copy());
        boolean handled = StripesRegistry.handleItem(level, pos, direction, player.getMainHandItem(), player, this);
        if (handled) entity.setItem(ItemStack.EMPTY);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.removeItemNoUpdate(i);
            if (handled && !stack.isEmpty()) sendItem(stack, direction);
        }
    }

    /**
     * Upstream {@code getFakePlayer(world, holder.getOwner(), pos)}: the fake player of the pipe's owner (see
     * {@link PipeBlockEntity#getOwner}), emptied and moved to {@code pos}. Loader protection events see the owner.
     */
    public ServerPlayer fakePlayer(ServerLevel level, BlockPos pos) {
        return BlockProtection.fakePlayer(level, pipe.getOwner(), pos);
    }

    /** Upstream {@code IStripesActivator.sendItem}: the stack enters the pipe's centre as if it came in from {@code from}. */
    @Override
    public boolean sendItem(ItemStack stack, Direction from) {
        if (stack.isEmpty()) return true;
        if (pipe.flow() instanceof ItemFlow items) {
            items.insertItemsForce(stack, from, null, 0.02);
            return true;
        }
        return false;
    }

    /** Upstream {@code IStripesActivator.dropItem}. */
    public void dropItem(ItemStack stack) {
        InventoryUtil.drop(pipe.getLevel(), pipe.getBlockPos(), stack);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("battery", battery.save());
        tag.putByte("direction", (byte) (direction == null ? -1 : direction.get3DDataValue()));
        tag.putLong("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        battery.load(tag.getCompound("battery"));
        byte value = tag.contains("direction") ? tag.getByte("direction") : -1;
        direction = value < 0 ? null : Direction.from3DDataValue(value);
        progress = tag.getLong("progress");
    }
}
