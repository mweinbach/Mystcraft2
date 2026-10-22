/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.capabilities.AttachedCapabilities;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.capabilities.ICapabilitySerializable;
import ports.mekanism.lib.common.util.ITeleporter;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.fluids.FluidType;
import ports.mekanism.lib.fluids.FluidTypes;

/**
 * Methods Forge adds to {@link Entity}, including those of its capability provider base class, injected into it on
 * Fabric. The capabilities come from {@link ports.mekanism.lib.event.AttachCapabilitiesEvent} and are saved with the
 * entity. Mixins invalidate them from {@link Entity#remove}. {@link #onRemovedFromWorld} runs when the entity leaves
 * the level's tracking lists, including a chunk unload, which is where Forge calls it.
 */
public interface IForgeEntity extends ICapabilitySerializable<CompoundTag> {

    private Entity self() {
        return (Entity) this;
    }

    @NotNull
    @Override
    default <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return ((AttachedCapabilities.Holder) this).mekanism$capabilities().getCapability(cap, side);
    }

    default void invalidateCaps() {
        ((AttachedCapabilities.Holder) this).mekanism$capabilities().invalidate();
    }

    default void reviveCaps() {
        ((AttachedCapabilities.Holder) this).mekanism$capabilities().revive();
    }

    @Override
    default void deserializeNBT(CompoundTag nbt) {
        self().load(nbt);
    }

    @Override
    default CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        String id = self().getEncodeId();
        if (id != null) {
            tag.putString(Entity.ID_TAG, id);
        }
        return self().saveWithoutId(tag);
    }

    default ItemStack getPickedResult(HitResult target) {
        ItemStack result = self().getPickResult();
        if (result == null) {
            SpawnEggItem egg = SpawnEggItem.byId(self().getType());
            return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
        }
        return result;
    }

    default void onRemovedFromWorld() {
    }

    /**
     * Forge tracks the height of every fluid type the entity touches; vanilla tracks water and lava, the only fluids
     * with types on Fabric that entities can be in.
     */
    default FluidType getMaxHeightFluidType() {
        double water = self().getFluidHeight(FluidTags.WATER);
        double lava = self().getFluidHeight(FluidTags.LAVA);
        if (water <= 0 && lava <= 0) {
            return FluidTypes.EMPTY;
        }
        return water >= lava ? FluidTypes.WATER : FluidTypes.LAVA;
    }

    default boolean isEyeInFluidType(FluidType type) {
        if (type == FluidTypes.WATER) {
            return self().isEyeInFluid(FluidTags.WATER);
        } else if (type == FluidTypes.LAVA) {
            return self().isEyeInFluid(FluidTags.LAVA);
        }
        return false;
    }

    default boolean isInFluidType(FluidType type) {
        if (type == FluidTypes.WATER) {
            return self().getFluidHeight(FluidTags.WATER) > 0;
        } else if (type == FluidTypes.LAVA) {
            return self().getFluidHeight(FluidTags.LAVA) > 0;
        }
        return false;
    }

    /**
     * Forge calls {@code placeEntity} with a function that moves the entity, and builds a nether portal when that
     * function is given {@code true}. Fabric's dimension API has no portal builder, so the flag is ignored and
     * {@link FabricDimensions#teleport} runs only when {@code placeEntity} asks to move. {@code playTeleportSound}
     * is not consulted, so a player can still hear the vanilla portal travel sound.
     */
    @Nullable
    default Entity changeDimension(ServerLevel destination, ITeleporter teleporter) {
        Entity entity = self();
        if (!(entity.level instanceof ServerLevel current) || entity.isRemoved()) {
            return null;
        }
        if (current.dimension() != destination.dimension() && !entity.canChangeDimensions()) {
            return null;
        }
        PortalInfo target = teleporter.getPortalInfo(entity, destination, entity::findDimensionEntryPoint);
        if (target == null) {
            return null;
        }
        float yaw = entity.getYRot();
        return teleporter.placeEntity(entity, current, destination, yaw, spawnPortal -> FabricDimensions.teleport(entity, destination, target));
    }
}
