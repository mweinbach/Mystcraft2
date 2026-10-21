/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fluids;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.extensions.common.IClientFluidTypeExtensions;
import ports.mekanism.lib.common.SoundActions;

/**
 * The shared physical properties of a still fluid and its flowing form. Fluids from vanilla and other Fabric mods get
 * a type derived from their transfer API attributes, see {@link FluidTypes}.
 */
public class FluidType {

    public static final int BUCKET_VOLUME = 1000;

    private String descriptionId;
    private final double motionScale;
    private final boolean canPushEntity;
    private final boolean canSwim;
    private final boolean canDrown;
    private final float fallDistanceModifier;
    private final boolean canExtinguish;
    private final boolean canConvertToSource;
    private final boolean supportsBoating;
    @Nullable
    private final BlockPathTypes pathType;
    @Nullable
    private final BlockPathTypes adjacentPathType;
    private final Map<SoundActions, SoundEvent> sounds;
    private final boolean canHydrate;
    private final int lightLevel;
    private final int density;
    private final int temperature;
    private final int viscosity;
    private final Rarity rarity;
    @Nullable
    private Object renderProperties;

    public FluidType(final Properties properties) {
        this.descriptionId = properties.descriptionId;
        this.motionScale = properties.motionScale;
        this.canPushEntity = properties.canPushEntity;
        this.canSwim = properties.canSwim;
        this.canDrown = properties.canDrown;
        this.fallDistanceModifier = properties.fallDistanceModifier;
        this.canExtinguish = properties.canExtinguish;
        this.canConvertToSource = properties.canConvertToSource;
        this.supportsBoating = properties.supportsBoating;
        this.pathType = properties.pathType;
        this.adjacentPathType = properties.adjacentPathType;
        this.sounds = Map.copyOf(properties.sounds);
        this.canHydrate = properties.canHydrate;
        this.lightLevel = properties.lightLevel;
        this.density = properties.density;
        this.temperature = properties.temperature;
        this.viscosity = properties.viscosity;
        this.rarity = properties.rarity;
    }

    public Component getDescription() {
        return Component.translatable(getDescriptionId());
    }

    public String getDescriptionId() {
        if (descriptionId == null) {
            descriptionId = Util.makeDescriptionId("fluid_type", FluidTypes.registryName(this));
        }
        return descriptionId;
    }

    public int getLightLevel() {
        return lightLevel;
    }

    public int getDensity() {
        return density;
    }

    public int getTemperature() {
        return temperature;
    }

    public int getViscosity() {
        return viscosity;
    }

    public Rarity getRarity() {
        return rarity;
    }

    @Nullable
    public SoundEvent getSound(SoundActions action) {
        return sounds.get(action);
    }

    public double motionScale(Entity entity) {
        return motionScale;
    }

    public boolean canPushEntity(Entity entity) {
        return canPushEntity;
    }

    public boolean canSwim(Entity entity) {
        return canSwim;
    }

    public float getFallDistanceModifier(Entity entity) {
        return fallDistanceModifier;
    }

    public boolean canExtinguish(Entity entity) {
        return canExtinguish;
    }

    public boolean canDrownIn(LivingEntity entity) {
        return canDrown;
    }

    public boolean supportsBoating(Entity boat) {
        return supportsBoating;
    }

    public boolean canHydrate(Entity entity) {
        return canHydrate;
    }

    @Nullable
    public SoundEvent getSound(Entity entity, SoundActions action) {
        return getSound(action);
    }

    public boolean canExtinguish(FluidState state, BlockGetter getter, BlockPos pos) {
        return canExtinguish;
    }

    public boolean canConvertToSource(FluidState state, BlockGetter getter, BlockPos pos) {
        return canConvertToSource;
    }

    @Nullable
    public BlockPathTypes getBlockPathType(FluidState state, BlockGetter level, BlockPos pos, @Nullable Entity mob, boolean canFluidLog) {
        return canFluidLog ? null : pathType;
    }

    @Nullable
    public BlockPathTypes getAdjacentBlockPathType(FluidState state, BlockGetter level, BlockPos pos, @Nullable Entity mob, BlockPathTypes originalType) {
        return adjacentPathType;
    }

    @Nullable
    public SoundEvent getSound(@Nullable Player player, BlockGetter getter, BlockPos pos, SoundActions action) {
        return getSound(action);
    }

    public boolean canHydrate(FluidState state, BlockGetter getter, BlockPos pos, BlockState source, BlockPos sourcePos) {
        return canHydrate;
    }

    public int getLightLevel(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getLightLevel();
    }

    public int getDensity(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getDensity();
    }

    public int getTemperature(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getTemperature();
    }

    public int getViscosity(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return getViscosity();
    }

    public boolean canConvertToSource(FluidStack stack) {
        return canConvertToSource;
    }

    @Nullable
    public SoundEvent getSound(FluidStack stack, SoundActions action) {
        return getSound(action);
    }

    public Component getDescription(FluidStack stack) {
        return Component.translatable(getDescriptionId(stack));
    }

    public String getDescriptionId(FluidStack stack) {
        return getDescriptionId();
    }

    public boolean canHydrate(FluidStack stack) {
        return canHydrate;
    }

    public int getLightLevel(FluidStack stack) {
        return getLightLevel();
    }

    public int getDensity(FluidStack stack) {
        return getDensity();
    }

    public int getTemperature(FluidStack stack) {
        return getTemperature();
    }

    public int getViscosity(FluidStack stack) {
        return getViscosity();
    }

    public Rarity getRarity(FluidStack stack) {
        return getRarity();
    }

