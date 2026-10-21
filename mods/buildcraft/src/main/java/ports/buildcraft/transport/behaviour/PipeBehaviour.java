package ports.buildcraft.transport.behaviour;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.ItemEntry;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;

/**
 * Upstream {@code PipeBehaviour}. Upstream behaviours subscribe to pipe events; the events used by
 * the ported pipes are plain overridable hooks here.
 */
public abstract class PipeBehaviour {
    /** Upstream {@code PipeEventItem.ModifySpeed#modifyTo}. */
    public record SpeedTarget(double target, double maxDelta) {}

    protected final PipeBlockEntity pipe;

    protected PipeBehaviour(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    public boolean canConnect(Direction face, PipeBehaviour other) {
        return true;
    }

    public boolean canConnectTile(Direction face, BlockEntity tile) {
        return true;
    }

    /** True when the connected {@code face} uses the pipe's alternate texture. */
    public boolean isSpecialSide(Direction face) {
        return false;
    }

    public void onTick() {}

    /** @param part the face whose arm was clicked, or null for the pipe centre */
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, @Nullable Direction part) {
        return InteractionResult.PASS;
    }

    public ItemStack onItemReachCenter(ItemStack stack, Direction from) {
        return stack;
    }

    public void itemSideCheck(SideCheck check) {}

    public boolean canBounce() {
        return false;
    }

    public void split(List<EnumSet<Direction>> order, List<ItemEntry> items, RandomSource random) {}

    @Nullable
    public SpeedTarget speedTarget() {
        return null;
    }

    public void fluidSideCheck(SideCheck check) {}

    public boolean canInsertFluid(Direction from) {
        return true;
    }

    public void onMoveFluidToCentre(int[] fluidEnteringCentre) {}

    @Nullable
    public EnergyPort energyPort(@Nullable Direction side) {
        return null;
    }

    /**
     * Upstream {@code IMjRedstoneReceiver.receivePower}: microjoules a pulsar offers this pipe, returning what was
     * not used. Pipes that do not take redstone power use none of it.
     */
    public long receiveRedstonePower(long microJoules, boolean simulate) {
        return microJoules;
    }

    /** Upstream {@code PipeEventStatement.AddActionInternal}, as fired to the behaviour. */
    public void addInternalActions(Collection<Action.Internal> actions) {}

    /** Upstream {@code PipeEventActionActivate}: a gate on this pipe has {@code action} active this tick. */
    public void onActionActivate(Action action, StatementParameter[] parameters) {}

    public void save(CompoundTag tag) {}

    public void load(CompoundTag tag) {}
}
