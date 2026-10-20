package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code JsonRule}: one entry of a {@code compat/buildcraft/builders} rule file. Unset fields are
 * null, so a rule only changes what it mentions; an empty {@code requiredExtractors} list makes a block free.
 */
public final class JsonRule {
    @Nullable public final List<JsonSelector> selectors;
    @Nullable public final List<RequiredExtractor> requiredExtractors;
    @Nullable public final List<BlockPos> requiredBlockOffsets;
    @Nullable public final List<String> ignoredProperties;
    @Nullable public final List<BlockPos> updateBlockOffsets;
    @Nullable public final String placeBlock;
    @Nullable public final List<String> canBeReplacedWithBlocks;
    @Nullable public final CompoundTag replaceNbt;
    /** Blocks matched by an ignoring rule are recorded as air. */
    public final boolean ignore;
    /** Entities are only recorded when a capturing rule matches them. */
    public final boolean capture;

    private JsonRule(JsonObject json) {
        selectors = list(json, "selectors", JsonSelector::fromJson);
        requiredExtractors = list(json, "requiredExtractors", element -> RequiredExtractor.fromJson(element.getAsJsonObject()));
        requiredBlockOffsets = list(json, "requiredBlockOffsets", JsonRule::blockPos);
        ignoredProperties = list(json, "ignoredProperties", JsonElement::getAsString);
        updateBlockOffsets = list(json, "updateBlockOffsets", JsonRule::blockPos);
        placeBlock = json.has("placeBlock") ? json.get("placeBlock").getAsString() : null;
        canBeReplacedWithBlocks = list(json, "canBeReplacedWithBlocks", JsonElement::getAsString);
        Tag replace = json.has("replaceNbt") ? NbtJson.fromJson(json.get("replaceNbt")) : null;
        replaceNbt = replace instanceof CompoundTag compound && replace != NbtJson.NULL ? compound : null;
        ignore = json.has("ignore") && json.get("ignore").getAsBoolean();
        capture = json.has("capture") && json.get("capture").getAsBoolean();
    }

    public static JsonRule fromJson(JsonObject json) {
        return new JsonRule(json);
    }

    @Nullable
    private static <T> List<T> list(JsonObject json, String key, Function<JsonElement, T> reader) {
        if (!json.has(key) || json.get(key).isJsonNull()) return null;
        List<T> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) values.add(reader.apply(element));
        return List.copyOf(values);
    }

    private static BlockPos blockPos(JsonElement json) {
        JsonArray array = json.getAsJsonArray();
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
}
