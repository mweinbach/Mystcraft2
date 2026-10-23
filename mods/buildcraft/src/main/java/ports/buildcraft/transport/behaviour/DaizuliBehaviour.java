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
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.statements.ActionPipeColor;
import ports.buildcraft.transport.statements.BCTransportStatements;

/**
 * Upstream {@code PipeBehaviourDaizuli}: items of the pipe's colour leave only through its selected face, and
 * every other item may go anywhere but there. A wrench on another connected arm selects that face; on the centre
 * or the selected face it steps the colour (back while sneaking). Gates set the face and the colour. As upstream,
 * any face may be selected, and a new pipe has none, so items of its colour find no exit.
 */
public class DaizuliBehaviour extends DirectionalBehaviour {
    private DyeColor colour = DyeColor.WHITE;

    public DaizuliBehaviour(PipeBlockEntity pipe) {
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

    @Override
    protected boolean canFaceDirection(@Nullable Direction dir) {
        return true;
    }

    /** Upstream texture index: only the selected face shows the colour. */
    @Override
    public boolean isSpecialSide(Direction face) {
        return face == currentDir;
    }

    /** Upstream {@code getTextureIndex}: the block shows the pipe's colour. */
    @Override
    public BlockState modelState(BlockState state) {
        return state.hasProperty(ColouredPipeBlock.COLOUR) ? state.setValue(ColouredPipeBlock.COLOUR, colour) : state;
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        if (part != null && part != currentDir) return super.onUse(player, hand, part);
        if (!Wrenches.isWrench(player.getItemInHand(hand))) return InteractionResult.PASS;
        setColour(LapisBehaviour.step(colour, player.isShiftKeyDown()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void itemSideCheck(SideCheck check) {
        if (colour == check.colour) {
            if (currentDir == null) check.disallowAll();
            else check.disallowAllExcept(currentDir);
        } else if (currentDir != null) {
            check.disallow(currentDir);
        }
    }

    @Override
    public void addInternalActions(Collection<Action.Internal> actions) {
        super.addInternalActions(actions);
        Collections.addAll(actions, BCTransportStatements.ACTION_PIPE_COLOUR);
    }

    @Override
    public void onActionActivate(Action action, StatementParameter[] parameters) {
        super.onActionActivate(action, parameters);
        if (action instanceof ActionPipeColor paint) setColour(paint.colour);
    }

    @Override
    public void save(CompoundTag tag) {
        super.save(tag);
        tag.putByte("colour", (byte) colour.getId());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        colour = tag.contains("colour") ? DyeColor.byId(tag.getByte("colour") & 15) : DyeColor.WHITE;
    }
}
