/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.extremereactors.platform.Platform;

/** A mutable amount of a fluid, in millibuckets, with optional extra NBT. */
public final class FluidStack {

    public static final FluidStack EMPTY = new FluidStack(Fluids.EMPTY, 0);

    public FluidStack(final Fluid fluid, final int amount) {
        this(fluid, amount, null);
    }

    public FluidStack(final Fluid fluid, final int amount, @Nullable final CompoundTag tag) {

        this._fluid = Objects.requireNonNull(fluid);
        this._amount = amount;
        this._tag = null == tag ? null : tag.copy();
    }

    public FluidStack(final FluidStack other, final int amount) {
        this(other.getFluid(), amount, other._tag);
    }

    public static FluidStack loadFluidStackFromNBT(@Nullable final CompoundTag data) {

        if (null == data || !data.contains("FluidName")) {
            return EMPTY;
        }

        final ResourceLocation id = ResourceLocation.tryParse(data.getString("FluidName"));
        final Fluid fluid = null == id ? Fluids.EMPTY : Registry.FLUID.get(id);

        if (Fluids.EMPTY == fluid) {
            return EMPTY;
        }

        final FluidStack stack = new FluidStack(fluid, data.getInt("Amount"), data.contains("Tag") ? data.getCompound("Tag") : null);

        return stack.isEmpty() ? EMPTY : stack;
    }

    public static FluidStack readFromPacket(final FriendlyByteBuf buffer) {

        final Fluid fluid = Registry.FLUID.byId(buffer.readVarInt());
        final int amount = buffer.readVarInt();
        final CompoundTag tag = buffer.readNbt();

        return Fluids.EMPTY == fluid || amount <= 0 ? EMPTY : new FluidStack(fluid, amount, tag);
    }

    public CompoundTag writeToNBT(final CompoundTag data) {

        data.putString("FluidName", Registry.FLUID.getKey(this.getFluid()).toString());
        data.putInt("Amount", this._amount);

        if (null != this._tag) {
            data.put("Tag", this._tag);
        }

        return data;
    }

    public void writeToPacket(final FriendlyByteBuf buffer) {

        buffer.writeVarInt(Registry.FLUID.getId(this.getFluid()));
        buffer.writeVarInt(this.isEmpty() ? 0 : this._amount);
        buffer.writeNbt(this._tag);
    }

    //region contents

    /** The fluid, or {@link Fluids#EMPTY} if the stack is empty. */
    public Fluid getFluid() {
        return this.isEmpty() ? Fluids.EMPTY : this._fluid;
    }

    /** The fluid, even if the amount is zero. */
    public Fluid getRawFluid() {
        return this._fluid;
    }

    public boolean isEmpty() {
        return this._amount <= 0 || Fluids.EMPTY == this._fluid;
    }

    public int getAmount() {
        return this.isEmpty() ? 0 : this._amount;
    }

    public void setAmount(final int amount) {

        if (this != EMPTY) {
            this._amount = amount;
        }
    }

    public void grow(final int amount) {
        this.setAmount(this._amount + amount);
    }

    public void shrink(final int amount) {
        this.setAmount(this._amount - amount);
    }

    public boolean hasTag() {
        return null != this._tag;
    }

    @Nullable
    public CompoundTag getTag() {
        return this._tag;
    }

    public void setTag(@Nullable final CompoundTag tag) {

        if (this != EMPTY) {
            this._tag = tag;
        }
    }

    public CompoundTag getOrCreateTag() {

        if (null == this._tag) {
            this.setTag(new CompoundTag());
        }

        return null == this._tag ? new CompoundTag() : this._tag;
    }

    public FluidStack copy() {
        return this.isEmpty() ? EMPTY : new FluidStack(this._fluid, this._amount, this._tag);
    }

    public Component getDisplayName() {
        return this.isEmpty() ? Component.translatable("block.minecraft.air") : Platform.backend().fluidName(this._fluid);
    }

    //endregion
    //region comparison

    /** Same fluid and same NBT; amounts are ignored. */
    public boolean isFluidEqual(final FluidStack other) {
        return this.getFluid().isSame(other.getFluid()) && areFluidStackTagsEqual(this, other);
    }

    /** Same fluid, same NBT and same amount. */
    public boolean isFluidStackIdentical(final FluidStack other) {
        return this.isFluidEqual(other) && this.getAmount() == other.getAmount();
    }

    /** Same fluid and NBT, and at least as much as {@code other}. */
    public boolean containsFluid(final FluidStack other) {
        return this.isFluidEqual(other) && this.getAmount() >= other.getAmount();
    }

    public static boolean areFluidStackTagsEqual(final FluidStack a, final FluidStack b) {
        return Objects.equals(a._tag, b._tag);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof FluidStack stack && this.isFluidEqual(stack));
    }

    @Override
    public int hashCode() {
        return 31 * this.getFluid().hashCode() + (null == this._tag ? 0 : this._tag.hashCode());
    }

    @Override
    public String toString() {
        return this.isEmpty() ? "empty" : this.getAmount() + " mB of " + Registry.FLUID.getKey(this._fluid);
    }

    //endregion

    private final Fluid _fluid;
    private int _amount;
    @Nullable
    private CompoundTag _tag;
}
