/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.crafting.conditions.ICondition;
import ports.mekanism.lib.common.crafting.conditions.IConditionSerializer;
import ports.mekanism.lib.fml.ModList;

/**
 * Forge's data-file conditions and custom ingredient types, read on Fabric by the port's resource and ingredient
 * mixins.
 */
public final class CraftingHelper {

    private static final Map<ResourceLocation, IConditionSerializer<?>> CONDITIONS = new ConcurrentHashMap<>();

    static {
        register(simple("true", json -> new Simple("true", context -> true)));
        register(simple("false", json -> new Simple("false", context -> false)));
        register(simple("mod_loaded", json -> {
            String modid = GsonHelper.getAsString(json, "modid");
            return new Simple("mod_loaded", context -> ModList.get().isLoaded(modid));
        }));
        register(simple("item_exists", json -> {
            ResourceLocation item = new ResourceLocation(GsonHelper.getAsString(json, "item"));
            return new Simple("item_exists", context -> Registry.ITEM.containsKey(item));
        }));
        register(simple("tag_empty", json -> {
            TagKey<Item> tag = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(GsonHelper.getAsString(json, "tag")));
            return new Simple("tag_empty", context -> context.getTag(tag).isEmpty());
        }));
        register(simple("not", json -> {
            ICondition value = getCondition(GsonHelper.getAsJsonObject(json, "value"));
            return new Simple("not", context -> !value.test(context));
        }));
        register(simple("and", json -> {
            List<ICondition> values = conditions(GsonHelper.getAsJsonArray(json, "values"));
            return new Simple("and", context -> values.stream().allMatch(condition -> condition.test(context)));
        }));
        register(simple("or", json -> {
            List<ICondition> values = conditions(GsonHelper.getAsJsonArray(json, "values"));
            return new Simple("or", context -> values.stream().anyMatch(condition -> condition.test(context)));
        }));
    }

    private CraftingHelper() {
    }

    public static IConditionSerializer<?> register(IConditionSerializer<?> serializer) {
        ResourceLocation key = serializer.getID();
        if (CONDITIONS.putIfAbsent(key, serializer) != null) {
            throw new IllegalStateException("Duplicate recipe condition serializer: " + key);
        }
        return serializer;
    }

    @SuppressWarnings("unchecked")
    public static <T extends ICondition> JsonObject serialize(T condition) {
        IConditionSerializer<T> serializer = (IConditionSerializer<T>) CONDITIONS.get(condition.getID());
        if (serializer == null) {
            throw new JsonSyntaxException("Unknown condition type: " + condition.getID());
        }
        return serializer.getJson(condition);
    }

    public static JsonArray serialize(ICondition... conditions) {
        JsonArray array = new JsonArray();
        for (ICondition condition : conditions) {
            array.add(serialize(condition));
        }
        return array;
    }

    public static boolean isConditionKnown(ResourceLocation type) {
        return CONDITIONS.containsKey(type);
    }

    public static ICondition getCondition(JsonObject json) {
        ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(json, "type"));
        IConditionSerializer<?> serializer = CONDITIONS.get(type);
        if (serializer == null) {
            throw new JsonSyntaxException("Unknown condition type: " + type);
        }
        return serializer.read(json);
    }

    public static boolean processConditions(JsonObject json, String memberName, ICondition.IContext context) {
        return !json.has(memberName) || processConditions(GsonHelper.getAsJsonArray(json, memberName), context);
    }

    public static boolean processConditions(JsonArray conditions, ICondition.IContext context) {
        for (ICondition condition : conditions(conditions)) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }

    private static List<ICondition> conditions(JsonArray array) {
        List<ICondition> conditions = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new JsonSyntaxException("Conditions must be an array of JsonObjects");
            }
            conditions.add(getCondition(element.getAsJsonObject()));
        }
        return conditions;
    }

    /**
     * Reads an ingredient that vanilla cannot: a JSON object whose {@code type} is a Forge ingredient type, or an array
     * containing one. Returns {@code null} for anything vanilla's reader handles.
     */
    @Nullable
    public static Ingredient getCustomIngredient(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            boolean custom = false;
            for (JsonElement element : array) {
                custom |= getCustomIngredient(element) != null;
            }
            if (!custom) {
                return null;
            }
            List<Ingredient> children = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                children.add(Ingredient.fromJson(element));
            }
            return CompoundIngredient.of(children.toArray(Ingredient[]::new));
        }
        if (!json.isJsonObject() || !json.getAsJsonObject().has("type")) {
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        String type = GsonHelper.getAsString(object, "type");
        return switch (type) {
            case "forge:nbt" -> StrictNBTIngredient.of(getItemStack(object, true));
            case "forge:difference" -> DifferenceIngredient.of(Ingredient.fromJson(object.get("base")), Ingredient.fromJson(object.get("subtracted")));
            case "forge:compound" -> CompoundIngredient.of(children(GsonHelper.getAsJsonArray(object, "children")));
            default -> null;
        };
    }

    private static Ingredient[] children(JsonArray children) {
        Ingredient[] ingredients = new Ingredient[children.size()];
        for (int i = 0; i < ingredients.length; i++) {
            ingredients[i] = Ingredient.fromJson(children.get(i));
        }
        return ingredients;
    }

    public static ItemStack getItemStack(JsonObject json, boolean readNBT) {
        ResourceLocation itemName = new ResourceLocation(GsonHelper.getAsString(json, "item"));
        Item item = Registry.ITEM.getOptional(itemName).orElseThrow(() -> new JsonSyntaxException("Unknown item '" + itemName + "'"));
        ItemStack stack = new ItemStack(item, GsonHelper.getAsInt(json, "count", 1));
        if (readNBT && json.has("nbt")) {
            JsonElement element = json.get("nbt");
            try {
                CompoundTag tag = element.isJsonObject() ? TagParser.parseTag(element.toString()) : TagParser.parseTag(GsonHelper.convertToString(element, "nbt"));
                stack.setTag(tag);
            } catch (CommandSyntaxException e) {
                throw new JsonSyntaxException("Invalid NBT Entry: " + e);
            }
        }
        return stack;
    }

    private static IConditionSerializer<Simple> simple(String name, Function<JsonObject, Simple> reader) {
        ResourceLocation id = new ResourceLocation("forge", name);
        return new IConditionSerializer<>() {
            @Override
            public void write(JsonObject json, Simple value) {
                throw new JsonParseException("Forge's built-in conditions are only read on Fabric");
            }

            @Override
            public Simple read(JsonObject json) {
                return reader.apply(json);
            }

            @Override
            public ResourceLocation getID() {
                return id;
            }
        };
    }

    private record Simple(String name, Predicate<ICondition.IContext> test) implements ICondition {

        @Override
        public ResourceLocation getID() {
            return new ResourceLocation("forge", name);
        }

        @Override
        public boolean test(IContext context) {
            return test.test(context);
        }
    }
}
