package ports.buildcraft.factory;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.BlockUtil;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.platform.ItemPort;
import ports.buildcraft.platform.Platform;

/** Upstream {@code TileMiningWell}: digs the column below itself and hands drops to its neighbours. */
public class MiningWellBlockEntity extends MinerBlockEntity {
    /**
     * Upstream re-checked whenever a block in the column changed; 1.19.2 has no world listener for that,
     * so an idle well rescans its column at this interval instead.
     */
    private static final int IDLE_RECHECK = 20;
    private boolean shouldCheck = true;

    public MiningWellBlockEntity(BlockPos pos, BlockState state) {
        super(BCFactory.MINING_WELL_TYPE.get(), pos, state);
    }

    @Override
    protected void mine() {
        if (level == null) return;
        if (currentPos != null && canBreak()) {
            shouldCheck = true;
            long target = BlockUtil.computeBlockBreakPower(level, currentPos);
            progress += battery.extractPower(0, target - progress);
            if (progress >= target) {
                progress = 0;
                level.destroyBlockProgress(currentPos.hashCode(), currentPos, -1);
                for (ItemStack stack : BlockUtil.breakBlockAndGetDrops((ServerLevel) level, currentPos)) {
                    InventoryUtil.addToBestAcceptor(level, worldPosition, null, stack);
                }
                nextPos();
            } else if (!level.isEmptyBlock(currentPos)) {
                level.destroyBlockProgress(currentPos.hashCode(), currentPos, (int) ((progress * 9) / target));
            }
            setChanged();
        } else if (shouldCheck || level.getGameTime() % IDLE_RECHECK == 0) {
            nextPos();
            if (currentPos == null) shouldCheck = false;
        }
    }

    private boolean canBreak() {
        if (level.isEmptyBlock(currentPos) || BlockUtil.isUnbreakableBlock(level, currentPos)) return false;
        Fluid fluid = BlockUtil.fluidWithFlowing(level, currentPos);
        return fluid == null || Platform.get().viscosity(fluid) <= 1000;
    }

    private void nextPos() {
        currentPos = worldPosition;
        while (true) {
            currentPos = currentPos.below();
            if (level.isOutsideBuildHeight(currentPos)) break;
            if (worldPosition.getY() - currentPos.getY() > MAX_DEPTH) break;
            if (canBreak()) {
                updateLength();
                return;
            } else if (!level.isEmptyBlock(currentPos) && !level.getBlockState(currentPos).is(BCFactory.TUBE.get())) {
                break;
            }
        }
        currentPos = null;
        updateLength();
    }

    @Override
    public ItemPort itemPort(@Nullable Direction side) {
        return InventoryUtil.PROVIDING;
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && currentPos != null) level.destroyBlockProgress(currentPos.hashCode(), currentPos, -1);
        super.setRemoved();
    }
}
