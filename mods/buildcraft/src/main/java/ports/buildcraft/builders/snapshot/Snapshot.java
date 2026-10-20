package ports.buildcraft.builders.snapshot;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code Snapshot}: a recorded box of {@code size} blocks, the direction the architect table
 * faced when it was taken, and the box's offset from the block behind the table. Its {@link Key} is a
 * hash of everything else, so equal snapshots share a key and a file.
 */
public abstract class Snapshot {
    public Key key = new Key();
    public BlockPos size = BlockPos.ZERO;
    public Direction facing = Direction.NORTH;
    public BlockPos offset = BlockPos.ZERO;

    public static Snapshot create(EnumSnapshotType type) {
        return switch (type) {
            case TEMPLATE -> new Template();
            case BLUEPRINT -> new Blueprint();
        };
    }

    public static int posToIndex(BlockPos size, int x, int y, int z) {
        return ((z * size.getY()) + y) * size.getX() + x;
    }

    public static int posToIndex(BlockPos size, BlockPos pos) {
        return posToIndex(size, pos.getX(), pos.getY(), pos.getZ());
    }

    public int posToIndex(int x, int y, int z) {
        return posToIndex(size, x, y, z);
    }

    public int posToIndex(BlockPos pos) {
        return posToIndex(size, pos);
    }

    public static BlockPos indexToPos(BlockPos size, int i) {
        return new BlockPos(i % size.getX(), (i / size.getX()) % size.getY(), i / (size.getY() * size.getX()));
    }

    public BlockPos indexToPos(int i) {
        return indexToPos(size, i);
    }

    public static int getDataSize(BlockPos size) {
        return size.getX() * size.getY() * size.getZ();
    }

    public int getDataSize() {
        return getDataSize(size);
    }

    /** The block count of a box of {@code size}, which may exceed an {@code int}. */
    public static long getVolume(BlockPos size) {
        return (long) size.getX() * size.getY() * size.getZ();
    }

    public static CompoundTag writeToNBT(Snapshot snapshot) {
        CompoundTag tag = snapshot.serializeNBT();
        tag.putString("type", snapshot.getType().name());
        return tag;
    }

    public static Snapshot readFromNBT(CompoundTag tag) throws InvalidSnapshotException {
        EnumSnapshotType type;
        try {
            type = EnumSnapshotType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            throw new InvalidSnapshotException("Unknown snapshot type " + tag.get("type"));
        }
        Snapshot snapshot = create(type);
        snapshot.deserializeNBT(tag);
        return snapshot;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("key", key.serializeNBT());
        tag.put("size", NbtUtils.writeBlockPos(size));
        tag.putString("facing", facing.name());
        tag.put("offset", NbtUtils.writeBlockPos(offset));
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) throws InvalidSnapshotException {
        key = new Key(tag.getCompound("key"));
        size = NbtUtils.readBlockPos(tag.getCompound("size"));
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || getVolume(size) > Integer.MAX_VALUE) {
            throw new InvalidSnapshotException("Invalid size " + size.toShortString());
        }
        try {
            facing = Direction.valueOf(tag.getString("facing"));
        } catch (IllegalArgumentException e) {
            throw new InvalidSnapshotException("Unknown facing " + tag.get("facing"));
        }
        offset = NbtUtils.readBlockPos(tag.getCompound("offset"));
    }

    public abstract Snapshot copy();

    public abstract EnumSnapshotType getType();

    public void computeKey() {
        CompoundTag tag = writeToNBT(this);
        if (tag.contains("key", Tag.TAG_COMPOUND)) tag.remove("key");
        key = new Key(key, SnapshotHash.compute(tag));
    }

    @Override
    public String toString() {
        return "Snapshot{key=" + key + ", size=" + size.toShortString() + ", facing=" + facing + ", offset=" + offset + "}";
    }

    /** Upstream {@code Snapshot.Key}: the hash, plus a header on copies kept in a player's local library. */
    public static final class Key {
        public final byte[] hash;
        @Nullable public final Header header;

        public Key() {
            hash = new byte[0];
            header = null;
        }

        public Key(Key oldKey, byte[] hash) {
            this.hash = hash;
            this.header = oldKey.header;
        }

        public Key(Key oldKey, @Nullable Header header) {
            this.hash = oldKey.hash;
            this.header = header;
        }

        public Key(CompoundTag tag) {
            hash = tag.getByteArray("hash");
            header = tag.contains("header") ? new Header(tag.getCompound("header")) : null;
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putByteArray("hash", hash);
            if (header != null) tag.put("header", header.serializeNBT());
            return tag;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Key other && Arrays.equals(hash, other.hash) && Objects.equals(header, other.header);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }

        @Override
        public String toString() {
            return SnapshotHash.toHex(hash);
        }
    }

    /** Upstream {@code Snapshot.Header}: who recorded a snapshot, when, and the name typed into the architect table. */
    public static final class Header {
        public final Key key;
        public final UUID owner;
        public final Date created;
        public final String name;

        public Header(Key key, UUID owner, Date created, String name) {
            this.key = key;
            this.owner = owner;
            this.created = created;
            this.name = name;
        }

        public Header(CompoundTag tag) {
            key = new Key(tag.getCompound("key"));
            owner = tag.hasUUID("owner") ? tag.getUUID("owner") : new UUID(0, 0);
            created = new Date(tag.getLong("created"));
            name = tag.getString("name");
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put("key", key.serializeNBT());
            tag.putUUID("owner", owner);
            tag.putLong("created", created.getTime());
            tag.putString("name", name);
            return tag;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Header other && key.equals(other.key) && owner.equals(other.owner)
                    && created.equals(other.created) && name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, owner, created, name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
