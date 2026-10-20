package ports.buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import ports.buildcraft.lib.Box;

/**
 * Upstream {@code VolumeBox}: a free-standing box made with the volume box item and resized with the
 * marker connector. While a player edits it, one corner stays held and the opposite corner follows
 * the block the player looks at. Upstream's addon slots (the filler planner) are not ported.
 */
public final class VolumeBox {
    public UUID id;
    public final Box box;
    @Nullable private UUID player;
    @Nullable private UUID oldPlayer;
    @Nullable private BlockPos held;
    private double dist;
    @Nullable private BlockPos oldMin, oldMax;
    public final List<Lock> locks = new ArrayList<>();

    public VolumeBox(BlockPos at) {
        id = UUID.randomUUID();
        box = new Box(at, at);
    }

    private VolumeBox(UUID id) {
        this.id = id;
        box = new Box();
    }

    public boolean isEditing() {
        return player != null;
    }

    private void resetEditing() {
        oldMin = oldMax = null;
        held = null;
        dist = 0;
    }

    public void cancelEditing() {
        player = null;
        box.reset();
        if (oldMin != null) box.extendToEncompass(oldMin);
        if (oldMax != null) box.extendToEncompass(oldMax);
        resetEditing();
    }

    public void confirmEditing() {
        player = null;
        resetEditing();
    }

    public void pauseEditing() {
        oldPlayer = player;
        player = null;
    }

    public void resumeEditing() {
        player = oldPlayer;
        oldPlayer = null;
    }

    public void setPlayer(Player player) {
        this.player = player.getGameProfile().getId();
    }

    @Nullable
    public UUID editingPlayer() {
        return player;
    }

    public boolean isEditingBy(@Nullable Player player) {
        return player != null && Objects.equals(this.player, player.getGameProfile().getId());
    }

    public boolean isPausedEditingBy(Player player) {
        return oldPlayer != null && Objects.equals(oldPlayer, player.getGameProfile().getId());
    }

    public void setHeldDistOldMinOldMax(BlockPos held, double dist, BlockPos oldMin, BlockPos oldMax) {
        this.held = held;
        this.dist = dist;
        this.oldMin = oldMin;
        this.oldMax = oldMax;
    }

    @Nullable
    public BlockPos getHeld() {
        return held;
    }

    public double getDist() {
        return dist;
    }

    public Stream<Lock.Target> getLockTargetsStream() {
        return locks.stream().flatMap(lock -> lock.targets.stream());
    }

    public boolean isLockedFor(Class<? extends Lock.Target> target) {
        return getLockTargetsStream().anyMatch(target::isInstance);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.put("box", box.save());
        if (player != null) tag.putUUID("player", player);
        if (oldPlayer != null) tag.putUUID("oldPlayer", oldPlayer);
        if (held != null) tag.put("held", NbtUtils.writeBlockPos(held));
        tag.putDouble("dist", dist);
        if (oldMin != null) tag.put("oldMin", NbtUtils.writeBlockPos(oldMin));
        if (oldMax != null) tag.put("oldMax", NbtUtils.writeBlockPos(oldMax));
        ListTag lockTags = new ListTag();
        for (Lock lock : locks) lockTags.add(lock.save());
        tag.put("locks", lockTags);
        return tag;
    }

    public static VolumeBox load(CompoundTag tag) {
        VolumeBox volumeBox = new VolumeBox(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID());
        volumeBox.box.load(tag.getCompound("box"));
        volumeBox.player = tag.hasUUID("player") ? tag.getUUID("player") : null;
        volumeBox.oldPlayer = tag.hasUUID("oldPlayer") ? tag.getUUID("oldPlayer") : null;
        volumeBox.held = tag.contains("held") ? NbtUtils.readBlockPos(tag.getCompound("held")) : null;
        volumeBox.dist = tag.getDouble("dist");
        volumeBox.oldMin = tag.contains("oldMin") ? NbtUtils.readBlockPos(tag.getCompound("oldMin")) : null;
        volumeBox.oldMax = tag.contains("oldMax") ? NbtUtils.readBlockPos(tag.getCompound("oldMax")) : null;
        for (Tag element : tag.getList("locks", Tag.TAG_COMPOUND)) {
            Lock lock = Lock.load((CompoundTag) element);
            if (lock != null) volumeBox.locks.add(lock);
        }
        return volumeBox;
    }

    /** Upstream {@code toBytes}: the id, box, editing player and locks; the editing state stays on the server. */
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeBlockPos(box.min());
        buffer.writeBlockPos(box.max());
        buffer.writeBoolean(player != null);
        if (player != null) buffer.writeUUID(player);
        buffer.writeVarInt(locks.size());
        for (Lock lock : locks) lock.write(buffer);
    }

    public static VolumeBox read(FriendlyByteBuf buffer) {
        VolumeBox volumeBox = new VolumeBox(buffer.readUUID());
        volumeBox.box.set(buffer.readBlockPos(), buffer.readBlockPos());
        volumeBox.player = buffer.readBoolean() ? buffer.readUUID() : null;
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) volumeBox.locks.add(Lock.read(buffer));
        return volumeBox;
    }

    /** Upstream {@code fromBytes} on a box the client already has: keeps the instance, replaces its state. */
    void copyFrom(VolumeBox other) {
        box.set(other.box.min(), other.box.max());
        player = other.player;
        locks.clear();
        locks.addAll(other.locks);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof VolumeBox other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
