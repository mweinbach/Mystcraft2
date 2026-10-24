/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches a stack only when every child matches ({@code forge:intersection}). Mekanism's recipes do not use it;
 * datapacks can.
 */
public class IntersectionIngredient extends AbstractIngredient {

    private final List<Ingredient> children;
    private final boolean simple;
    @Nullable
    private ItemStack[] stacks;

    protected IntersectionIngredient(List<Ingredient> children) {
        if (children.size() < 2) {
            throw new IllegalArgumentException("Cannot create an IntersectionIngredient with one or no children");
        }
        this.children = Collections.unmodifiableList(children);
        this.simple = children.stream().allMatch(Ingredient::projecte$isSimple);
    }

    public static Ingredient of(Ingredient... ingredients) {
        if (ingredients.length == 0) {
            throw new IllegalArgumentException("Cannot create an IntersectionIngredient with no children, use Ingredient.of() to create an empty ingredient");
        }
        if (ingredients.length == 1) {
            return ingredients[0];
        }
        return new IntersectionIngredient(Arrays.asList(ingredients));
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Ingredient child : children) {
            if (!child.test(stack)) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        if (stacks == null) {
            stacks = Arrays.stream(children.get(0).getItems()).filter(stack -> {
                for (int i = 1; i < children.size(); i++) {
                    if (!children.get(i).test(stack)) {
                        return false;
                    }
                }
                return true;
            }).toArray(ItemStack[]::new);
        }
        return stacks;
    }

    @Override
    public boolean isEmpty() {
        return children.stream().anyMatch(Ingredient::isEmpty);
    }

    @Override
    public boolean projecte$isSimple() {
        return simple;
    }

    @NotNull
    @Override
    public IIngredientSerializer<IntersectionIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @NotNull
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", CraftingHelper.getID(Serializer.INSTANCE).toString());
        JsonArray array = new JsonArray();
        for (Ingredient child : children) {
            array.add(child.toJson());
        }
        json.add("children", array);
        return json;
    }

    public static final class Serializer implements IIngredientSerializer<IntersectionIngredient> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public IntersectionIngredient parse(JsonObject json) {
            JsonArray children = GsonHelper.getAsJsonArray(json, "children");
            if (children.size() < 2) {
                throw new JsonSyntaxException("Must have at least two children for an intersection ingredient");
            }
            List<Ingredient> parsed = new ArrayList<>(children.size());
            for (JsonElement child : children) {
                parsed.add(Ingredient.fromJson(child));
            }
            return new IntersectionIngredient(parsed);
        }

        @Override
        public IntersectionIngredient parse(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<Ingredient> children = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                children.add(Ingredient.fromNetwork(buffer));
            }
            return new IntersectionIngredient(children);
        }

        @Override
        public void write(FriendlyByteBuf buffer, IntersectionIngredient ingredient) {
            buffer.writeVarInt(ingredient.children.size());
            for (Ingredient child : ingredient.children) {
                child.toNetwork(buffer);
            }
        }
    }
}
