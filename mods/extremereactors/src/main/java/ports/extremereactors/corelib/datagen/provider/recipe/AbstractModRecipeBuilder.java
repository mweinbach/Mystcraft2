/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.datagen.provider.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared part of the machine recipe builders: writes the recipe JSON for data generation (or tests).
 */
public abstract class AbstractModRecipeBuilder<Builder extends AbstractModRecipeBuilder<Builder>> {

    /** Emit the recipe with the given id. */
    public void build(final Consumer<FinishedRecipe> consumer, final ResourceLocation id) {

        final JsonObject data = new JsonObject();

        this.serializeRecipeData(data);
        consumer.accept(new Finished(id, this._serializerId, data));
    }

    /** The complete recipe JSON, including its type. */
    public JsonObject toJson() {

        final JsonObject json = new JsonObject();

        json.addProperty("type", this._serializerId.toString());
        this.serializeRecipeData(json);
        return json;
    }

    //region internals

    protected AbstractModRecipeBuilder(final ResourceLocation serializerId) {
        this._serializerId = Objects.requireNonNull(serializerId);
    }

    protected abstract void serializeRecipeData(JsonObject json);

    private record Finished(ResourceLocation id, ResourceLocation serializerId, JsonObject data)
            implements FinishedRecipe {

        @Override
        public void serializeRecipeData(final JsonObject json) {
            this.data.entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue().deepCopy()));
        }

        @Override
        public ResourceLocation getId() {
            return this.id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return Registry.RECIPE_SERIALIZER.getOptional(this.serializerId)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe serializer " + this.serializerId));
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }

    private final ResourceLocation _serializerId;

    //endregion
}
