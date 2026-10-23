package net.mystcraft.gametest;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.InkRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;
import net.mystcraft.minecraft.writing.WritingDeskBlock;
import net.mystcraft.minecraft.writing.WritingDeskBlockEntity;

/**
 * Gameplay and automation surfaces that each loader wires natively: ink vial and bucket item fluid handlers, the
 * writing-desk inkwell, the lectern inventory, black ink's entity physics and the exploration biome tag. The bodies
 * are shared; Forge reaches them through capabilities and {@code FluidType}, Fabric through the Transfer API and
 * the bundled Fluid Physics library.
 */
public final class InkGameplayTests {
    /** A 7x4x7 template whose bottom layer is stone. GameTest structures start one block above the structure block, so the floor is at relative y=1 and blocks placed at y=2 stand on it. */
    public static final String FLOOR = "gametest/floor";
    public static final TagKey<Biome> HAS_EXPLORATION = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation("mystcraft", "has_exploration"));

    /** {@code ForgeInkFluidType}'s values; Fabric's registration must report exactly the same. */
    public static final InkPhysics FORGE_INK_FLUID_TYPE = new InkPhysics(true, true, true, true, 0.014D, 0.5F, false,
            BlockPathTypes.WATER, BlockPathTypes.WATER_BORDER);

    private InkGameplayTests() {}

    public record InkPhysics(boolean canSwim, boolean canDrown, boolean supportsBoating, boolean canPushEntity,
                             double motionScale, float fallDistanceModifier, boolean canExtinguish,
                             BlockPathTypes pathType, BlockPathTypes adjacentPathType) {}

    /** The amount moved, in millibuckets, and the container stack afterwards. */
    public record Exchange(int amount, ItemStack container) {}

    /** One loader's native transfer surfaces, in millibuckets. Simulated operations must not change state. */
    public interface Transfer {
        /** Drains a copy of one container; null when the stack exposes no fluid handler. */
        Exchange drainItem(ItemStack container, int maximum, boolean execute);
        /** Fills a copy of one container with black ink; null when the stack exposes no fluid handler. */
        Exchange fillItem(ItemStack container, int amount, boolean execute);
        /** The amount accepted, or -1 when the block exposes no fluid handler on that side. */
        int fillTank(ServerLevel level, BlockPos pos, Direction side, Fluid fluid, int amount, boolean execute);
        /** The amount of black ink drained, or -1 when the block exposes no fluid handler on that side. */
        int drainTank(ServerLevel level, BlockPos pos, Direction side, int amount, boolean execute);
        /** The remainder, or null when the block exposes no item handler on that side. */
        ItemStack insertItem(ServerLevel level, BlockPos pos, Direction side, ItemStack stack, boolean execute);
        /** The extracted stack, or null when the block exposes no item handler on that side. */
        ItemStack extractItem(ServerLevel level, BlockPos pos, Direction side, int amount, boolean execute);
        /** Whether the loader's own fluid tracking reports the entity's eyes in black ink. */
        boolean isEyeInInk(Entity entity);
        /** Black ink's registered entity physics as the loader reports them. */
        InkPhysics inkPhysics(ServerLevel level);
    }

    /** The vial holds one indivisible bucket: it drains whole into a glass bottle and refuses partial requests. */
    public static void vialDrainsOneIndivisibleBucket(GameTestHelper helper, Transfer transfer) {
        ItemStack vial = new ItemStack(InkMixerRegistry.INK_VIAL.get());
        Exchange partial = transfer.drainItem(vial, 999, true);
        require(helper, partial != null, "ink vial exposes no item fluid handler");
        expect(helper, partial, 0, InkMixerRegistry.INK_VIAL.get().getDefaultInstance(), "partial vial drain");
        expect(helper, transfer.drainItem(vial, 1000, false), 1000, vial, "simulated vial drain");
        expect(helper, transfer.drainItem(vial, 1000, true), 1000, new ItemStack(Items.GLASS_BOTTLE), "vial drain");
        // Neither loader turns a plain glass bottle into an ink vial through the fluid API (the mixer does that).
        Exchange bottle = transfer.fillItem(new ItemStack(Items.GLASS_BOTTLE), 1000, true);
        require(helper, bottle == null || bottle.amount() == 0, "a glass bottle accepted ink: " + bottle);
        helper.succeed();
    }

    /** The black ink bucket empties to a bucket, and an empty bucket fills to a black ink bucket, one bucket at a time. */
    public static void bucketExchangesWholeBuckets(GameTestHelper helper, Transfer transfer) {
        ItemStack full = new ItemStack(InkRegistry.BLACK_INK_BUCKET.get());
        Exchange partial = transfer.drainItem(full, 500, true);
        require(helper, partial != null, "black ink bucket exposes no item fluid handler");
        expect(helper, partial, 0, full, "partial bucket drain");
        expect(helper, transfer.drainItem(full, 1000, false), 1000, full, "simulated bucket drain");
        expect(helper, transfer.drainItem(full, 1000, true), 1000, new ItemStack(Items.BUCKET), "bucket drain");
        ItemStack empty = new ItemStack(Items.BUCKET);
        expect(helper, transfer.fillItem(empty, 999, true), 0, empty, "partial bucket fill");
        expect(helper, transfer.fillItem(empty, 1000, false), 1000, empty, "simulated bucket fill");
        expect(helper, transfer.fillItem(empty, 1000, true), 1000, full, "bucket fill");
        helper.succeed();
    }

    /** The desk's 1000 mB inkwell accepts fractional ink on any side and leaves state alone when simulating. */
    public static void deskInkwellTransfersFractionalInk(GameTestHelper helper, Transfer transfer) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(new BlockPos(3, 2, 3));
        var state = WritingDeskRegistry.WRITING_DESK.get().defaultBlockState().setValue(WritingDeskBlock.FACING, Direction.NORTH);
        // Flag 2 places both halves without the neighbour checks that would break a half-built desk.
        level.setBlock(head.relative(Direction.NORTH), state.setValue(WritingDeskBlock.FOOT, true), 2);
        level.setBlock(head, state, 2);
        require(helper, level.getBlockEntity(head) instanceof WritingDeskBlockEntity, "writing desk did not create its block entity");
        WritingDeskBlockEntity desk = (WritingDeskBlockEntity) level.getBlockEntity(head);
        Fluid ink = InkRegistry.BLACK_INK.get();
        equal(helper, 137, transfer.fillTank(level, head, Direction.UP, ink, 137, false), "simulated desk fill");
        equal(helper, 0, desk.inkAmount(), "simulated desk fill changed the inkwell");
        equal(helper, 137, transfer.fillTank(level, head, Direction.NORTH, ink, 137, true), "desk fill");
        equal(helper, 137, desk.inkAmount(), "desk fill amount");
        equal(helper, 50, transfer.drainTank(level, head, Direction.EAST, 50, false), "simulated desk drain");
        equal(helper, 137, desk.inkAmount(), "simulated desk drain changed the inkwell");
        equal(helper, 50, transfer.drainTank(level, head, Direction.DOWN, 50, true), "desk drain");
        equal(helper, 87, desk.inkAmount(), "desk drain amount");
        equal(helper, 0, transfer.fillTank(level, head, Direction.UP, Fluids.WATER, 100, true), "desk accepted water");
        equal(helper, WritingDeskBlockEntity.INK_CAPACITY - 87, transfer.fillTank(level, head, Direction.UP, ink, 5000, true), "desk overfill");
        equal(helper, WritingDeskBlockEntity.INK_CAPACITY, desk.inkAmount(), "desk capacity");
        equal(helper, WritingDeskBlockEntity.INK_CAPACITY, transfer.drainTank(level, head, Direction.UP, 5000, true), "desk overdrain");
        equal(helper, 0, desk.inkAmount(), "desk empty");
        helper.succeed();
    }

    /** The lectern's single slot takes one acceptable item (books, pages, maps) and rejects everything else. */
    public static void lecternInventoryHoldsOneBook(GameTestHelper helper, Transfer transfer) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, ExplorationRegistry.LECTERN.get().defaultBlockState(), 3);
        require(helper, level.getBlockEntity(pos) instanceof MystLecternBlockEntity, "lectern did not create its block entity");
        MystLecternBlockEntity lectern = (MystLecternBlockEntity) level.getBlockEntity(pos);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack rejected = transfer.insertItem(level, pos, Direction.UP, dirt, true);
        require(helper, rejected != null, "lectern exposes no item handler");
        same(helper, dirt, rejected, "lectern accepted dirt");
        require(helper, lectern.isEmpty(), "lectern stored dirt");
        ItemStack maps = new ItemStack(Items.FILLED_MAP, 2);
        same(helper, new ItemStack(Items.FILLED_MAP), transfer.insertItem(level, pos, Direction.NORTH, maps, false), "simulated lectern insert");
        require(helper, lectern.isEmpty(), "simulated lectern insert changed the lectern");
        same(helper, new ItemStack(Items.FILLED_MAP), transfer.insertItem(level, pos, Direction.NORTH, maps, true), "lectern insert");
        same(helper, new ItemStack(Items.FILLED_MAP), lectern.getBook(), "lectern contents");
        same(helper, new ItemStack(Items.FILLED_MAP), transfer.insertItem(level, pos, Direction.NORTH, new ItemStack(Items.FILLED_MAP), true), "full lectern insert");
        same(helper, new ItemStack(Items.FILLED_MAP), transfer.extractItem(level, pos, Direction.DOWN, 1, false), "simulated lectern extract");
        require(helper, !lectern.isEmpty(), "simulated lectern extract emptied the lectern");
        same(helper, new ItemStack(Items.FILLED_MAP), transfer.extractItem(level, pos, Direction.DOWN, 1, true), "lectern extract");
        require(helper, lectern.isEmpty(), "lectern extract left the book");
        helper.succeed();
    }

    /** Ink is WATER to walking mobs, and the walkable block beside it is WATER_BORDER, as Forge's FluidType defaults give. */
    public static void inkPathsAsWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 2, 3), InkRegistry.BLACK_INK_BLOCK.get());
        BlockPathTypes ink = WalkNodeEvaluator.getBlockPathTypeStatic(level, helper.absolutePos(new BlockPos(3, 2, 3)).mutable());
        BlockPathTypes beside = WalkNodeEvaluator.getBlockPathTypeStatic(level, helper.absolutePos(new BlockPos(4, 2, 3)).mutable());
        require(helper, ink == BlockPathTypes.WATER, "black ink has path type " + ink);
        require(helper, beside == BlockPathTypes.WATER_BORDER, "the block beside black ink has path type " + beside);
        helper.succeed();
    }

    /** A cow submerged in ink is in the fluid, starts swimming and loses air, but ink is not water and does not put out fire. */
    public static void entityInInkSwimsAndDrowns(GameTestHelper helper, Transfer transfer) {
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
            helper.setBlock(new BlockPos(3, y, 3), InkRegistry.BLACK_INK_BLOCK.get());
        }
        // A roof keeps rain from putting the fire out.
        helper.setBlock(new BlockPos(3, 5, 3), Blocks.GLASS);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 2, 3));
        cow.setAirSupply(100);
        cow.setSprinting(true);
        cow.setSwimming(false);
        cow.setSecondsOnFire(10);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    require(helper, transfer.isEyeInInk(cow), "the loader does not report the cow's eyes in black ink");
                    require(helper, cow.isSwimming(), "a sprinting cow in black ink is not swimming");
                    require(helper, !cow.isInWater(), "black ink counts as water");
                })
                .thenIdle(18)
                .thenExecute(() -> {
                    require(helper, cow.getAirSupply() < 100, "a cow in black ink kept its air: " + cow.getAirSupply());
                    require(helper, cow.isOnFire(), "black ink extinguished the cow; Forge's ink FluidType does not");
                })
                .thenSucceed();
    }

    /** Fabric's Fluid Physics registration must carry {@code ForgeInkFluidType}'s values. */
    public static void inkPhysicsMatchesForgeFluidType(GameTestHelper helper, Transfer transfer) {
        InkPhysics actual = transfer.inkPhysics(helper.getLevel());
        require(helper, FORGE_INK_FLUID_TYPE.equals(actual), "black ink physics " + actual + " differ from " + FORGE_INK_FLUID_TYPE);
        helper.succeed();
    }

    /**
     * Every registered biome is in the default {@code #mystcraft:has_exploration} tag, which reproduces the earlier
     * every-biome selection, and a biome carries the library and crystal features exactly when it is in the tag.
     */
    public static void explorationFeaturesFollowTheBiomeTag(GameTestHelper helper) {
        var biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        ResourceKey<PlacedFeature> library = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation("mystcraft", "small_library"));
        ResourceKey<PlacedFeature> crystal = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation("mystcraft", "crystal_formation"));
        int count = 0;
        for (Holder.Reference<Biome> biome : biomes.holders().toList()) {
            String id = biome.key().location().toString();
            boolean tagged = biome.is(HAS_EXPLORATION);
            require(helper, tagged, "biome " + id + " is missing from #mystcraft:has_exploration");
            var features = biome.value().getGenerationSettings().features();
            require(helper, has(features, GenerationStep.Decoration.SURFACE_STRUCTURES, library) == tagged, "small_library on " + id + " does not follow the tag");
            require(helper, has(features, GenerationStep.Decoration.UNDERGROUND_DECORATION, crystal) == tagged, "crystal_formation on " + id + " does not follow the tag");
            count++;
        }
        require(helper, count >= 63, "expected at least the 63 vanilla biomes, found " + count);
        helper.succeed();
    }

    private static boolean has(java.util.List<HolderSet<PlacedFeature>> features, GenerationStep.Decoration step, ResourceKey<PlacedFeature> key) {
        return features.size() > step.ordinal() && features.get(step.ordinal()).stream().anyMatch(holder -> holder.is(key));
    }

    private static void expect(GameTestHelper helper, Exchange actual, int amount, ItemStack container, String label) {
        require(helper, actual != null, label + ": no fluid handler");
        equal(helper, amount, actual.amount(), label);
        same(helper, container, actual.container(), label + " container");
    }

    private static void equal(GameTestHelper helper, int expected, int actual, String label) {
        require(helper, expected == actual, label + ": expected " + expected + ", got " + actual);
    }

    private static void same(GameTestHelper helper, ItemStack expected, ItemStack actual, String label) {
        require(helper, actual != null && ItemStack.matches(expected, actual),
                label + ": expected " + expected + " " + expected.getTag() + ", got " + actual + (actual == null ? "" : " " + actual.getTag()));
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(Objects.requireNonNull(message));
    }
}
