package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code JsonSelector}: a block or entity id, optionally with block state properties as
 * {@code "minecraft:lever[face=wall, facing=north]"}, and NBT conditions that must all hold.
 */
public final class JsonSelector {
    public final String base;
    private final List<Expression> expressions;

    private JsonSelector(String base, List<Expression> expressions) {
        this.base = base;
        this.expressions = expressions;
    }

    public static JsonSelector fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) return new JsonSelector(json.getAsString(), List.of());
        JsonObject object = json.getAsJsonObject();
        List<Expression> expressions = new ArrayList<>();
        if (object.has("nbt")) {
            for (JsonElement element : object.getAsJsonArray("nbt")) {
                JsonObject expression = element.getAsJsonObject();
                expressions.add(new Expression(NbtPath.fromJson(expression.get("key")),
                        NbtCompareOperation.byName(expression.get("operation").getAsString()),
                        NbtJson.fromJson(expression.get("value"))));
            }
        }
        return new JsonSelector(object.get("base").getAsString(), List.copyOf(expressions));
    }

    public boolean matches(Predicate<String> basePredicate, CompoundTag nbt) {
        return basePredicate.test(base)
                && expressions.stream().allMatch(expression -> expression.operation.compare(expression.key.get(nbt), expression.value));
    }

    private record Expression(NbtPath key, NbtCompareOperation operation, Tag value) {}
}
