package ports.buildcraft.silicon.plug;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;

/**
 * Upstream {@code FacadeInstance}: the states a facade shows and whether it is hollow. One state is a basic
 * facade; more than one is phased. A phased facade has at most one state per dye colour and one with none.
 */
public final class FacadeInstance {
    public static final int MAX_STATES = 17;

    public final FacadePhasedState[] phasedStates;
    public final boolean phased;
    public final boolean hollow;

    public FacadeInstance(FacadePhasedState[] phasedStates, boolean hollow) {
        if (phasedStates == null || phasedStates.length == 0 || phasedStates.length > MAX_STATES) {
            throw new IllegalArgumentException("a facade needs between 1 and " + MAX_STATES + " states");
        }
        this.phasedStates = phasedStates;
        this.phased = phasedStates.length > 1;
        this.hollow = hollow;
    }

    public static FacadeInstance single(FacadeBlockStateInfo info, boolean hollow) {
        return new FacadeInstance(new FacadePhasedState[] {new FacadePhasedState(info, null)}, hollow);
    }

    public static FacadeInstance read(CompoundTag tag) {
        ListTag list = tag.getList("states", Tag.TAG_COMPOUND);
        if (list.isEmpty()) return single(FacadeStateManager.defaultState(), false);
        FacadePhasedState[] states = new FacadePhasedState[Math.min(list.size(), MAX_STATES)];
        for (int i = 0; i < states.length; i++) states[i] = FacadePhasedState.load(list.getCompound(i));
        return new FacadeInstance(states, tag.getBoolean("isHollow"));
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (FacadePhasedState state : phasedStates) list.add(state.save());
        tag.put("states", list);
        tag.putBoolean("isHollow", hollow);
        return tag;
    }

    public boolean canAddColour(@Nullable DyeColor colour) {
        for (FacadePhasedState state : phasedStates) {
            if (state.activeColour == colour) return false;
        }
        return true;
    }

    /** The state a held phased facade shows, advancing every half second, as upstream's stack preview. */
    public FacadePhasedState currentForStack() {
        if (phasedStates.length == 1) return phasedStates[0];
        int now = (int) (System.currentTimeMillis() % 100_000);
        return phasedStates[(now / 500) % phasedStates.length];
    }

    public FacadeInstance withSwappedHollow() {
        return new FacadeInstance(phasedStates, !hollow);
    }

    @Nullable
    public FacadeInstance withState(FacadePhasedState state) {
        if (!canAddColour(state.activeColour)) return null;
        FacadePhasedState[] states = Arrays.copyOf(phasedStates, phasedStates.length + 1);
        states[states.length - 1] = state;
        return new FacadeInstance(states, hollow);
    }

    public boolean areAllStatesSolid(Direction side) {
        for (FacadePhasedState state : phasedStates) {
            if (!state.info.isSideSolid(side)) return false;
        }
        return true;
    }
}
