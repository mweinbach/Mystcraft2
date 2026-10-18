package dev.icbm.port.block;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.core.DirectFlight;
import dev.icbm.port.entity.MissileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** A one-missile, redstone-triggered launcher. State persists in the chunk. */
public final class LauncherBlock extends FacingBlock {
    public static final BooleanProperty LOADED = BooleanProperty.of("loaded");
    public static final BooleanProperty TRIGGERED = Properties.TRIGGERED;

    public LauncherBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH).with(LOADED, false).with(TRIGGERED, false));
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, LOADED, TRIGGERED); }
    @Override public PistonBehavior getPistonBehavior(BlockState state) { return PistonBehavior.BLOCK; }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getPlayerLookDirection().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(IcbmPort.CONDENSED_MISSILE) && !state.get(LOADED)) {
            if (!world.isClient) {
                world.setBlockState(pos, state.with(LOADED, true), Block.NOTIFY_ALL);
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                player.sendMessage(Text.translatable("message.icbm_port.loaded"), true);
            }
            return ActionResult.success(world.isClient);
        }
        if (player.isSneaking() && stack.isEmpty() && state.get(LOADED)) {
            if (!world.isClient) {
                world.setBlockState(pos, state.with(LOADED, false), Block.NOTIFY_ALL);
                player.giveItemStack(new ItemStack(IcbmPort.CONDENSED_MISSILE));
            }
            return ActionResult.success(world.isClient);
        }
        if (!world.isClient) player.sendMessage(Text.translatable(state.get(LOADED)
                ? "message.icbm_port.ready" : "message.icbm_port.empty"), true);
        return ActionResult.success(world.isClient);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block source, BlockPos sourcePos, boolean notify) {
        updatePower(state, world, pos);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.isOf(this)) updatePower(state, world, pos);
    }

    private void updatePower(BlockState state, World world, BlockPos pos) {
        if (world.isClient) return;
        boolean powered = world.isReceivingRedstonePower(pos);
        if (powered && !state.get(TRIGGERED)) {
            world.setBlockState(pos, state.with(TRIGGERED, true), Block.NOTIFY_ALL);
            world.createAndScheduleBlockTick(pos, this, 4);
        } else if (!powered && state.get(TRIGGERED)) {
            world.setBlockState(pos, state.with(TRIGGERED, false), Block.NOTIFY_ALL);
        }
    }

    @Override public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) { launch(world, pos); }

    public boolean launch(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(this) || !state.get(LOADED)) return false;
        Direction facing = state.get(FACING);
        // Require a clear muzzle; no spawning through a wall in front of the launcher.
        BlockPos muzzle = pos.offset(facing);
        if (!world.isChunkLoaded(muzzle) || !world.getWorldBorder().contains(muzzle)
                || world.isOutOfHeightLimit(muzzle)
                || !world.getBlockState(muzzle).getCollisionShape(world, muzzle).isEmpty()) return false;
        MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
        Vec3d start = Vec3d.ofCenter(pos).add(facing.getOffsetX() * 1.1, facing.getOffsetY() * 1.1, facing.getOffsetZ() * 1.1);
        missile.setPosition(start);
        DirectFlight.Velocity motion = DirectFlight.velocity(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ(), IcbmPort.MISSILE_SPEED);
        missile.setVelocity(motion.x(), motion.y(), motion.z());
        if (!world.spawnEntity(missile)) return false;
        world.setBlockState(pos, state.with(LOADED, false), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 1, 0.7F);
        return true;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && state.get(LOADED) && !world.isClient)
            Block.dropStack(world, pos, new ItemStack(IcbmPort.CONDENSED_MISSILE));
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
