/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches what any of its children match. A JSON array that mixes a custom ingredient with a vanilla one becomes one
 * of these. A JSON object whose type is {@code forge:compound} is rejected, as it is on Forge.
 */
public class CompoundIngredient extends AbstractIngredient {

    private final List<Ingredient> children;
    private final boolean simple;
    @Nullable
    private ItemStack[] stacks;

    protected CompoundIngredient(List<Ingredient> children) {
        this.children = Collections.unmodifiableList(children);
        this.simple = children.stream().allMatch(Ingredient::isSimple);
    }

    public static Ingredient of(Ingredient... children) {
        if (children.length == 0) {
            throw new IllegalArgumentException("Cannot create a compound ingredient with no children, use Ingredient.of() to create an empty ingredient");
        }
        if (children.length == 1) {
            return children[0];
        }
        List<Ingredient> vanilla = new ArrayList<>();
        List<Ingredient> all = new ArrayList<>();
        for (Ingredient child : children) {
            if (child.getClass() == Ingredient.class) {
                vanilla.add(child);
            } else {
                all.add(child);
            }
        }
        all.addAll(vanilla);
        if (all.size() == 1) {
            return all.get(0);
        }
        return new CompoundIngredient(all);
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        if (stacks == null) {
            List<ItemStack> combined = new ArrayList<>();
            for (Ingredient child : children) {
                Collections.addAll(combined, child.getItems());
            }
            stacks = combined.toArray(ItemStack[]::new);
        }
        return stacks;
    }

    @Override
    public boolean test(@Nullable ItemStack target) {
        if (target == null) {
            return false;
        }
        for (Ingredient child : children) {
            if (child.test(target)) {
                return true;
            }
        }
        return false;
    }

    public Collection<Ingredient> getChildren() {
        return children;
    }

    @Override
    public boolean isEmpty() {
        return children.stream().allMatch(Ingredient::isEmpty);
    }

    @Override
    public boolean isSimple() {
        return simple;
    }

    @NotNull
    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @NotNull
    @Override
    public JsonElement toJson() {
        if (children.size() == 1) {
            return children.get(0).toJson();
        }
        JsonArray array = new JsonArray();
        for (Ingredient child : children) {
            array.add(child.toJson());
        }
        return array;
    }

    public static final class Serializer implements IIngredientSerializer<CompoundIngredient> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public CompoundIngredient parse(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<Ingredient> children = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                children.add(Ingredient.fromNetwork(buffer));
            }
            return new CompoundIngredient(children);
        }

        @Override
        public CompoundIngredient parse(JsonObject json) {
            throw new JsonSyntaxException("CompoundIngredient should not be directly referenced in json, just use an array of ingredients.");
        }

        @Override
        public void write(FriendlyByteBuf buffer, CompoundIngredient ingredient) {
            buffer.writeVarInt(ingredient.children.size());
            for (Ingredient child : ingredient.children) {
                child.toNetwork(buffer);
            }
        }
    }
}
