package ports.buildcraft.silicon.plug;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * Upstream {@code PluggableFacade}. A full facade blocks its face, as a plug does; a hollow one leaves the pipe
 * connected. Both add a two-pixel slab across the face. The copied state is drawn by {@code FacadeRenderer}.
 */
public class PluggableFacade extends PipePluggable {
    public static final int SIZE = 2;
    private static final AABB[] BOXES = faceBoxes(0, SIZE, 0, 16);

    private FacadeInstance states = FacadeInstance.single(FacadeStateManager.defaultState(), false);
    private boolean sideSolid;

    public PluggableFacade(PluggableDefinition definition, PipeBlockEntity holder, Direction side) {
        super(definition, holder, side);
    }

    public PluggableFacade(PluggableDefinition definition, PipeBlockEntity holder, Direction side, FacadeInstance states) {
        super(definition, holder, side);
        setStates(states);
    }

    public FacadeInstance states() {
        return states;
    }

    public FacadePhasedState active() {
        int index = 0;
        return states.phasedStates[index];
    }

    public boolean isHollow() {
        return states.hollow;
    }

    @Override
    public boolean isSideSolid() {
        return sideSolid;
    }

    private void setStates(FacadeInstance states) {
        this.states = states;
        this.sideSolid = states.areAllStatesSolid(side);
    }

    @Override
    public AABB getBoundingBox() {
        return BOXES[side.ordinal()];
    }

    @Override
    public boolean isBlocking() {
        return !isHollow();
    }

    @Override
    public ItemStack getPickStack() {
        return FacadeItem.create(states);
    }

    @Override
    public void save(CompoundTag tag) {
        write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        read(tag);
    }

    @Override
    public void writeClient(CompoundTag tag) {
        write(tag);
    }

    @Override
    public void readClient(CompoundTag tag) {
        read(tag);
    }

    private void write(CompoundTag tag) {
        tag.put("facade", states.write());
    }

    private void read(CompoundTag tag) {
        CompoundTag facade = tag.getCompound("facade");
        if (facade.isEmpty() && tag.contains("states", Tag.TAG_LIST)) {
            ListTag states = tag.getList("states", Tag.TAG_COMPOUND);
            if (!states.isEmpty()) {
                facade = new CompoundTag();
                facade.put("states", states);
                facade.putBoolean("isHollow", states.getCompound(0).getBoolean("isHollow"));
            }
        }
        setStates(FacadeInstance.read(facade));
    }
}
