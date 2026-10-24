package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code NbtPath}: compound keys and list or array indices, as a JSON list of strings. Paths that
 * lead nowhere give {@link NbtJson#NULL}.
 */
public final class NbtPath {
    private final List<String> elements;

    private NbtPath(List<String> elements) {
        this.elements = elements;
    }

    public static NbtPath fromJson(JsonElement json) {
        List<String> elements = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray()) elements.add(element.getAsString());
        return new NbtPath(List.copyOf(elements));
    }

    public Tag get(@Nullable Tag tag) {
        if (tag == null) return NbtJson.NULL;
        if (elements.isEmpty()) return tag;
        if (tag instanceof CompoundTag compound && tag != NbtJson.NULL) {
            String key = elements.get(0);
            return compound.contains(key) ? rest().get(compound.get(key)) : NbtJson.NULL;
        }
        if (elements.size() == 1 && tag instanceof ByteArrayTag bytes) {
            int index = index();
            return index >= 0 && index < bytes.getAsByteArray().length ? ByteTag.valueOf(bytes.getAsByteArray()[index]) : NbtJson.NULL;
        }
        if (elements.size() == 1 && tag instanceof IntArrayTag ints) {
            int index = index();
            return index >= 0 && index < ints.getAsIntArray().length ? IntTag.valueOf(ints.getAsIntArray()[index]) : NbtJson.NULL;
        }
        if (elements.size() == 1 && tag instanceof ListTag list) {
            int index = index();
            return index >= 0 && index < list.size() ? rest().get(list.get(index)) : NbtJson.NULL;
        }
        return NbtJson.NULL;
    }

    private NbtPath rest() {
        return new NbtPath(elements.subList(1, elements.size()));
    }

    private int index() {
        try {
            return Integer.parseInt(elements.get(0));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The first key of the path, or null for an empty path. */
    @Nullable
    public String first() {
        return elements.isEmpty() ? null : elements.get(0);
    }

    @Override
    public String toString() {
        return "NbtPath{" + elements + "}";
    }
}
