package ports.gridwright.worker;

import ports.gridwright.block.MechanicalBlock;
import ports.gridwright.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * The mechanical user right-clicks the block in front of it once every {@link #INTERVAL} ticks, as
 * its owner would, with the next stack from its nine input slots in turn: it places blocks, flips
 * levers, fills and empties buckets, plants seeds, uses bone meal. If clicking the block does
 * nothing, the item is used in the air (so a bucket can scoop the fluid in front). What the use
 * leaves in hand goes back to its input slot if it is still the same item; anything else it gives
 * back goes to the output, and overflow drops in front. It needs {@link #GP} Grid Power, waits while
 * no output slot is free, and pauses on a redstone signal.
 *
 * <p>The click goes through the loader's interaction events (Forge {@code RightClickBlock}, Fabric
 * {@code UseBlockCallback}), so claim mods can refuse it; spawn protection and the world border are
 * checked first. A refused click ends the action, and the use in the air only happens where the
 * owner may also break the block in front and every block the item could aim at.
 */
public class MechanicalUserBlockEntity extends WorkerBlockEntity {

    public static final double GP = 2;
    public static final int INTERVAL = 20;
    /** Further than any player's reach, so whatever an item used in the air could hit is checked. */
    private static final double AIR_USE_REACH = 6;

    private int cooldown;
    private int next;
    private int uses;

    public MechanicalUserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_USER.get(), pos, state, WorkerLayout.MECHANICAL_USER);
    }

    @Override
    protected int progressPerMille() {
        return cooldown * 1000 / INTERVAL;
    }

    @Override
    protected int counter() {
        return uses;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof MechanicalUserBlockEntity user) user.tick(server);
    }

    private void tick(ServerLevel level) {
        if (level.hasNeighborSignal(worldPosition)) {
            stop(Status.PAUSED);
            return;
        }
        int slot = nextInput();
        if (slot < 0) {
            stop(Status.IDLE);
            return;
        }
        if (!hasFreeOutput()) {
            stop(Status.BLOCKED);
            return;
        }
        if (!requestPower(level, GP)) return;
        if (++cooldown < INTERVAL) return;
        cooldown = 0;
        Direction facing = getBlockState().getValue(MechanicalBlock.FACING);
        ServerPlayer player = MachinePlayer.prepare(level, this, facing);
        if (player == null) {
            stop(Status.NO_OWNER);
            return;
        }
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            InteractionResult result = use(level, player, stack, facing);
            if (result == InteractionResult.FAIL) status = Status.PROTECTED;
            else if (result.consumesAction()) uses++;
        } finally {
            collect(level, player, slot, stack, facing);
            if (player.containerMenu != player.inventoryMenu) player.doCloseContainer();
            next = slot + 1;
            setChanged();
        }
    }

    /** Clicks the block in front, then uses the item in the air if that did nothing; FAIL if refused. */
    private InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack, Direction facing) {
        BlockPos target = worldPosition.relative(facing);
        if (!level.mayInteract(player, target)) return InteractionResult.FAIL;
        Direction face = facing.getOpposite();
        Vec3 hitPos = Vec3.atCenterOf(target).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, face, target, false);
        InteractionResult result = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        if (result == InteractionResult.FAIL || result.consumesAction() || stack.isEmpty()) return result;
        if (!mayAct(level, player, target)) return InteractionResult.FAIL;
        for (BlockPos aimed : aimedBlocks(level, player)) {
            if (!mayAct(level, player, aimed)) return InteractionResult.FAIL;
        }
        return player.gameMode.useItem(player, level, stack, InteractionHand.MAIN_HAND);
    }

    private static boolean mayAct(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return level.mayInteract(player, pos) && MachinePlayer.mayBreak(level, pos, level.getBlockState(pos), player);
    }

    /**
     * Every block an item used in the air could act on: the first block hit along the fake player's
     * look, within {@link #AIR_USE_REACH}, for each way items treat fluids (ignored, sources, any).
     */
    private static Set<BlockPos> aimedBlocks(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(AIR_USE_REACH));
        Set<BlockPos> aimed = new HashSet<>();
        for (ClipContext.Fluid fluid : ClipContext.Fluid.values()) {
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluid, player));
            if (hit.getType() == HitResult.Type.BLOCK) aimed.add(hit.getBlockPos().immutable());
        }
        return aimed;
    }

    /** Takes back everything the fake player holds: the same item to its slot, the rest to the output. */
    private void collect(ServerLevel level, ServerPlayer player, int slot, ItemStack used, Direction facing) {
        Inventory inventory = player.getInventory();
        ItemStack held = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!held.isEmpty() && held.is(used.getItem()) && items.get(slot).isEmpty()) items.set(slot, held);
        else give(level, held, facing);
        for (int i = 0; i < inventory.getContainerSize(); i++) give(level, inventory.removeItemNoUpdate(i), facing);
    }

    private void give(ServerLevel level, ItemStack stack, Direction facing) {
        if (stack.isEmpty()) return;
        ItemStack left = store(stack, layout().firstOutput(), layout().size());
        if (!left.isEmpty()) {
            BlockPos front = worldPosition.relative(facing);
            Containers.dropItemStack(level, front.getX() + 0.5, front.getY() + 0.5, front.getZ() + 0.5, left);
        }
    }

    /** The next non-empty input slot, round-robin, or -1. */
    private int nextInput() {
        int first = layout().first(WorkerLayout.Role.INPUT);
        int count = layout().firstOutput() - first;
        for (int i = 0; i < count; i++) {
            int slot = first + Math.floorMod(next - first + i, count);
            if (!items.get(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private boolean hasFreeOutput() {
        for (int slot = layout().firstOutput(); slot < layout().size(); slot++) if (items.get(slot).isEmpty()) return true;
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Cooldown", cooldown);
        tag.putInt("Next", next);
        tag.putInt("Uses", uses);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cooldown = tag.getInt("Cooldown");
        next = tag.getInt("Next");
        uses = tag.getInt("Uses");
    }
}
