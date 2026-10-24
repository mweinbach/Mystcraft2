package ports.gridwright.deepdark;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where each player entered the Deep Dark from, kept with the overworld's saved data so it works the
 * same on both loaders and survives restarts.
 */
public final class DeepDarkReturnPoints extends SavedData {

    public static final String NAME = "gridwright_deep_dark_returns";

    /** A dimension and position to go back to. */
    public record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot) { }

    private final Map<UUID, ReturnPoint> points = new HashMap<>();

    public static DeepDarkReturnPoints get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DeepDarkReturnPoints::load, DeepDarkReturnPoints::new, NAME);
    }

    @Nullable
    public ReturnPoint get(UUID player) {
        return points.get(player);
    }

    public void put(UUID player, ReturnPoint point) {
        points.put(player, point);
        setDirty();
    }

    public void remove(UUID player) {
        if (points.remove(player) != null) setDirty();
    }

    public Map<UUID, ReturnPoint> all() {
        return Collections.unmodifiableMap(points);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        points.forEach((player, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putString("Dimension", point.dimension().location().toString());
            entry.putDouble("X", point.position().x);
            entry.putDouble("Y", point.position().y);
            entry.putDouble("Z", point.position().z);
            entry.putFloat("YRot", point.yRot());
            entry.putFloat("XRot", point.xRot());
            list.add(entry);
        });
        tag.put("Points", list);
        return tag;
    }

    /** Reads saved points, skipping any entry without a player or with an unreadable dimension id. */
    public static DeepDarkReturnPoints load(CompoundTag tag) {
        DeepDarkReturnPoints data = new DeepDarkReturnPoints();
        for (Tag element : tag.getList("Points", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (!entry.hasUUID("Player") || dimension == null || entry.getString("Dimension").isEmpty()) continue;
            data.points.put(entry.getUUID("Player"), new ReturnPoint(ResourceKey.create(Registry.DIMENSION_REGISTRY, dimension),
                    new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")), entry.getFloat("YRot"), entry.getFloat("XRot")));
        }
        return data;
    }
}
