package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonElement;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code NbtRef} for single values: a JSON list is a path into the block entity or entity data, and
 * anything else is the value itself.
 */
public final class NbtRef {
    @Nullable private final NbtPath path;
    @Nullable private final Tag value;

    private NbtRef(@Nullable NbtPath path, @Nullable Tag value) {
        this.path = path;
        this.value = value;
    }

    @Nullable
    public static NbtRef fromJson(@Nullable JsonElement json) {
        if (json == null) return null;
        return json.isJsonArray() ? new NbtRef(NbtPath.fromJson(json), null) : new NbtRef(null, NbtJson.fromJson(json));
    }

    public boolean isPath() {
        return path != null;
    }

    public Optional<Tag> get(@Nullable Tag nbt) {
        Tag found = path != null ? path.get(nbt) : value;
        return found == null || found == NbtJson.NULL ? Optional.empty() : Optional.of(found);
    }

    @Override
    public String toString() {
        return path != null ? "NbtRef{path=" + path + "}" : "NbtRef{value=" + value + "}";
    }
}
