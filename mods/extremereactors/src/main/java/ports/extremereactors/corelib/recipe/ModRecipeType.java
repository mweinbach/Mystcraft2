/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A recipe type with lookups against the recipes currently loaded. The type still has to be registered in the
 * recipe type registry by its owner.
 */
public final class ModRecipeType<R extends ModRecipe>
        implements RecipeType<R> {

    public static <R extends ModRecipe> ModRecipeType<R> create(final ResourceLocation id) {
        return new ModRecipeType<>(id);
    }

    public ResourceLocation getId() {
        return this._id;
    }

    /** The first loaded recipe of this type that matches the filter, in the recipe manager's order. */
    public Optional<R> findFirst(final Predicate<R> filter) {
        return this.getRecipes().stream().filter(filter).findFirst();
    }

    /**
     * Every loaded recipe of this type. The server's recipe manager is used when a server is running in this
     * process, otherwise the copy the client received; an empty list is returned when neither is available.
     */
    public List<R> getRecipes() {

        final RecipeManager manager = currentRecipeManager();

        return null == manager ? List.of() : manager.getAllRecipesFor(this);
    }

    @Override
    public String toString() {
        return this._id.toString();
    }

    //region internals

    private ModRecipeType(final ResourceLocation id) {
        this._id = id;
    }

    @Nullable
    private static RecipeManager currentRecipeManager() {

        final MinecraftServer server = Platform.currentServer();

        return null != server ? server.getRecipeManager() : Platform.backend().clientRecipeManager();
    }

    private final ResourceLocation _id;

    //endregion
}
