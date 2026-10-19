package net.mystcraft.minecraft.linking;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Versioned destination data; malformed or incomplete books never resolve a destination. */
public record LinkDestination(ResourceLocation dimensionId, double x, double y, double z,
                              float yaw, float pitch) {
    public static final String TAG = "MystcraftLink";
    /** Resolve a registry key only at the runtime boundary; saved data needs no registry bootstrap. */
    public ResourceKey<Level> dimension() {
        return ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
    }

    public static LinkDestination at(ServerPlayer player) {
        return new LinkDestination(player.getLevel().dimension().location(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
    }

    public void write(ItemStack book) {
        book.getOrCreateTag().put(TAG, toTag());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putString("Dimension", dimensionId.toString());
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        return tag;
    }

    public static Optional<LinkDestination> read(ItemStack book) {
        if (!book.hasTag() || !book.getTag().contains(TAG, Tag.TAG_COMPOUND)) return Optional.empty();
        return readTag(book.getTag().getCompound(TAG));
    }

    public static Optional<LinkDestination> readTag(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !tag.contains("Dimension", Tag.TAG_STRING)) return Optional.empty();
        for (String field : new String[]{"X", "Y", "Z", "Yaw", "Pitch"}) {
            if (!tag.contains(field, Tag.TAG_ANY_NUMERIC)) return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        double x = tag.getDouble("X"), y = tag.getDouble("Y"), z = tag.getDouble("Z");
        float yaw = tag.getFloat("Yaw"), pitch = tag.getFloat("Pitch");
        if (id == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 29999984 || Math.abs(z) > 29999984 || Math.abs(y) > 20000000
                || pitch < -90 || pitch > 90) return Optional.empty();
        return Optional.of(new LinkDestination(id,
                x, y, z, yaw, pitch));
    }
}
