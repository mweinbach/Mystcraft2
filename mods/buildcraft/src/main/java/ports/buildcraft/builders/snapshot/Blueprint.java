package ports.buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream {@code Blueprint}: a palette of block schematics, the palette index of every block in the box, and
 * the entities found in it.
 */
public class Blueprint extends Snapshot {
    /** A scan records entities whose bounds touch the box; the largest it captures, paintings, reach 2 blocks from their centre. */
    private static final double ENTITY_MARGIN = 4;
    public final List<SchematicBlock> palette = new ArrayList<>();
    public int[] data = new int[0];
    public final List<SchematicEntity> entities = new ArrayList<>();

    @Override
    public Blueprint copy() {
        Blueprint blueprint = new Blueprint();
        blueprint.size = size;
        blueprint.facing = facing;
        blueprint.offset = offset;
        blueprint.palette.addAll(palette);
        blueprint.data = data.clone();
        blueprint.entities.addAll(entities);
        blueprint.computeKey();
        return blueprint;
    }

    public void replace(SchematicBlock from, SchematicBlock to) {
        Collections.replaceAll(palette, from, to);
    }

    public SchematicBlock get(int x, int y, int z) {
        return palette.get(data[posToIndex(x, y, z)]);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        ListTag paletteTag = new ListTag();
        palette.forEach(schematicBlock -> paletteTag.add(SchematicBlockManager.writeToNBT(schematicBlock)));
        tag.put("palette", paletteTag);
        ListTag dataTag = new ListTag();
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) dataTag.add(IntTag.valueOf(data[posToIndex(x, y, z)]));
            }
        }
        tag.put("data", dataTag);
        ListTag entitiesTag = new ListTag();
        entities.forEach(schematicEntity -> entitiesTag.add(SchematicEntityManager.writeToNBT(schematicEntity)));
        tag.put("entities", entitiesTag);
        return tag;
    }

    /** Upstream reads the data as either an int list or an int array. */
    @Override
    public void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException {
        super.deserializeNBT(tag);
        palette.clear();
        for (Tag schematicBlockTag : tag.getList("palette", Tag.TAG_COMPOUND)) palette.add(SchematicBlockManager.readFromNBT((CompoundTag) schematicBlockTag));
        ListTag dataList = tag.contains("data", Tag.TAG_LIST) ? tag.getList("data", Tag.TAG_INT) : null;
        int[] dataArray = tag.contains("data", Tag.TAG_INT_ARRAY) ? tag.getIntArray("data") : null;
        if (dataList == null && dataArray == null) throw new InvalidSnapshotException("Can't read a blueprint with no data!");
        int length = dataList == null ? dataArray.length : dataList.size();
        if (length != getDataSize()) {
            throw new InvalidSnapshotException("Serialized data has length of " + length + ", but we expected " + getDataSize() + " (" + size.toShortString() + ")");
        }
        data = new int[getDataSize()];
        for (int i = 0; i < data.length; i++) {
            int index = dataList == null ? dataArray[i] : dataList.getInt(i);
            if (index < 0 || index >= palette.size()) throw new InvalidSnapshotException("Palette index " + index + " is outside the palette of " + palette.size());
            data[i] = index;
        }
        entities.clear();
        for (Tag schematicEntityTag : tag.getList("entities", Tag.TAG_COMPOUND)) {
            SchematicEntity schematicEntity = SchematicEntityManager.readFromNBT((CompoundTag) schematicEntityTag);
            Vec3 pos = schematicEntity.getPos();
            if (pos.x < -ENTITY_MARGIN || pos.y < -ENTITY_MARGIN || pos.z < -ENTITY_MARGIN || pos.x > size.getX() + ENTITY_MARGIN
                    || pos.y > size.getY() + ENTITY_MARGIN || pos.z > size.getZ() + ENTITY_MARGIN) {
                throw new InvalidSnapshotException("Entity at " + pos + " is outside the blueprint of " + size.toShortString());
            }
            entities.add(schematicEntity);
        }
    }

    @Override
    public EnumSnapshotType getType() {
        return EnumSnapshotType.BLUEPRINT;
    }
}
