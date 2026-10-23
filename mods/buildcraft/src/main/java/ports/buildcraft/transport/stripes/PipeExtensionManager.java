package ports.buildcraft.transport.stripes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.PipeDefinition;
import ports.buildcraft.transport.PipeItem;
import ports.buildcraft.transport.behaviour.StripesBehaviour;
import ports.buildcraft.transport.wire.WireSystems;

/**
 * Upstream {@code PipeExtensionManager}: item pipes handed to a stripes pipe are laid at the end of the level tick.
 * A pipe is placed where the stripes pipe was and the stripes pipe moves one block forward, into air or a
 * replaceable block. A void pipe instead pulls the stripes pipe back one block, removing the pipe it replaces, if
 * that pipe has no inventories next to it and no branches. The stripes pipe keeps its contents, gates and wires.
 * Unused pipes and the removed pipe's drops come back through the stripes pipe.
 *
 * <p>Upstream posted Forge block break and place events for the stripes pipe's owner and rolled back with block
 * snapshots when they were cancelled. Pipes in the port have no owner and no loader-neutral protection events, so
 * the move only checks that each block can be replaced.
 */
public final class PipeExtensionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    private static final Map<ServerLevel, List<Request>> REQUESTS = new IdentityHashMap<>();
    private static final Set<PipeDefinition> RETRACTION_PIPES = new HashSet<>();

    private record Request(BlockPos pos, Direction dir, PipeDefinition pipeDef, ItemStack stack) {}

    private PipeExtensionManager() {}

    /** Upstream {@code registerRetractionPipe}; the void item pipe is registered by default. */
    public static synchronized void registerRetractionPipe(PipeDefinition definition) {
        RETRACTION_PIPES.add(definition);
    }

    private static synchronized boolean isRetractionPipe(PipeDefinition definition) {
        return RETRACTION_PIPES.contains(definition);
    }

    public static boolean requestPipeExtension(ServerLevel level, BlockPos pos, Direction dir, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PipeItem item)) return false;
        REQUESTS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Request(pos.immutable(), dir, item.definition(), stack.copy()));
        return true;
    }

    /** Called at the end of each server level tick. */
    public static void tick(ServerLevel level) {
        List<Request> requests = REQUESTS.remove(level);
        if (requests == null) return;
        for (Request request : requests) {
            if (isRetractionPipe(request.pipeDef)) retract(level, request);
            else extend(level, request);
        }
    }

    private static void extend(ServerLevel level, Request r) {
        StripesBehaviour stripes = stripesAt(level, r.pos);
        if (stripes == null) return;
        BlockPos p = r.pos.relative(r.dir);
        if (!level.isEmptyBlock(p) && !level.getBlockState(p).getMaterial().isReplaceable()) {
            stripes.sendItem(r.stack.copy(), r.dir);
            return;
        }
        BlockState stripesState = level.getBlockState(r.pos);
        CompoundTag stripesTag = takeBlockEntity(level, r.pos);
        level.setBlock(r.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        List<ItemStack> sendBack = new ArrayList<>();
        ServerPlayer player = StripesBehaviour.fakePlayer(level, r.pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, r.stack);
        ItemStack held = player.getMainHandItem();
        boolean placed = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(r.pos), r.dir.getOpposite(), r.pos, false))).consumesAction()
                && level.getBlockEntity(r.pos) instanceof PipeBlockEntity;
        // What the player still holds (all of the stack if nothing was placed) goes back.
        takeInventory(player, sendBack);
        if (!placed) {
            level.setBlock(r.pos, stripesState, Block.UPDATE_ALL);
            restore(level, r.pos, stripesTag, r.dir, sendBack);
            return;
        }
        level.setBlock(p, stripesState, Block.UPDATE_ALL);
        restore(level, p, stripesTag, r.dir, sendBack);
    }

    private static void retract(ServerLevel level, Request r) {
        StripesBehaviour stripes = stripesAt(level, r.pos);
        if (stripes == null) return;
        Direction retractDir = r.dir.getOpposite();
        if (!isValidRetractionPath(level, r, retractDir)) {
            List<Direction> possible = new ArrayList<>();
            for (Direction facing : Direction.values()) {
                if (facing.getAxis() != r.dir.getAxis() && isValidRetractionPath(level, r, facing)) possible.add(facing);
            }
            if (possible.isEmpty()) {
                stripes.sendItem(r.stack.copy(), r.dir);
                return;
            }
            retractDir = possible.get(level.random.nextInt(possible.size()));
        }
        BlockPos p = r.pos.relative(retractDir);
        List<ItemStack> sendBack = new ArrayList<>();
        // Upstream always returns the void pipe that asked for the retraction.
        sendBack.add(r.stack);
        stripes.setDirection(retractDir.getOpposite());
        BlockState stripesState = level.getBlockState(r.pos);
        CompoundTag stripesTag = takeBlockEntity(level, r.pos);
        level.setBlock(r.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        sendBack.addAll(BlockUtil.breakBlockAndGetDrops(level, p));
        level.setBlock(p, stripesState, Block.UPDATE_ALL);
        restore(level, p, stripesTag, r.dir, sendBack);
    }

    /** Upstream {@code isValidRetractionPath}: the pipe behind joins only the stripes pipe and one other pipe. */
    private static boolean isValidRetractionPath(ServerLevel level, Request r, Direction retractDir) {
        if (!(level.getBlockEntity(r.pos.relative(retractDir)) instanceof PipeBlockEntity pipe)) return false;
        boolean connected = false;
        Direction back = retractDir.getOpposite();
        for (Direction facing : Direction.values()) {
            PipeBlockEntity.ConnectedType type = pipe.connectedType(facing);
            if (type == PipeBlockEntity.ConnectedType.TILE) return false;
            if (facing == back) {
                if (type != PipeBlockEntity.ConnectedType.PIPE) return false;
            } else if (type != null) {
                if (connected) return false;
                connected = true;
            }
        }
        return true;
    }

    private static StripesBehaviour stripesAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe && pipe.behaviour() instanceof StripesBehaviour stripes) return stripes;
        LOGGER.warn("Found an invalid pipe extension request at {}: there is no stripes pipe there", pos);
        return null;
    }

    /** Saves the block entity at {@code pos} and removes it, so that replacing the block drops none of its contents. */
    private static CompoundTag takeBlockEntity(ServerLevel level, BlockPos pos) {
        PipeBlockEntity pipe = (PipeBlockEntity) level.getBlockEntity(pos);
        CompoundTag tag = pipe.saveWithoutMetadata();
        level.removeBlockEntity(pos);
        return tag;
    }

    /** Upstream {@code cleanup}: loads the stripes pipe at {@code pos} and sends everything collected back through it. */
    private static void restore(ServerLevel level, BlockPos pos, CompoundTag tag, Direction dir, List<ItemStack> sendBack) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) {
            for (ItemStack stack : sendBack) InventoryUtil.drop(level, pos, stack);
            return;
        }
        pipe.load(tag);
        // Travelling items are timed against the level clock, which they read once the level is set.
        pipe.setLevel(level);
        pipe.markForUpdate();
        pipe.setChanged();
        if (!pipe.wires().parts().isEmpty()) WireSystems.get(level).rebuildWireSystemsAround(pipe);
        StripesBehaviour stripes = pipe.behaviour() instanceof StripesBehaviour s ? s : null;
        for (ItemStack stack : sendBack) {
            if (stack.isEmpty()) continue;
            if (stripes == null || !stripes.sendItem(stack.copy(), dir)) InventoryUtil.drop(level, pos, stack);
        }
    }

    private static void takeInventory(ServerPlayer player, List<ItemStack> into) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().removeItemNoUpdate(i);
            if (!stack.isEmpty()) into.add(stack);
        }
    }
}
