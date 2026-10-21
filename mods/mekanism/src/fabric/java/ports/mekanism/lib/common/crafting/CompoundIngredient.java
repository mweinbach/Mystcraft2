/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches what any of its children match; JSON arrays that contain a custom ingredient become one of these.
 */
public class CompoundIngredient extends AbstractIngredient {

    private final List<Ingredient> children;
    @Nullable
    private ItemStack[] stacks;

    protected CompoundIngredient(List<? extends Ingredient> children) {
        this.children = Collections.unmodifiableList(children);
    }

    public static Ingredient of(Ingredient... children) {
        if (children.length == 1) {
            return children[0];
        }
        List<Ingredient> flattened = new ArrayList<>();
        for (Ingredient child : children) {
            if (child instanceof CompoundIngredient compound) {
                flattened.addAll(compound.children);
            } else {
                flattened.add(child);
            }
        }
        return new CompoundIngredient(flattened);
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        if (stacks == null) {
            stacks = children.stream().flatMap(child -> Arrays.stream(child.getItems())).toArray(ItemStack[]::new);
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

    @NotNull
    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (Ingredient child : children) {
            array.add(child.toJson());
        }
        return array;
    }
}
