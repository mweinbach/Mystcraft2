/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fluids;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.platform.Platform;

/**
 * A fluid, an amount in millibuckets and an optional tag. The NBT and packet layouts are the ones Forge uses, so
 * Mekanism's saved data reads the same on both loaders.
 */
public class FluidStack {

    public static final FluidStack EMPTY = new FluidStack(Fluids.EMPTY, 0);

    public static final Codec<FluidStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
          ResourceLocation.CODEC.fieldOf("FluidName").forGetter(stack -> Registry.FLUID.getKey(stack.getFluid())),
          Codec.INT.fieldOf("Amount").forGetter(FluidStack::getAmount),
          CompoundTag.CODEC.optionalFieldOf("Tag").forGetter(stack -> Optional.ofNullable(stack.getTag()))
    ).apply(instance, (name, amount, tag) -> {
        FluidStack stack = new FluidStack(Registry.FLUID.get(name), amount);
        tag.ifPresent(stack::setTag);
        return stack;
    }));

    private final Fluid fluid;
    private int amount;
    @Nullable
    private CompoundTag tag;
    private boolean isEmpty;

    public FluidStack(Fluid fluid, int amount) {
        this.fluid = fluid == null ? Fluids.EMPTY : fluid;
        this.amount = amount;
        updateEmpty();
    }

    public FluidStack(Fluid fluid, int amount, @Nullable CompoundTag nbt) {
        this(fluid, amount);
        if (nbt != null) {
            tag = nbt.copy();
        }
    }

    public FluidStack(FluidStack stack, int amount) {
        this(stack.getFluid(), amount, stack.tag);
    }

    public static FluidStack loadFluidStackFromNBT(@Nullable CompoundTag nbt) {
        if (nbt == null || !nbt.contains("FluidName", 8)) {
            return EMPTY;
        }
        ResourceLocation name = ResourceLocation.tryParse(nbt.getString("FluidName"));
        if (name == null || !Registry.FLUID.containsKey(name)) {
            return EMPTY;
        }
        FluidStack stack = new FluidStack(Registry.FLUID.get(name), nbt.getInt("Amount"));
        if (nbt.contains("Tag", 10)) {
            stack.tag = nbt.getCompound("Tag");
        }
        return stack;
    }

    public CompoundTag writeToNBT(CompoundTag nbt) {
        nbt.putString("FluidName", Registry.FLUID.getKey(getFluid()).toString());
        nbt.putInt("Amount", amount);
        if (tag != null) {
            nbt.put("Tag", tag);
        }
        return nbt;
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        if (isEmpty()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeId(Registry.FLUID, getFluid());
            buf.writeVarInt(getAmount());
            buf.writeNbt(tag);
        }
    }

    public static FluidStack readFromPacket(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return EMPTY;
        }
        Fluid fluid = buf.readById(Registry.FLUID);
        int amount = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        if (fluid == null || fluid == Fluids.EMPTY) {
            return EMPTY;
        }
        return new FluidStack(fluid, amount, tag);
    }

    @NotNull
    public final Fluid getFluid() {
        return isEmpty ? Fluids.EMPTY : fluid;
    }

    @NotNull
    public final Fluid getRawFluid() {
        return fluid;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    protected void updateEmpty() {
        isEmpty = getRawFluid() == Fluids.EMPTY || amount <= 0;
    }

    public int getAmount() {
        return isEmpty ? 0 : amount;
    }

    public void setAmount(int amount) {
        if (fluid == Fluids.EMPTY) {
            throw new IllegalStateException("Can't modify the empty stack.");
        }
        this.amount = amount;
        updateEmpty();
    }

    public void grow(int amount) {
        setAmount(this.amount + amount);
    }

    public void shrink(int amount) {
        setAmount(this.amount - amount);
    }

    public boolean hasTag() {
        return tag != null;
    }

    @Nullable
    public CompoundTag getTag() {
        return tag;
    }

    public void setTag(@Nullable CompoundTag tag) {
        if (fluid == Fluids.EMPTY) {
            throw new IllegalStateException("Can't modify the empty stack.");
        }
        this.tag = tag;
    }

    public CompoundTag getOrCreateTag() {
        if (tag == null) {
            setTag(new CompoundTag());
        }
        return tag;
    }

    @Nullable
    public CompoundTag getChildTag(String childName) {
        return tag == null ? null : tag.getCompound(childName);
    }

    public CompoundTag getOrCreateChildTag(String childName) {
        getOrCreateTag();
        CompoundTag child = tag.getCompound(childName);
        if (!tag.contains(childName, 10)) {
            tag.put(childName, child);
        }
        return child;
    }

    public void removeChildTag(String childName) {
        if (tag != null) {
            tag.remove(childName);
        }
    }

    public Component getDisplayName() {
        return Platform.fluidName(this);
    }

    public String getTranslationKey() {
        return Platform.fluidTranslationKey(this);
    }

    public FluidStack copy() {
        return new FluidStack(getFluid(), amount, tag);
    }

    public boolean isFluidEqual(@NotNull FluidStack other) {
        return getFluid() == other.getFluid() && isFluidStackTagEqual(other);
    }

    private boolean isFluidStackTagEqual(FluidStack other) {
        return tag == null ? other.tag == null : other.tag != null && tag.equals(other.tag);
    }

    public static boolean areFluidStackTagsEqual(@NotNull FluidStack stack1, @NotNull FluidStack stack2) {
        return stack1.isFluidStackTagEqual(stack2);
    }

    public boolean containsFluid(@NotNull FluidStack other) {
        return isFluidEqual(other) && amount >= other.amount;
    }

    public boolean isFluidStackIdentical(FluidStack other) {
        return isFluidEqual(other) && amount == other.amount;
    }

    public boolean isFluidEqual(@NotNull net.minecraft.world.item.ItemStack other) {
        return ports.mekanism.lib.fluids.FluidUtil.getFluidContained(other).map(this::isFluidEqual).orElse(false);
    }

    @Override
    public final int hashCode() {
        int code = 1;
        code = 31 * code + getFluid().hashCode();
        if (tag != null) {
            code = 31 * code + tag.hashCode();
        }
        return code;
    }

    /**
     * Same fluid and tag; the amount is ignored, as on Forge.
     */
    @Override
    public final boolean equals(Object o) {
        return o instanceof FluidStack other && isFluidEqual(other);
    }

    @Override
    public String toString() {
        return getAmount() + " " + Objects.toString(Registry.FLUID.getKey(getFluid()));
    }
}
