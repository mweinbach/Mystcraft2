package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code JsonUtil}'s NBT adapters and {@code NBTUtilBC}'s {@code NBT_NULL} and {@code merge}: how
 * rule files turn JSON into NBT, compare it and patch block entity and entity data with it.
 */
public final class NbtJson {
    /** JSON {@code null}. Paths that lead nowhere return it, and merging it into a compound removes the key. */
    public static final CompoundTag NULL = new CompoundTag();

    private NbtJson() {}

    /** Integral numbers become longs and others doubles, booleans bytes, arrays lists and objects compounds. */
    public static Tag fromJson(JsonElement json) {
        if (json.isJsonNull()) return NULL;
        if (json instanceof JsonPrimitive primitive) {
            if (primitive.isBoolean()) return ByteTag.valueOf(primitive.getAsBoolean());
            if (primitive.isNumber()) {
                String text = primitive.getAsString();
                if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) return LongTag.valueOf(primitive.getAsLong());
                return DoubleTag.valueOf(primitive.getAsDouble());
            }
            return StringTag.valueOf(primitive.getAsString());
        }
        if (json instanceof JsonArray array) {
            ListTag list = new ListTag();
            for (JsonElement element : array) list.add(fromJson(element));
            return list;
        }
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) compound.put(entry.getKey(), fromJson(entry.getValue()));
        return compound;
    }

    public static JsonElement toJson(Tag tag) {
        if (tag == NULL) return JsonNull.INSTANCE;
        if (tag instanceof ByteTag b) return new JsonPrimitive(b.getAsByte());
        if (tag instanceof ShortTag s) return new JsonPrimitive(s.getAsShort());
        if (tag instanceof IntTag i) return new JsonPrimitive(i.getAsInt());
        if (tag instanceof LongTag l) return new JsonPrimitive(l.getAsLong());
        if (tag instanceof FloatTag f) return new JsonPrimitive(f.getAsFloat());
        if (tag instanceof DoubleTag d) return new JsonPrimitive(d.getAsDouble());
        if (tag instanceof StringTag s) return new JsonPrimitive(s.getAsString());
        JsonArray array = new JsonArray();
        if (tag instanceof ByteArrayTag bytes) {
            for (byte b : bytes.getAsByteArray()) array.add(b);
            return array;
        }
        if (tag instanceof IntArrayTag ints) {
            for (int i : ints.getAsIntArray()) array.add(i);
            return array;
        }
        if (tag instanceof LongArrayTag longs) {
            for (long l : longs.getAsLongArray()) array.add(l);
            return array;
        }
        if (tag instanceof ListTag list) {
            for (Tag element : list) array.add(toJson(element));
            return array;
        }
        JsonObject object = new JsonObject();
        CompoundTag compound = (CompoundTag) tag;
        for (String key : compound.getAllKeys()) object.add(key, toJson(compound.get(key)));
        return object;
    }

    /** Upstream {@code NBTUtilBC.merge}: compounds merge key by key, a {@link #NULL} source value deletes, anything else replaces. */
    @Nullable
    public static Tag merge(@Nullable Tag destination, @Nullable Tag source) {
        if (source == null) return null;
        if (destination == null) return source;
        if (destination instanceof CompoundTag dest && source instanceof CompoundTag src && src != NULL) {
            CompoundTag result = new CompoundTag();
            Set<String> keys = new HashSet<>(dest.getAllKeys());
            keys.addAll(src.getAllKeys());
            for (String key : keys) {
                if (!src.contains(key)) {
                    result.put(key, dest.get(key));
                } else if (src.get(key) != NULL) {
                    result.put(key, dest.contains(key) ? merge(dest.get(key), src.get(key)) : src.get(key));
                }
            }
            return result;
        }
        return source;
    }
}
