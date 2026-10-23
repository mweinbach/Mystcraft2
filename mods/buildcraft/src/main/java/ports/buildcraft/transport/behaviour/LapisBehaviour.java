package ports.buildcraft.transport.behaviour;

import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.ColouredPipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.statements.ActionPipeColor;
import ports.buildcraft.transport.statements.BCTransportStatements;

/**
 * Upstream {@code PipeBehaviourLapis}: every item reaching the centre leaves painted the pipe's colour. A wrench
 * steps the colour forward (back while sneaking) and the gate paint actions set it; the pipe shows its colour.
 */
public class LapisBehaviour extends PipeBehaviour {
    private DyeColor colour = DyeColor.WHITE;

    public LapisBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    public DyeColor colour() {
        return colour;
    }

    public void setColour(DyeColor colour) {
        if (this.colour == colour) return;
        this.colour = colour;
        pipe.setChanged();
        pipe.refreshState();
    }

    /** Upstream's wrench step: the next colour by id, or the previous one when {@code back}. */
    static DyeColor step(DyeColor colour, boolean back) {
        return DyeColor.byId((colour.getId() + (back ? 15 : 1)) & 15);
    }

    /** Upstream {@code getTextureIndex}: the block shows the pipe's colour. */
    @Override
    public BlockState modelState(BlockState state) {
        return state.hasProperty(ColouredPipeBlock.COLOUR) ? state.setValue(ColouredPipeBlock.COLOUR, colour) : state;
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        if (!Wrenches.isWrench(player.getItemInHand(hand))) return InteractionResult.PASS;
        setColour(step(colour, player.isShiftKeyDown()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public DyeColor colourAtCenter(@Nullable DyeColor colour) {
        return this.colour;
    }

    @Override
    public void addInternalActions(Collection<Action.Internal> actions) {
        Collections.addAll(actions, BCTransportStatements.ACTION_PIPE_COLOUR);
    }

    @Override
    public void onActionActivate(Action action, StatementParameter[] parameters) {
        if (action instanceof ActionPipeColor paint) setColour(paint.colour);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putByte("colour", (byte) colour.getId());
    }

    @Override
    public void load(CompoundTag tag) {
        colour = tag.contains("colour") ? DyeColor.byId(tag.getByte("colour") & 15) : DyeColor.WHITE;
    }
}
