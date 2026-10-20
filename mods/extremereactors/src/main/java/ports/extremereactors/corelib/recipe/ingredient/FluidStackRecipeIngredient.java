/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.ingredient;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.extremereactors.corelib.fluid.FluidStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A fluid ingredient: a single fluid or a fluid tag, plus a required amount in mB.
 * <p>
 * JSON: {@code {"fluid": {"fluid": "ns:id", "count": N}}} or {@code {"fluid": {"tag": "ns:tag", "count": N}}}.
 * The inner object is also accepted on its own.
 */
public final class FluidStackRecipeIngredient
        implements IRecipeIngredient<FluidStack> {

    public static FluidStackRecipeIngredient from(final Fluid fluid, final int amount) {
        return new FluidStackRecipeIngredient(fluid, null, amount);
    }

    public static FluidStackRecipeIngredient from(final TagKey<Fluid> tag, final int amount) {
        return new FluidStackRecipeIngredient(null, tag, amount);
    }

    public static FluidStackRecipeIngredient from(final JsonElement json) {

        JsonObject object = GsonHelper.convertToJsonObject(json, "fluid ingredient");

        if (object.has(JSON_FLUID) && object.get(JSON_FLUID).isJsonObject()) {
            object = object.getAsJsonObject(JSON_FLUID);
        }

        final int amount = GsonHelper.getAsInt(object, JSON_COUNT);

        if (amount < 1) {
            throw new JsonSyntaxException("Invalid fluid ingredient amount: " + amount);
        }

        if (object.has(JSON_TAG)) {
            return from(TagKey.create(Registry.FLUID_REGISTRY,
                    new ResourceLocation(GsonHelper.getAsString(object, JSON_TAG))), amount);
        }

        final ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(object, JSON_FLUID));
        final Fluid fluid = Registry.FLUID.getOptional(id)
                .orElseThrow(() -> new JsonSyntaxException("Unknown fluid: " + id));

        if (Fluids.EMPTY == fluid) {
            throw new JsonSyntaxException("A fluid ingredient cannot be empty");
        }

        return from(fluid, amount);
    }

    public static FluidStackRecipeIngredient from(final FriendlyByteBuf buffer) {

        final boolean isTag = buffer.readBoolean();
        final ResourceLocation id = buffer.readResourceLocation();
        final int amount = buffer.readVarInt();

        return isTag ? from(TagKey.create(Registry.FLUID_REGISTRY, id), amount) : from(Registry.FLUID.get(id), amount);
    }

    //region IRecipeIngredient

    @Override
    public int getAmount() {
        return this._amount;
    }

    @Override
    public boolean test(final FluidStack stack) {
        return this.testIgnoreAmount(stack) && stack.getAmount() >= this._amount;
    }

    @Override
    public boolean testIgnoreAmount(final FluidStack stack) {

        if (stack.isEmpty()) {
            return false;
        }

        final Fluid fluid = stack.getFluid();

        if (null != this._tag) {
            return fluid.is(this._tag);
        }

        return null != this._fluid && (this._fluid == fluid || this._fluid.isSame(fluid));
    }

    @Override
    public FluidStack getMatchFrom(final FluidStack stack) {
        return this.test(stack) ? new FluidStack(stack, this._amount) : FluidStack.EMPTY;
    }

    @Override
    public List<FluidStack> getMatchingElements() {

        if (null != this._fluid) {
            return List.of(new FluidStack(this._fluid, this._amount));
        }

        final ImmutableList.Builder<FluidStack> list = ImmutableList.builder();

        for (final Holder<Fluid> holder : Registry.FLUID.getTagOrEmpty(this._tag)) {

            final Fluid fluid = holder.value();

            if (fluid.isSource(fluid.defaultFluidState())) {
                list.add(new FluidStack(fluid, this._amount));
            }
        }

        return list.build();
    }

    @Override
    public boolean isEmpty() {
        return null == this._tag && (null == this._fluid || Fluids.EMPTY == this._fluid);
    }

    @Override
    public JsonElement toJson() {

        final JsonObject inner = new JsonObject();

        if (null != this._tag) {
            inner.addProperty(JSON_TAG, this._tag.location().toString());
        } else {
            inner.addProperty(JSON_FLUID, Registry.FLUID.getKey(this._fluid).toString());
        }

        inner.addProperty(JSON_COUNT, this._amount);

        final JsonObject json = new JsonObject();

        json.add(JSON_FLUID, inner);
        return json;
    }

    @Override
    public void toNetwork(final FriendlyByteBuf buffer) {

        buffer.writeBoolean(null != this._tag);
        buffer.writeResourceLocation(null != this._tag ? this._tag.location() : Registry.FLUID.getKey(this._fluid));
        buffer.writeVarInt(this._amount);
    }

    //endregion
    //region internals

    private FluidStackRecipeIngredient(@Nullable final Fluid fluid, @Nullable final TagKey<Fluid> tag, final int amount) {

        this._fluid = fluid;
        this._tag = tag;
        this._amount = Math.max(1, amount);
    }

    private static final String JSON_FLUID = "fluid";
    private static final String JSON_TAG = "tag";
    private static final String JSON_COUNT = "count";

    @Nullable
    private final Fluid _fluid;
    @Nullable
    private final TagKey<Fluid> _tag;
    private final int _amount;

    //endregion
}
