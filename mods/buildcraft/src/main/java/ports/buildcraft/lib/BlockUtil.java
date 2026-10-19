package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.platform.Platform;

/** Upstream {@code BlockUtil}: world fluid and block-breaking helpers shared by the pump, mining well and quarry. */
public final class BlockUtil {
    private BlockUtil() {}

    /** The still fluid at {@code pos}, counting flowing blocks; null when there is none. */
    @Nullable
    public static Fluid fluidWithFlowing(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        return state.isEmpty() ? null : Fluids2.still(state.getType());
    }

    /** The fluid a pump could drain from {@code pos}: a source block of a liquid block. */
    @Nullable
    public static Fluid drainableFluid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (!(state.getBlock() instanceof LiquidBlock) || !fluid.isSource()) return null;
        return Fluids2.still(fluid.getType());
    }

    /** Upstream {@code drainBlock}: removes one bucket from a source block. */
    @Nullable
    public static Fluid drainBlock(Level level, BlockPos pos, boolean doDrain) {
        Fluid fluid = drainableFluid(level, pos);
        if (fluid != null && doDrain) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return fluid;
    }

    /** Upstream {@code computeBlockBreakPower}: 16 MJ times twice (hardness + 1). */
    public static long computeBlockBreakPower(Level level, BlockPos pos) {
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return (long) Math.floor(16 * Power.MJ * ((hardness + 1) * 2));
    }

    /** Upstream {@code isUnbreakableBlock}: negative hardness and very dense fluids. */
    public static boolean isUnbreakableBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0) return true;
        FluidState fluid = state.getFluidState();
        return state.getBlock() instanceof LiquidBlock && !fluid.isEmpty() && Platform.get().viscosity(fluid.getType()) >= 3000;
    }

    /**
     * Upstream {@code breakBlockAndGetDrops}: harvests as if with a diamond pickaxe, then collects the item
     * entities that breaking spawned around the block.
     */
    public static List<ItemStack> breakBlockAndGetDrops(ServerLevel level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(1);
        Set<ItemEntity> existing = Collections.newSetFromMap(new IdentityHashMap<>());
        existing.addAll(level.getEntitiesOfClass(ItemEntity.class, area));
        BlockState state = level.getBlockState(pos);
        BlockEntity tile = level.getBlockEntity(pos);
        level.levelEvent(2001, pos, Block.getId(state));
        Block.dropResources(state, level, pos, tile, null, new ItemStack(Items.DIAMOND_PICKAXE));
        level.removeBlock(pos, false);
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (existing.contains(entity) || !entity.isAlive()) continue;
            stacks.add(entity.getItem().copy());
            entity.discard();
        }
        return stacks;
    }
}