    public final boolean isAir() {
        return this == FluidTypes.EMPTY;
    }

    public final boolean isVanilla() {
        return this == FluidTypes.EMPTY || this == FluidTypes.WATER || this == FluidTypes.LAVA;
    }

    public ItemStack getBucket(FluidStack stack) {
        return new ItemStack(stack.getFluid().getBucket());
    }

    public BlockState getBlockForFluidState(BlockAndTintGetter getter, BlockPos pos, FluidState state) {
        return state.createLegacyBlock();
    }

    public FluidState getStateForPlacement(BlockAndTintGetter getter, BlockPos pos, FluidStack stack) {
        return stack.getFluid().defaultFluidState();
    }

    public final boolean canBePlacedInLevel(BlockAndTintGetter getter, BlockPos pos, FluidState state) {
        return !getBlockForFluidState(getter, pos, state).is(Blocks.AIR);
    }

    public final boolean canBePlacedInLevel(BlockAndTintGetter getter, BlockPos pos, FluidStack stack) {
        return canBePlacedInLevel(getter, pos, getStateForPlacement(getter, pos, stack));
    }

    public final boolean isLighterThanAir() {
        return density <= 0;
    }

    public boolean isVaporizedOnPlacement(Level level, BlockPos pos, FluidStack stack) {
        return this == FluidTypes.WATER && level.dimensionType().ultraWarm();
    }

    public void onVaporize(@Nullable Player player, Level level, BlockPos pos, FluidStack stack) {
        SoundEvent sound = getSound(player, level, pos, SoundActions.FLUID_VAPORIZE);
        level.playSound(player, pos, sound == null ? SoundEvents.FIRE_EXTINGUISH : sound, SoundSource.BLOCKS, 0.5F,
              2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
        for (int l = 0; l < 8; ++l) {
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + Math.random(), pos.getY() + Math.random(),
                  pos.getZ() + Math.random(), 0, 0, 0);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(FluidTypes.registryName(this));
    }

    @Nullable
    public Object getRenderPropertiesInternal() {
        return renderProperties;
    }

    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
    }

    /**
     * Runs {@link #initializeClient} once on the client, the way Forge does from the constructor.
     */
    public final void initializeClientOnce() {
        if (renderProperties == null) {
            initializeClient(properties -> {
                if (properties == this) {
                    throw new IllegalStateException("Don't extend IClientFluidTypeExtensions in your fluid type, use an anonymous class instead.");
                }
                renderProperties = properties;
            });
            if (renderProperties == null) {
                renderProperties = IClientFluidTypeExtensions.DEFAULT;
            }
        }
    }

    public static final class Properties {

        private String descriptionId;
        private double motionScale = 0.014D;
        private boolean canPushEntity = true;
        private boolean canSwim = true;
        private boolean canDrown;
        private float fallDistanceModifier = 0.5F;
        private boolean canExtinguish;
        private boolean canConvertToSource;
        private boolean supportsBoating;
        @Nullable
        private BlockPathTypes pathType = BlockPathTypes.WATER;
        @Nullable
        private BlockPathTypes adjacentPathType = BlockPathTypes.WATER_BORDER;
        private final Map<SoundActions, SoundEvent> sounds = new HashMap<>();
        private boolean canHydrate;
        private int lightLevel;
        private int density = 1000;
        private int temperature = 300;
        private int viscosity = 1000;
        private Rarity rarity = Rarity.COMMON;

        private Properties() {
        }

        public static Properties create() {
            return new Properties();
        }

        public Properties descriptionId(String descriptionId) {
            this.descriptionId = descriptionId;
            return this;
        }

        public Properties motionScale(double motionScale) {
            this.motionScale = motionScale;
            return this;
        }

        public Properties canPushEntity(boolean canPushEntity) {
            this.canPushEntity = canPushEntity;
            return this;
        }

        public Properties canSwim(boolean canSwim) {
            this.canSwim = canSwim;
            return this;
        }

        public Properties canDrown(boolean canDrown) {
            this.canDrown = canDrown;
            return this;
        }

        public Properties fallDistanceModifier(float fallDistanceModifier) {
            this.fallDistanceModifier = fallDistanceModifier;
            return this;
        }

        public Properties canExtinguish(boolean canExtinguish) {
            this.canExtinguish = canExtinguish;
            return this;
        }

        public Properties canConvertToSource(boolean canConvertToSource) {
            this.canConvertToSource = canConvertToSource;
            return this;
        }

        public Properties supportsBoating(boolean supportsBoating) {
            this.supportsBoating = supportsBoating;
            return this;
        }

        public Properties pathType(@Nullable BlockPathTypes pathType) {
            this.pathType = pathType;
            return this;
        }

        public Properties adjacentPathType(@Nullable BlockPathTypes adjacentPathType) {
            this.adjacentPathType = adjacentPathType;
            return this;
        }

        public Properties sound(SoundActions action, @Nullable SoundEvent sound) {
            if (sound != null) {
                sounds.put(action, sound);
            }
            return this;
        }

        public Properties canHydrate(boolean canHydrate) {
            this.canHydrate = canHydrate;
            return this;
        }

        public Properties lightLevel(int lightLevel) {
            this.lightLevel = lightLevel;
            return this;
        }

        public Properties density(int density) {
            this.density = density;
            return this;
        }

        public Properties temperature(int temperature) {
            this.temperature = temperature;
            return this;
        }

        public Properties viscosity(int viscosity) {
            this.viscosity = viscosity;
            return this;
        }

        public Properties rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }
    }
}
