/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches what {@code base} matches unless {@code subtracted} also matches ({@code forge:difference}).
 */
public class DifferenceIngredient extends AbstractIngredient {

    private final Ingredient base;
    private final Ingredient subtracted;
    @Nullable
    private ItemStack[] stacks;

    protected DifferenceIngredient(Ingredient base, Ingredient subtracted) {
        this.base = base;
        this.subtracted = subtracted;
    }

    public static DifferenceIngredient of(Ingredient base, Ingredient subtracted) {
        return new DifferenceIngredient(base, subtracted);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && base.test(stack) && !subtracted.test(stack);
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        if (stacks == null) {
            stacks = Arrays.stream(base.getItems()).filter(stack -> !subtracted.test(stack)).toArray(ItemStack[]::new);
        }
        return stacks;
    }

    @Override
    public boolean isEmpty() {
        return base.isEmpty();
    }

    @Override
    public boolean projecte$isSimple() {
        return base.projecte$isSimple() && subtracted.projecte$isSimple();
    }

    @NotNull
    @Override
    public IIngredientSerializer<DifferenceIngredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @NotNull
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", CraftingHelper.getID(Serializer.INSTANCE).toString());
        json.add("base", base.toJson());
        json.add("subtracted", subtracted.toJson());
        return json;
    }

    public static final class Serializer implements IIngredientSerializer<DifferenceIngredient> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public DifferenceIngredient parse(JsonObject json) {
            return new DifferenceIngredient(Ingredient.fromJson(json.get("base")), Ingredient.fromJson(json.get("subtracted")));
        }

        @Override
        public DifferenceIngredient parse(FriendlyByteBuf buffer) {
            return new DifferenceIngredient(Ingredient.fromNetwork(buffer), Ingredient.fromNetwork(buffer));
        }

        @Override
        public void write(FriendlyByteBuf buffer, DifferenceIngredient ingredient) {
            ingredient.base.toNetwork(buffer);
            ingredient.subtracted.toNetwork(buffer);
        }
    }
}
