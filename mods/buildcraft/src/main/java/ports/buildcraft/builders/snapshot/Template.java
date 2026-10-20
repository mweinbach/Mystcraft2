package ports.buildcraft.builders.snapshot;

import java.util.BitSet;
import net.minecraft.nbt.CompoundTag;

/** Upstream {@code Template}: one bit per block of the box, set where the scan found a block. */
public class Template extends Snapshot {
    public BitSet data = new BitSet();

    @Override
    public Template copy() {
        Template template = new Template();
        template.size = size;
        template.facing = facing;
        template.offset = offset;
        template.data = (BitSet) data.clone();
        template.computeKey();
        return template;
    }

    public void invert() {
        data.flip(0, getDataSize());
    }

    public boolean get(int x, int y, int z) {
        return data.get(posToIndex(x, y, z));
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putByteArray("data", data.toByteArray());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException {
        super.deserializeNBT(tag);
        data = BitSet.valueOf(tag.getByteArray("data"));
        if (data.length() > getDataSize()) {
            throw new InvalidSnapshotException("Serialized data has length of " + data.length() + ", but we expected at most "
                    + getDataSize() + " (" + size.toShortString() + ")");
        }
    }

    @Override
    public EnumSnapshotType getType() {
        return EnumSnapshotType.TEMPLATE;
    }
}
