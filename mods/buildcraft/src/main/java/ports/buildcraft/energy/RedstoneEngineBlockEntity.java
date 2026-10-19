package ports.buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.Power;

/** Upstream {@code TileEngineRedstone_BC8}: powers only wooden pipes and pumps, and never overheats. */
public class RedstoneEngineBlockEntity extends EngineBlockEntity {
    public RedstoneEngineBlockEntity(BlockPos pos, BlockState state) {
        super(BCEnergy.REDSTONE_ENGINE_TYPE.get(), pos, state);
    }

    @Override protected boolean isRedstoneOnly() { return true; }
    @Override public boolean isBurning() { return isRedstonePowered; }

    @Override
    protected void engineUpdate() {
        super.engineUpdate();
        if (isRedstonePowered) {
            power = getMaxPower();
            if (level.getGameTime() % 16 == 0 && getHeatLevel() < 0.8) heat += 4;
        } else {
            power = 0;
        }
    }

    @Override
    public double getPistonSpeed() {
        return super.getPistonSpeed() / 2;
    }

    @Override
    public void updateHeatLevel() {
        if (heat > MIN_HEAT) {
            heat -= 0.2f;
            if (heat < MIN_HEAT) heat = MIN_HEAT;
        }
    }

    @Override protected int getMaxChainLength() { return 0; }
    @Override public long getMaxPower() { return Power.MJ; }
    @Override public long maxPowerExtracted() { return 4 * Power.MJ; }
    @Override public long getCurrentOutput() { return Power.MJ / 20; }
}
