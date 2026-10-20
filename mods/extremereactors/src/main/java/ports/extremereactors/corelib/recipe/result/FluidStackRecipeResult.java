/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.result;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Registry;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.extremereactors.corelib.fluid.FluidStack;

/**
 * A fluid result. JSON: {@code {"fluid": "ns:id", "count": N, "nbt": {...}}}, with the amount in mB.
 */
public final class FluidStackRecipeResult
        implements IRecipeResult<FluidStack> {

    public static FluidStackRecipeResult from(final FluidStack stack) {
        return new FluidStackRecipeResult(stack.copy());
    }

    public static FluidStackRecipeResult from(final Fluid fluid, final int amount) {
        return new FluidStackRecipeResult(new FluidStack(fluid, amount));
    }

    public static FluidStackRecipeResult from(final JsonElement json) {

        final JsonObject object = GsonHelper.convertToJsonObject(json, "fluid result");
        final ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(object, JSON_FLUID));
        final Fluid fluid = Registry.FLUID.getOptional(id).orElseThrow(() -> new JsonSyntaxException("Unknown fluid: " + id));
        final int amount = GsonHelper.getAsInt(object, JSON_COUNT);

        if (Fluids.EMPTY == fluid || amount < 1) {
            throw new JsonSyntaxException("A fluid result cannot be empty");
        }

        final FluidStack stack = new FluidStack(fluid, amount);

        if (object.has(JSON_NBT)) {

            final JsonElement nbt = object.get(JSON_NBT);

            try {
                stack.setTag(TagParser.parseTag(nbt.isJsonObject() ? nbt.toString() : GsonHelper.convertToString(nbt, JSON_NBT)));
            } catch (final CommandSyntaxException ex) {
                throw new JsonSyntaxException("Invalid result NBT: " + ex.getMessage());
            }
        }

        return new FluidStackRecipeResult(stack);
    }

    public static FluidStackRecipeResult from(final FriendlyByteBuf buffer) {
        return new FluidStackRecipeResult(FluidStack.readFromPacket(buffer));
    }

    //region IRecipeResult

    @Override
    public FluidStack getResult() {
        return this._result.copy();
    }

    @Override
    public long getAmount() {
        return this._result.getAmount();
    }

    @Override
    public boolean isEmpty() {
        return this._result.isEmpty();
    }

    @Override
    public JsonElement toJson() {

        final JsonObject json = new JsonObject();

        json.addProperty(JSON_FLUID, Registry.FLUID.getKey(this._result.getFluid()).toString());
        json.addProperty(JSON_COUNT, this._result.getAmount());

        if (this._result.hasTag()) {
            json.addProperty(JSON_NBT, this._result.getTag().toString());
        }

        return json;
    }

    @Override
    public void toNetwork(final FriendlyByteBuf buffer) {
        this._result.writeToPacket(buffer);
    }

    //endregion
    //region internals

    private FluidStackRecipeResult(final FluidStack result) {
        this._result = result;
    }

    private static final String JSON_FLUID = "fluid";
    private static final String JSON_COUNT = "count";
    private static final String JSON_NBT = "nbt";

    private final FluidStack _result;

    //endregion
}
