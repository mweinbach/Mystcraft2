package ports.buildcraft.transport.behaviour;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.lib.Power;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.LimiterPipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.statements.ActionPowerLimit;

/**
 * Upstream {@code PipeBehaviourLimiter}, the iron and diamond kinesis pipes: the most power the pipe asks for
 * through each face is its rate shifted right by the limit, from the full rate to 1/32 of it, and the last
 * step stops it asking its neighbouring pipes for power at all. A wrench steps through the limits and says the
 * new one on the action bar; the kinesis power limit gate action sets one. Upstream gives the pipe no output
 * direction: it sends power wherever it is requested.
 */
public class LimiterBehaviour extends PipeBehaviour {
    public static final int MAX_SHIFT = 6;

    private int limitShift;

    public LimiterBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    public int limitShift() {
        return limitShift;
    }

    public void setLimitShift(int shift) {
        shift = Mth.clamp(shift, 0, MAX_SHIFT);
        if (shift == limitShift) return;
        limitShift = shift;
        pipe.setChanged();
        pipe.refreshState();
    }

    /** The limit in whole MJ per tick for {@code shift}, as upstream's chat message and action description. */
    public static int limitMj(long transferPerTick, int shift) {
        return shift >= MAX_SHIFT ? 0 : (int) ((transferPerTick >> shift) / Power.MJ);
    }

    @Override
    public long configureMaxPower(long maxPower) {
        return limitShift == MAX_SHIFT ? maxPower : maxPower >> limitShift;
    }

    @Override
    public boolean disablesPowerTransfer() {
        return limitShift == MAX_SHIFT;
    }

    @Override
    public void onActionActivate(Action action, StatementParameter[] parameters) {
        if (action instanceof ActionPowerLimit limit) setLimitShift(limit.limitShift);
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        if (!Wrenches.isWrench(player.getItemInHand(hand))) return InteractionResult.PASS;
        setLimitShift(limitShift >= MAX_SHIFT ? 0 : limitShift + 1);
        int limit = limitMj(pipe.definition().powerTransferPerTick, limitShift);
        player.displayClientMessage(Component.translatable("chat.pipe.power.iron.mode", limit), true);
        return InteractionResult.SUCCESS;
    }

    /** Upstream {@code getTextureIndex}: {@code MAX_SHIFT - limitShift} of the {@code _m0} to {@code _m128} textures. */
    @Override
    public BlockState modelState(BlockState state) {
        return state.hasProperty(LimiterPipeBlock.LIMIT_SHIFT) ? state.setValue(LimiterPipeBlock.LIMIT_SHIFT, limitShift) : state;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("limitShift", limitShift);
    }

    @Override
    public void load(CompoundTag tag) {
        limitShift = Mth.clamp(tag.getInt("limitShift"), 0, MAX_SHIFT);
    }
}
