package ports.buildcraft.silicon.plug;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream {@code FacadePhasedState}: one block state of a facade and, for a phased facade, the dye colour that
 * shows it. Nothing in this port changes the colour of a placed facade; the colour is kept so a saved phased
 * facade still names its states.
 */
public final class FacadePhasedState {
    public final FacadeBlockStateInfo info;
    @Nullable
    public final DyeColor activeColour;

    public FacadePhasedState(FacadeBlockStateInfo info, @Nullable DyeColor activeColour) {
        this.info = info;
        this.activeColour = activeColour;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("state", NbtUtils.writeBlockState(info.state));
        if (activeColour != null) tag.putString("activeColour", activeColour.getName());
        return tag;
    }

    public static FacadePhasedState load(CompoundTag tag) {
        FacadeBlockStateInfo info = FacadeStateManager.defaultState();
        if (tag.contains("state")) {
            BlockState state = NbtUtils.readBlockState(tag.getCompound("state"));
            FacadeBlockStateInfo known = FacadeStateManager.info(state);
            if (known != null) info = known;
        }
        DyeColor colour = tag.contains("activeColour") ? DyeColor.byName(tag.getString("activeColour"), null) : null;
        return new FacadePhasedState(info, colour);
    }
}
