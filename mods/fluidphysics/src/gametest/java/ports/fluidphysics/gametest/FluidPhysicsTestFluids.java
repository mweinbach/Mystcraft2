/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import ports.fluidphysics.FluidPhysics;
import ports.fluidphysics.FluidProperties;

import java.util.function.Supplier;

/**
 * The development-only test mod. It registers one fully swimmable fluid and one fluid with every motion behavior off,
 * so the library's GameTests can check both sides of each property.
 */
public final class FluidPhysicsTestFluids implements ModInitializer {

    public static final String MOD_ID = "fluidphysics";

    public static final FlowingFluid SWIMMABLE_SOURCE;
    public static final FlowingFluid SWIMMABLE_FLOWING;
    public static final LiquidBlock SWIMMABLE_BLOCK;

    public static final FlowingFluid PLAIN_SOURCE;
    public static final FlowingFluid PLAIN_FLOWING;
    public static final LiquidBlock PLAIN_BLOCK;

    private static boolean s_registered;

    static {
        final Registration swimmable = create("test_swimmable");
        final Registration plain = create("test_plain");

        SWIMMABLE_SOURCE = swimmable.source;
        SWIMMABLE_FLOWING = swimmable.flowing;
        SWIMMABLE_BLOCK = swimmable.block;

        PLAIN_SOURCE = plain.source;
        PLAIN_FLOWING = plain.flowing;
        PLAIN_BLOCK = plain.block;
    }

    @Override
    public void onInitialize() {

        if (s_registered) {
            return;
        }

        s_registered = true;

        register("test_swimmable", SWIMMABLE_SOURCE, SWIMMABLE_FLOWING, SWIMMABLE_BLOCK);
        register("test_plain", PLAIN_SOURCE, PLAIN_FLOWING, PLAIN_BLOCK);

        FluidPhysics.register(SWIMMABLE_SOURCE, SWIMMABLE_FLOWING, swimmableProperties());
        FluidPhysics.register(PLAIN_SOURCE, PLAIN_FLOWING, plainProperties());
    }

    private static void register(final String name, final FlowingFluid source, final FlowingFluid flowing,
                                 final LiquidBlock block) {

        Registry.register(Registry.FLUID, id(name), source);
        Registry.register(Registry.FLUID, id(name + "_flowing"), flowing);
        Registry.register(Registry.BLOCK, id(name), block);
    }

    private static FluidProperties swimmableProperties() {
        return FluidProperties.create()
                .canSwim(true)
                .canDrown(true)
                .canPushEntity(true)
                .supportsBoating(true)
                .fallDistanceModifier(0.4F)
                .pathType(BlockPathTypes.WATER);
    }

    private static FluidProperties plainProperties() {
        return FluidProperties.create()
                .canSwim(false)
                .canDrown(false)
                .canPushEntity(false)
                .supportsBoating(false)
                .fallDistanceModifier(0.1F)
                .pathType(BlockPathTypes.WALKABLE);
    }

    private static ResourceLocation id(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private static Registration create(final String name) {

        final Holder holder = new Holder();
        final Block.Properties blockProperties = Block.Properties.of(Material.WATER)
                .noCollission()
                .strength(100.0F)
                .noLootTable();

        holder.source = new TestFluid.Source(() -> holder.source, () -> holder.flowing, () -> holder.block);
        holder.flowing = new TestFluid.Flowing(() -> holder.source, () -> holder.flowing, () -> holder.block);
        holder.block = new LiquidBlock(holder.source, blockProperties);
        return new Registration(holder);
    }

    /** A minimal still/flowing pair, modeled on vanilla's water flow constants. */
    private abstract static class TestFluid extends FlowingFluid {

        private final Supplier<? extends FlowingFluid> source;
        private final Supplier<? extends FlowingFluid> flowing;
        private final Supplier<? extends LiquidBlock> block;

        TestFluid(final Supplier<? extends FlowingFluid> source, final Supplier<? extends FlowingFluid> flowing,
                  final Supplier<? extends LiquidBlock> block) {

            this.source = source;
            this.flowing = flowing;
            this.block = block;
        }

        @Override
        public Fluid getFlowing() {
            return this.flowing.get();
        }

        @Override
        public Fluid getSource() {
            return this.source.get();
        }

        @Override
        public Item getBucket() {
            return Items.AIR;
        }

        @Override
        public boolean isSame(final Fluid fluid) {
            return fluid == this.source.get() || fluid == this.flowing.get();
        }

        @Override
        protected boolean canConvertToSource() {
            return false;
        }

        @Override
        protected void beforeDestroyingBlock(final LevelAccessor level, final net.minecraft.core.BlockPos position,
                                             final BlockState state) {

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
        protected boolean canBeReplacedWith(final FluidState state, final BlockGetter level,
                                            final net.minecraft.core.BlockPos position, final Fluid fluid,
                                            final Direction direction) {
            return Direction.DOWN == direction && !this.isSame(fluid);
        }

        @Override
        protected BlockState createLegacyBlock(final FluidState state) {
            return this.block.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
        }

        static final class Source extends TestFluid {

            Source(final Supplier<? extends FlowingFluid> source, final Supplier<? extends FlowingFluid> flowing,
                   final Supplier<? extends LiquidBlock> block) {
                super(source, flowing, block);
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

        static final class Flowing extends TestFluid {

            Flowing(final Supplier<? extends FlowingFluid> source, final Supplier<? extends FlowingFluid> flowing,
                    final Supplier<? extends LiquidBlock> block) {

                super(source, flowing, block);
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
    }

    private static final class Holder {
        FlowingFluid source;
        FlowingFluid flowing;
        LiquidBlock block;
    }

    private static final class Registration {

        final FlowingFluid source;
        final FlowingFluid flowing;
        final LiquidBlock block;

        Registration(final Holder holder) {
            this.source = holder.source;
            this.flowing = holder.flowing;
            this.block = holder.block;
        }
    }
}
