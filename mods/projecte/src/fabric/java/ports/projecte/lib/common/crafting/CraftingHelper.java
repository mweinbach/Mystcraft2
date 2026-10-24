/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.crafting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.common.crafting.conditions.ICondition;
import ports.projecte.lib.common.crafting.conditions.IConditionSerializer;
import ports.projecte.lib.common.extensions.IForgeIngredient;
import ports.projecte.lib.fml.ModList;
import ports.projecte.lib.registries.ForgeRegistries;

/**
 * Forge's data-file conditions and custom ingredient types, read on Fabric by the port's resource and ingredient
 * mixins.
 */
public final class CraftingHelper {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final ResourceLocation VANILLA_ITEM = new ResourceLocation("minecraft", "item");
    private static final Map<ResourceLocation, IConditionSerializer<?>> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, IIngredientSerializer<?>> INGREDIENTS = new ConcurrentHashMap<>();
    private static final Map<IIngredientSerializer<?>, ResourceLocation> INGREDIENT_IDS = new IdentityHashMap<>();

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
        register(VANILLA_ITEM, VanillaIngredientSerializer.INSTANCE);
        register(new ResourceLocation("forge", "nbt"), StrictNBTIngredient.Serializer.INSTANCE);
        register(new ResourceLocation("forge", "partial_nbt"), PartialNBTIngredient.Serializer.INSTANCE);
        register(new ResourceLocation("forge", "difference"), DifferenceIngredient.Serializer.INSTANCE);
        register(new ResourceLocation("forge", "intersection"), IntersectionIngredient.Serializer.INSTANCE);
        register(new ResourceLocation("forge", "compound"), CompoundIngredient.Serializer.INSTANCE);
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

    public static <T extends Ingredient> IIngredientSerializer<T> register(ResourceLocation key, IIngredientSerializer<T> serializer) {
        if (INGREDIENTS.putIfAbsent(key, serializer) != null) {
            throw new IllegalStateException("Duplicate recipe ingredient serializer: " + key);
        }
        if (INGREDIENT_IDS.putIfAbsent(serializer, key) != null) {
            throw new IllegalStateException("Duplicate recipe ingredient serializer: " + key + " " + serializer);
        }
        return serializer;
    }

    @Nullable
    public static ResourceLocation getID(IIngredientSerializer<?> serializer) {
        return INGREDIENT_IDS.get(serializer);
    }

    public static <T extends Ingredient> void write(FriendlyByteBuf buffer, T ingredient) {
        @SuppressWarnings("unchecked")
        IIngredientSerializer<T> serializer = (IIngredientSerializer<T>) ((IForgeIngredient) ingredient).getSerializer();
        ResourceLocation key = INGREDIENT_IDS.get(serializer);
        if (key == null) {
            throw new IllegalArgumentException("Tried to serialize unregistered Ingredient: " + ingredient + " " + serializer);
        }
        if (serializer != VanillaIngredientSerializer.INSTANCE) {
            buffer.writeVarInt(-1);
            buffer.writeResourceLocation(key);
        }
        serializer.write(buffer, ingredient);
    }

    public static boolean hasIngredient(ResourceLocation type) {
        return INGREDIENTS.containsKey(type);
    }

    public static Ingredient getIngredient(ResourceLocation type, FriendlyByteBuf buffer) {
        IIngredientSerializer<?> serializer = INGREDIENTS.get(type);
        if (serializer == null) {
            throw new IllegalArgumentException("Can not deserialize unknown Ingredient type: " + type);
        }
        return serializer.parse(buffer);
    }

