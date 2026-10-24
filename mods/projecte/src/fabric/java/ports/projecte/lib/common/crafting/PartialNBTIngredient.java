/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.advancements.critereon.NbtPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.registries.ForgeRegistries;

/**
 * Matches any of the given items when the stack's tag contains the required tags ({@code forge:partial_nbt}). Extra
 * tags still match. ProjectE's own recipes do not use this type; datapacks can.
 */
public class PartialNBTIngredient extends AbstractIngredient {

    private final Set<Item> items;
    private final CompoundTag nbt;
    private final NbtPredicate predicate;

    protected PartialNBTIngredient(Set<Item> items, CompoundTag nbt) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a PartialNBTIngredient with no items");
        }
        this.items = Collections.unmodifiableSet(items);
        this.nbt = nbt;
        this.predicate = new NbtPredicate(nbt);
    }

    public static PartialNBTIngredient of(ItemLike item, CompoundTag nbt) {
        return new PartialNBTIngredient(Set.of(item.asItem()), nbt);
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null) {
            return false;
        }
        return items.contains(input.getItem()) && predicate.matches(input.getTag());
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        ItemStack[] stacks = new ItemStack[items.size()];
        int index = 0;
        for (Item item : items) {
            ItemStack stack = new ItemStack(item);
            stack.setTag(nbt.copy());
            stacks[index++] = stack;
        }
        return stacks;
    }

    @NotNull
    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @NotNull
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", CraftingHelper.getID(Serializer.INSTANCE).toString());
        if (items.size() == 1) {
            json.addProperty("item", ForgeRegistries.ITEMS.getKey(items.iterator().next()).toString());
        } else {
            JsonArray array = new JsonArray();
            items.stream().map(ForgeRegistries.ITEMS::getKey).sorted().forEach(name -> array.add(name.toString()));
            json.add("items", array);
        }
        json.addProperty("nbt", nbt.toString());
        return json;
    }

    public static final class Serializer implements IIngredientSerializer<PartialNBTIngredient> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public PartialNBTIngredient parse(JsonObject json) {
            Set<Item> items = new LinkedHashSet<>();
            if (json.has("item")) {
                items.add(CraftingHelper.getItem(GsonHelper.getAsString(json, "item"), true));
            } else if (json.has("items")) {
                JsonArray itemArray = GsonHelper.getAsJsonArray(json, "items");
                for (int i = 0; i < itemArray.size(); i++) {
                    items.add(CraftingHelper.getItem(GsonHelper.convertToString(itemArray.get(i), "items[" + i + ']'), true));
                }
            } else {
                throw new JsonSyntaxException("Must set either 'item' or 'items'");
            }
            if (!json.has("nbt")) {
                throw new JsonSyntaxException("Missing nbt, expected to find a String or JsonObject");
            }
            return new PartialNBTIngredient(items, CraftingHelper.getNBT(json.get("nbt")));
        }

        @Override
        public PartialNBTIngredient parse(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            Set<Item> items = new LinkedHashSet<>();
            for (int i = 0; i < count; i++) {
                ResourceLocation name = buffer.readResourceLocation();
                Item item = ForgeRegistries.ITEMS.getValue(name);
                if (item == null || !ForgeRegistries.ITEMS.containsKey(name)) {
                    throw new IllegalArgumentException("Unknown item in partial NBT ingredient: " + name);
                }
                items.add(item);
            }
            return new PartialNBTIngredient(items, Objects.requireNonNull(buffer.readNbt(), "Partial NBT ingredient is missing its tag"));
        }

        @Override
        public void write(FriendlyByteBuf buffer, PartialNBTIngredient ingredient) {
            buffer.writeVarInt(ingredient.items.size());
            for (Item item : ingredient.items) {
                buffer.writeResourceLocation(Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item), () -> "Unregistered item " + item));
            }
            buffer.writeNbt(ingredient.nbt);
        }
    }
}
