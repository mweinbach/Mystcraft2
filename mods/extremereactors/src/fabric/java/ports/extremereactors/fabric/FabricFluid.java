/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import ports.extremereactors.platform.FluidDefinition;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Our fluids on Fabric. They flow like Forge's default {@code ForgeFlowingFluid}, which the Forge variant uses, so
 * both loaders spread them the same way: no infinite sources, four-block slope search, one level lost per block and
 * a five tick delay.
 */
abstract class FabricFluid extends FlowingFluid {

    static FlowingFluid create(final FluidDefinition definition, final boolean source,
                               final Supplier<? extends FlowingFluid> sourceFluid,
                               final Supplier<? extends FlowingFluid> flowingFluid,
                               final Supplier<? extends Item> bucket, final Supplier<? extends LiquidBlock> block) {
        return source ? new Source(definition, sourceFluid, flowingFluid, bucket, block) :
                new Flowing(definition, sourceFluid, flowingFluid, bucket, block);
    }

    FabricFluid(final FluidDefinition definition, final Supplier<? extends FlowingFluid> sourceFluid,
                final Supplier<? extends FlowingFluid> flowingFluid, final Supplier<? extends Item> bucket,
                final Supplier<? extends LiquidBlock> block) {

        this._definition = definition;
        this._source = sourceFluid;
        this._flowing = flowingFluid;
        this._bucket = bucket;
        this._block = block;
    }

    FluidDefinition getDefinition() {
        return this._definition;
    }

    @Override
    public Fluid getFlowing() {
        return this._flowing.get();
    }

    @Override
    public Fluid getSource() {
        return this._source.get();
    }

    @Override
    public Item getBucket() {
        return this._bucket.get();
    }

    @Override
    public boolean isSame(final Fluid fluid) {
        return fluid == this._source.get() || fluid == this._flowing.get();
    }

    /**
     * Filling a bucket plays the sound the Forge variant's fluid type declares for {@code BUCKET_FILL}.
     */
    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    @Override
    protected boolean canConvertToSource() {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(final LevelAccessor level, final BlockPos position, final BlockState state) {

        final BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(position) : null;

        Block.dropResources(state, level, position, blockEntity);
    }

    @Override
    protected int getSlopeFindDistance(final LevelReader level) {
        return 4;
    }

    @Override
    protected int getDropOff(final LevelReader level) {
        return 1;
    }

    @Override
    public int getTickDelay(final LevelReader level) {
        return 5;
    }

    @Override
    protected float getExplosionResistance() {
        return 1.0f;
    }

    @Override
    protected boolean canBeReplacedWith(final FluidState state, final BlockGetter level, final BlockPos position,
                                        final Fluid fluid, final Direction direction) {
        return Direction.DOWN == direction && !this.isSame(fluid);
    }

    @Override
    protected BlockState createLegacyBlock(final FluidState state) {
        return this._block.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    static final class Source extends FabricFluid {

        Source(final FluidDefinition definition, final Supplier<? extends FlowingFluid> sourceFluid,
               final Supplier<? extends FlowingFluid> flowingFluid, final Supplier<? extends Item> bucket,
               final Supplier<? extends LiquidBlock> block) {
            super(definition, sourceFluid, flowingFluid, bucket, block);
        }

        @Override
        public boolean isSource(final FluidState state) {
            return true;
        }

        @Override
        public int getAmount(final FluidState state) {
            return 8;
        }
    }

    static final class Flowing extends FabricFluid {

        Flowing(final FluidDefinition definition, final Supplier<? extends FlowingFluid> sourceFluid,
                final Supplier<? extends FlowingFluid> flowingFluid, final Supplier<? extends Item> bucket,
                final Supplier<? extends LiquidBlock> block) {

            super(definition, sourceFluid, flowingFluid, bucket, block);
            this.registerDefaultState(this.getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(final StateDefinition.Builder<Fluid, FluidState> builder) {

            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(final FluidState state) {
            return false;
        }

        @Override
        public int getAmount(final FluidState state) {
            return state.getValue(LEVEL);
        }
    }

    /**
     * The Transfer API attributes of a fluid definition.
     */
    record Attributes(FluidDefinition definition) implements FluidVariantAttributeHandler {

        @Override
        public Component getName(final FluidVariant fluidVariant) {
            return Component.translatable(this.definition.descriptionId());
        }

        @Override
        public int getLuminance(final FluidVariant variant) {
            return this.definition.lightLevel();
        }

        @Override
        public int getTemperature(final FluidVariant variant) {
            return this.definition.temperature();
        }

        @Override
        public int getViscosity(final FluidVariant variant, final Level world) {
            return this.definition.viscosity();
        }

        @Override
        public boolean isLighterThanAir(final FluidVariant variant) {
            return this.definition.lighterThanAir();
        }
    }

    private final FluidDefinition _definition;
    private final Supplier<? extends FlowingFluid> _source;
    private final Supplier<? extends FlowingFluid> _flowing;
    private final Supplier<? extends Item> _bucket;
    private final Supplier<? extends LiquidBlock> _block;
}