    /**
     * Reads an ingredient that vanilla cannot: a JSON object whose {@code type} is a Forge ingredient type registered
     * here, or an array containing one. Returns {@code null} for everything else, including {@code minecraft:item} and
     * types this port does not know, so that other mods' readers (IngredientExtensionAPI's, for one) get them. Once
     * they have run, {@link #requireClaimedType} rejects a type nobody read.
     */
    @Nullable
    public static Ingredient getCustomIngredient(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonArray()) {
            return arrayIngredient(json.getAsJsonArray());
        }
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected ingredient to be a object or array of objects");
        }
        JsonObject object = json.getAsJsonObject();
        if (!object.has("type")) {
            return null;
        }
        String type = GsonHelper.getAsString(object, "type");
        if (type.isEmpty()) {
            throw new JsonSyntaxException("Ingredient type can not be an empty string");
        }
        ResourceLocation key = new ResourceLocation(type);
        if (VANILLA_ITEM.equals(key)) {
            return null;
        }
        IIngredientSerializer<?> serializer = INGREDIENTS.get(key);
        return serializer == null ? null : serializer.parse(object);
    }

    /**
     * Throws, as Forge does, when {@code json} names an ingredient type that neither this port nor another mod's
     * reader turned into its own ingredient: vanilla's reader ignores {@code type} and would read the object as a
     * plain item ingredient instead.
     */
    public static void requireClaimedType(@Nullable JsonElement json, Ingredient read) {
        if (json != null && json.isJsonObject() && json.getAsJsonObject().has("type") && read.getClass() == Ingredient.class) {
            String type = GsonHelper.getAsString(json.getAsJsonObject(), "type");
            if (!VANILLA_ITEM.toString().equals(type) && !VANILLA_ITEM.equals(ResourceLocation.tryParse(type))) {
                throw new JsonSyntaxException("Unknown ingredient type: " + type);
            }
        }
    }

    /**
     * Whether this port writes {@code ingredient} to the network: one of the Forge ingredient types registered here.
     * Vanilla ingredients and other mods' subclasses (IngredientExtensionAPI's, Fabric's custom ingredients) are left
     * to vanilla's writer and the injections other mods make into it.
     */
    public static boolean writesIngredient(Ingredient ingredient) {
        IForgeIngredient forge = (IForgeIngredient) ingredient;
        if (forge.projecte$isVanilla()) {
            return false;
        }
        try {
            return INGREDIENT_IDS.containsKey(forge.getSerializer());
        } catch (IllegalStateException notForge) {
            return false;
        }
    }

    @Nullable
    private static Ingredient arrayIngredient(JsonArray array) {
        if (array.isEmpty()) {
            return null;
        }
        List<Ingredient> custom = new ArrayList<>();
        List<Ingredient> vanilla = new ArrayList<>();
        for (JsonElement element : array) {
            // Ingredient.fromJson runs this port's reader again and every other mod's, so an element another mod
            // reads (IngredientExtensionAPI's, for one) comes back as that mod's own ingredient.
            Ingredient parsed = getCustomIngredient(element);
            if (parsed == null) {
                parsed = Ingredient.fromJson(element);
            }
            if (parsed.getClass() == Ingredient.class) {
                vanilla.add(parsed);
            } else {
                custom.add(parsed);
            }
        }
        if (custom.isEmpty()) {
            return null;
        }
        custom.addAll(vanilla);
        if (custom.size() == 1) {
            return custom.get(0);
        }
        return new CompoundIngredient(custom);
    }

    public static Item getItem(String itemName, boolean disallowsAir) {
        ResourceLocation itemKey = new ResourceLocation(itemName);
        if (!ForgeRegistries.ITEMS.containsKey(itemKey)) {
            throw new JsonSyntaxException("Unknown item '" + itemName + "'");
        }
        Item item = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(itemKey));
        if (disallowsAir && item == Items.AIR) {
            throw new JsonSyntaxException("Invalid item: " + itemName);
        }
        return item;
    }

    public static CompoundTag getNBT(JsonElement element) {
        try {
            if (element.isJsonObject()) {
                return TagParser.parseTag(GSON.toJson(element));
            }
            return TagParser.parseTag(GsonHelper.convertToString(element, "nbt"));
        } catch (CommandSyntaxException e) {
            throw new JsonSyntaxException("Invalid NBT Entry: " + e);
        }
    }

    public static ItemStack getItemStack(JsonObject json, boolean readNBT) {
        return getItemStack(json, readNBT, false);
    }

    public static ItemStack getItemStack(JsonObject json, boolean readNBT, boolean disallowsAir) {
        String itemName = GsonHelper.getAsString(json, "item");
        Item item = getItem(itemName, disallowsAir);
        if (readNBT && json.has("nbt")) {
            CompoundTag nbt = getNBT(json.get("nbt"));
            CompoundTag saved = new CompoundTag();
            if (nbt.contains("ForgeCaps")) {
                saved.put("ForgeCaps", nbt.get("ForgeCaps"));
                nbt.remove("ForgeCaps");
            }
            saved.put("tag", nbt);
            saved.putString("id", itemName);
            saved.putInt("Count", GsonHelper.getAsInt(json, "count", 1));
            return ItemStack.of(saved);
        }
        return new ItemStack(item, GsonHelper.getAsInt(json, "count", 1));
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
