/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fluids;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.SoundActions;
import ports.mekanism.lib.registries.ForgeRegistries;
import ports.mekanism.lib.registries.RegistryManager;
import ports.mekanism.lib.registries.SimpleForgeRegistry;

/**
 * Fabric home of Forge's {@code forge:fluid_type} registry and of the {@code Fluid -> FluidType} mapping Forge patches
 * into {@link Fluid}. Fluids registered by other Fabric mods get a type built from their
 * {@link FluidVariantAttributes}; the result is cached per fluid.
 */
public final class FluidTypes {

    public static final SimpleForgeRegistry<FluidType> REGISTRY = new SimpleForgeRegistry<>(ForgeRegistries.Keys.FLUID_TYPES,
          new ResourceLocation("empty"), null);

    public static final FluidType EMPTY = new FluidType(FluidType.Properties.create()
          .descriptionId("block.minecraft.air")
          .motionScale(1)
          .canPushEntity(false)
          .canSwim(false)
          .canDrown(false)
          .fallDistanceModifier(1F)
          .pathType(null)
          .adjacentPathType(null)
          .density(0)
          .temperature(0)
          .viscosity(0));
    public static final FluidType WATER = new FluidType(FluidType.Properties.create()
          .descriptionId("block.minecraft.water")
          .fallDistanceModifier(0F)
          .canExtinguish(true)
          .canConvertToSource(true)
          .supportsBoating(true)
          .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
          .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
          .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
          .canHydrate(true));
    public static final FluidType LAVA = new FluidType(FluidType.Properties.create()
          .descriptionId("block.minecraft.lava")
          .canSwim(false)
          .canDrown(false)
          .pathType(BlockPathTypes.LAVA)
          .adjacentPathType(null)
          .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
          .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)
          .lightLevel(15)
          .density(3000)
          .viscosity(6000)
          .temperature(1300));

    private static final Map<Fluid, FluidType> DERIVED = new ConcurrentHashMap<>();

    static {
        REGISTRY.register(new ResourceLocation("empty"), EMPTY);
        REGISTRY.register(new ResourceLocation("water"), WATER);
        REGISTRY.register(new ResourceLocation("lava"), LAVA);
        RegistryManager.ACTIVE.add(REGISTRY);
    }

    private FluidTypes() {
    }

    /**
     * The type of a fluid that does not define its own, which is every fluid not built on {@link ForgeFlowingFluid}.
     */
    public static FluidType of(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return EMPTY;
        } else if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return WATER;
        } else if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return LAVA;
        }
        return DERIVED.computeIfAbsent(fluid, FluidTypes::derive);
    }

    private static FluidType derive(Fluid fluid) {
        FluidVariant variant = FluidVariant.of(fluid);
        FluidType.Properties properties = FluidType.Properties.create()
              .descriptionId(fluid.defaultFluidState().createLegacyBlock().getBlock().getDescriptionId())
              .lightLevel(FluidVariantAttributes.getLuminance(variant))
              .temperature(FluidVariantAttributes.getTemperature(variant))
              .viscosity(FluidVariantAttributes.getViscosity(variant, null) * 1000 / FluidConstants.WATER_VISCOSITY)
              .density(FluidVariantAttributes.isLighterThanAir(variant) ? -1000 : 1000)
              .rarity(Rarity.COMMON)
              .sound(SoundActions.BUCKET_FILL, FluidVariantAttributes.getFillSound(variant))
              .sound(SoundActions.BUCKET_EMPTY, FluidVariantAttributes.getEmptySound(variant));
        return new FluidType(properties) {
            @Override
            public Component getDescription(FluidStack stack) {
                return FluidVariantAttributes.getName(FluidVariant.of(stack.getFluid(), stack.getTag()));
            }
        };
    }

    @Nullable
    static ResourceLocation registryName(FluidType type) {
        ResourceLocation name = REGISTRY.getKey(type);
        if (name != null && (type == EMPTY || !name.equals(REGISTRY.getDefaultKey()))) {
            return name;
        }
        for (Map.Entry<Fluid, FluidType> entry : DERIVED.entrySet()) {
            if (entry.getValue() == type) {
                return Registry.FLUID.getKey(entry.getKey());
            }
        }
        return null;
    }
}
