package ports.buildcraft.core.marker.volume;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code WorldSavedDataVolumeBoxes}: a level's volume boxes. Every change sends the whole list
 * to the level's players, as upstream's {@code MessageVolumeBoxes} did.
 */
public final class VolumeBoxes extends SavedData {
    public static final ResourceLocation CHANNEL = BuildCraft.id("volume_boxes");
    private static final String DATA_NAME = "buildcraft_volume_boxes";

    private final ServerLevel level;
    public final List<VolumeBox> volumeBoxes = new ArrayList<>();

    private VolumeBoxes(ServerLevel level) {
        this.level = level;
    }

    public static VolumeBoxes get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), () -> new VolumeBoxes(level), DATA_NAME);
    }

    public static VolumeBoxes load(ServerLevel level, CompoundTag tag) {
        VolumeBoxes data = new VolumeBoxes(level);
        for (Tag element : tag.getList("volumeBoxes", Tag.TAG_COMPOUND)) data.volumeBoxes.add(VolumeBox.load((CompoundTag) element));
        return data;
    }

    @Nullable
    public VolumeBox getVolumeBoxAt(BlockPos pos) {
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.box.contains(pos)) return volumeBox;
        }
        return null;
    }

    public VolumeBox addVolumeBox(BlockPos pos) {
        VolumeBox volumeBox = new VolumeBox(pos);
        volumeBoxes.add(volumeBox);
        return volumeBox;
    }

    @Nullable
    public VolumeBox getVolumeBoxFromId(UUID id) {
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.id.equals(id)) return volumeBox;
        }
        return null;
    }

    @Nullable
    public VolumeBox getCurrentEditing(Player player) {
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.isEditingBy(player)) return volumeBox;
        }
        return null;
    }

    /** Upstream {@code tick}: edited boxes follow their player's gaze, and locks whose block is gone lapse. */
    public void tick() {
        tick(level::getPlayerByUUID);
    }

    /** {@link #tick()} with editing players looked up by their game profile id through {@code players}. */
    public void tick(Function<UUID, Player> players) {
        boolean dirty = false;
        for (VolumeBox volumeBox : volumeBoxes) {
            if (!volumeBox.isEditing()) continue;
            Player player = players.apply(volumeBox.editingPlayer());
            if (player == null) {
                volumeBox.pauseEditing();
                dirty = true;
                continue;
            }
            BlockPos oldMin = volumeBox.box.min();
            BlockPos oldMax = volumeBox.box.max();
            volumeBox.box.reset();
            volumeBox.box.extendToEncompass(volumeBox.getHeld());
            Vec3 lookingAt = player.getEyePosition().add(player.getLookAngle().scale(volumeBox.getDist()));
            volumeBox.box.extendToEncompass(new BlockPos(lookingAt));
            if (!volumeBox.box.min().equals(oldMin) || !volumeBox.box.max().equals(oldMax)) dirty = true;
        }
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.locks.removeIf(lock -> !lock.cause.stillWorks(level))) dirty = true;
        }
        if (dirty) markDirty();
    }

    /** Upstream {@code markDirty}: saves and sends every box to the level's players. */
    public void markDirty() {
        setDirty();
        for (ServerPlayer player : level.players()) sendTo(player);
    }

    public void sendTo(ServerPlayer player) {
        Platform.get().sendToPlayer(player, CHANNEL, this::write);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(volumeBoxes.size());
        for (VolumeBox volumeBox : volumeBoxes) volumeBox.write(buffer);
    }

    public static List<VolumeBox> read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<VolumeBox> boxes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) boxes.add(VolumeBox.read(buffer));
        return boxes;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (VolumeBox volumeBox : volumeBoxes) list.add(volumeBox.save());
        tag.put("volumeBoxes", list);
        return tag;
    }
}
