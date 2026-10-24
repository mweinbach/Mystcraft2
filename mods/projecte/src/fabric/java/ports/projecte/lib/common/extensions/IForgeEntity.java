/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.common.capabilities.AttachedCapabilities;
import ports.projecte.lib.common.capabilities.Capability;
import ports.projecte.lib.common.capabilities.ICapabilitySerializable;
import ports.projecte.lib.common.util.ITeleporter;
import ports.projecte.lib.common.util.LazyOptional;
import ports.projecte.lib.fluids.FluidType;
import ports.projecte.lib.fluids.FluidTypes;

/**
 * Methods Forge adds to {@link Entity}, including those of its capability provider base class, injected into it on
 * Fabric. The capabilities come from {@link ports.projecte.lib.event.AttachCapabilitiesEvent} and are saved with the
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
        return ((AttachedCapabilities.Holder) this).projecte$capabilities().getCapability(cap, side);
    }

    default void projecte$invalidateCaps() {
        ((AttachedCapabilities.Holder) this).projecte$capabilities().invalidate();
    }

    default void reviveCaps() {
        ((AttachedCapabilities.Holder) this).projecte$capabilities().revive();
    }

    @Override
    default void projecte$deserializeNBT(CompoundTag nbt) {
        self().load(nbt);
    }

    @Override
    default CompoundTag projecte$serializeNBT() {
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
     * Forge tracks the height of every fluid type the entity touches. Vanilla tracks water and lava; the other types,
     * such as Mekanism's fluids, are measured here the way Forge's per-type fluid pass measures them.
     */
    default FluidType getMaxHeightFluidType() {
        double water = self().getFluidHeight(FluidTags.WATER);
        double lava = self().getFluidHeight(FluidTags.LAVA);
        FluidType tallest = water <= 0 && lava <= 0 ? FluidTypes.EMPTY : water >= lava ? FluidTypes.WATER : FluidTypes.LAVA;
        double height = Math.max(water, lava);
        for (Map.Entry<FluidType, Double> entry : moddedFluidHeights(self()).entrySet()) {
            if (entry.getValue() > height) {
                tallest = entry.getKey();
                height = entry.getValue();
            }
        }
        return tallest;
    }

    default boolean isEyeInFluidType(FluidType type) {
        if (type == FluidTypes.WATER) {
            return self().isEyeInFluid(FluidTags.WATER);
        } else if (type == FluidTypes.LAVA) {
            return self().isEyeInFluid(FluidTags.LAVA);
        } else if (type.isAir()) {
            return false;
        }
        // Vanilla's updateFluidOnEyes test: the fluid surface in the eye block is above the eye, and a boat the
        // entity rides above water hides it.
        Entity entity = self();
        double eye = entity.getEyeY() - 0.11111111F;
        if (entity.getVehicle() instanceof Boat boat && !boat.isUnderWater() && boat.getBoundingBox().maxY >= eye && boat.getBoundingBox().minY <= eye) {
            return false;
        }
        BlockPos pos = new BlockPos(entity.getX(), eye, entity.getZ());
        FluidState state = entity.level.getFluidState(pos);
        return !state.isEmpty() && state.getFluidType() == type && pos.getY() + state.getHeight(entity.level, pos) > eye;
    }

    default boolean isInFluidType(FluidType type) {
        if (type == FluidTypes.WATER) {
            return self().getFluidHeight(FluidTags.WATER) > 0;
        } else if (type == FluidTypes.LAVA) {
            return self().getFluidHeight(FluidTags.LAVA) > 0;
        }
        return moddedFluidHeights(self()).getOrDefault(type, 0D) > 0;
    }

    /**
     * The depth of each non-vanilla fluid type in the entity's bounding box, measured as vanilla's
     * {@code updateFluidHeightAndDoFluidPushing} measures water and lava.
     */
    private static Map<FluidType, Double> moddedFluidHeights(Entity entity) {
        AABB box = entity.getBoundingBox().deflate(0.001);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.ceil(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);
        Map<FluidType, Double> heights = new IdentityHashMap<>();
        if (!entity.level.hasChunksAt(minX, minY, minZ, maxX, maxY, maxZ)) {
            return heights;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState state = entity.level.getFluidState(pos);
                    if (state.isEmpty() || state.is(FluidTags.WATER) || state.is(FluidTags.LAVA)) {
                        continue;
                    }
                    double surface = y + state.getHeight(entity.level, pos);
                    if (surface >= box.minY) {
                        heights.merge(state.getFluidType(), surface - box.minY, Math::max);
                    }
                }
            }
        }
        return heights;
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
