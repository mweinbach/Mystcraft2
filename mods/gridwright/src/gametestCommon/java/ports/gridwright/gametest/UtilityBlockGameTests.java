package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import ports.gridwright.block.CompressedKind;
import ports.gridwright.block.DrumTier;
import ports.gridwright.block.SpikeBlock;
import ports.gridwright.block.SpikeKind;
import ports.gridwright.block.TrashKind;
import ports.gridwright.platform.Platform;
import ports.gridwright.registry.ModUtilities;
import ports.gridwright.storage.DrumBlockEntity;
import ports.gridwright.storage.SoundMuffling;
import ports.gridwright.storage.TrashCanBlockEntity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.connectedPlayer;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.platform;

/** Phase 2b blocks: compressed blocks, spikes, the sound muffler, drums and trash cans. */
public class UtilityBlockGameTests {

    // --- Compressed blocks ------------------------------------------------------------------------

    private static CraftingContainer grid() {
        return new CraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
    }

    private static ItemStack craft(ServerLevel level, CraftingContainer grid) {
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        return recipe.map(found -> found.assemble(grid)).orElse(ItemStack.EMPTY);
    }

    @GameTest(template = TEMPLATE)
    public static void compressedBlocksCraftNineToOneAndBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int checked = 0;
        for (CompressedKind kind : CompressedKind.values()) {
            List<java.util.function.Supplier<Block>> tiers = ModUtilities.COMPRESSED.get(kind);
            check(tiers.size() == kind.tiers(), kind + " has " + tiers.size() + " tiers");
            Item base = Registry.ITEM.get(new ResourceLocation("minecraft", kind.name().toLowerCase(Locale.ROOT)));
            float lastHardness = -1;
            for (int tier = 1; tier <= kind.tiers(); tier++) {
                Item lower = tier == 1 ? base : tiers.get(tier - 2).get().asItem();
                Block block = tiers.get(tier - 1).get();
                CraftingContainer nine = grid();
                for (int slot = 0; slot < 9; slot++) nine.setItem(slot, new ItemStack(lower));
                ItemStack up = craft(level, nine);
                check(up.is(block.asItem()) && up.getCount() == 1, "Nine " + lower + " should make one " + block + ", got " + up);
                CraftingContainer one = grid();
                one.setItem(4, new ItemStack(block));
                ItemStack down = craft(level, one);
                check(down.is(lower) && down.getCount() == 9, "One " + block + " should make nine " + lower + ", got " + down);
                float hardness = block.defaultBlockState().getDestroySpeed(level, BlockPos.ZERO);
                check(hardness > lastHardness, block + " should be harder than the tier below");
                lastHardness = hardness;
                checked++;
            }
        }
        check(CompressedKind.contents(8) == 43_046_721L, "Octuple compressed cobblestone holds 9^8 cobblestone");
        check(checked == 22, "Checked " + checked + " compressed blocks");
        helper.succeed();
    }

    // --- Spikes -----------------------------------------------------------------------------------

    private static Cow cow(GameTestHelper helper, int x, int z) {
        return helper.spawnWithNoFreeWill(EntityType.COW, x, 1, z);
    }

    private static void hitUntilDead(ServerLevel level, Cow cow, SpikeKind kind) {
        for (int hit = 0; hit < 40 && cow.isAlive(); hit++) {
            cow.invulnerableTime = 0;
            SpikeBlock.hurt(level, cow, kind);
        }
    }

    @GameTest(template = TEMPLATE)
    public static void woodenSpikesNeverKill(GameTestHelper helper) {
        Cow cow = cow(helper, 2, 2);
        for (int hit = 0; hit < 40; hit++) {
            cow.invulnerableTime = 0;
            SpikeBlock.hurt(helper.getLevel(), cow, SpikeKind.WOODEN);
        }
        check(cow.isAlive() && cow.getHealth() == 1.0F, "A wooden spike leaves one health point: " + cow.getHealth());
        cow.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void goldenSpikesDropExperienceIronSpikesDoNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow iron = cow(helper, 1, 1);
        Cow golden = cow(helper, 6, 6);
        // Orbs fly off with a random velocity and the arena has no floor, so look across the whole arena
        // and well below it; the iron kill is checked first so its area is free of the golden kill's orbs.
        AABB arena = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8)))
                .inflate(4, 0, 4).expandTowards(0, -24, 0);
        hitUntilDead(level, iron, SpikeKind.IRON);
        check(!iron.isAlive(), "Iron spikes kill");
        helper.startSequence()
                .thenWaitUntil(() -> check(iron.isRemoved(), "Waiting for the iron spike kill's death animation"))
                .thenExecute(() -> {
                    check(level.getEntitiesOfClass(ExperienceOrb.class, arena).isEmpty(), "An iron spike kill drops none");
                    golden.invulnerableTime = 0;
                    SpikeBlock.hurt(level, golden, SpikeKind.GOLDEN);
                    check(golden.getKillCredit() instanceof ServerPlayer, "A golden spike credits its kills to a player");
                    hitUntilDead(level, golden, SpikeKind.GOLDEN);
                    check(!golden.isAlive(), "Golden spikes kill");
                })
                .thenWaitUntil(() -> check(!level.getEntitiesOfClass(ExperienceOrb.class, arena).isEmpty(),
                        "A golden spike kill drops experience"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void spikesHurtCreaturesStandingOnThem(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), ModUtilities.SPIKES.get(SpikeKind.WOODEN).get());
        // A glass pen keeps the cow on the spike; it needs AI (and so physics) to touch the block.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(3, y, 3).relative(side), Blocks.GLASS);
        }
        helper.setBlock(new BlockPos(3, 4, 3), Blocks.GLASS);
        Cow cow = helper.spawn(EntityType.COW, 3.5F, 2.0F, 3.5F);
        helper.succeedWhen(() -> {
            check(cow.getHealth() < cow.getMaxHealth(), "The cow on the spike is unhurt");
            check(cow.isAlive(), "A wooden spike killed the cow");
        });
    }

    // --- Sound Muffler ----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void soundMufflerQuietsNearbySounds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos muffler = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos far = muffler.offset(20, 0, 20);
        check(!SoundMuffling.muffled(level, far.getX(), far.getY(), far.getZ()), "Nothing muffled yet");
        helper.setBlock(new BlockPos(1, 1, 1), ModUtilities.SOUND_MUFFLER.get());
        check(SoundMuffling.volume(level, muffler.getX() + 5.5, muffler.getY() + 0.5, muffler.getZ() + 0.5, 1.0F) == SoundMuffling.FACTOR,
                "A sound five blocks away plays at a tenth of its volume");
        check(SoundMuffling.volume(level, far.getX(), far.getY(), far.getZ(), 1.0F) == 1.0F, "Sounds out of range are untouched");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        check(!SoundMuffling.muffled(level, muffler.getX() + 0.5, muffler.getY() + 0.5, muffler.getZ() + 0.5), "Breaking the muffler ends muffling");
        helper.succeed();
    }

    // --- Drums ------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void drumsHoldOneFluidThroughTheLoaderApi(GameTestHelper helper) {
        long bucket = Platform.bucketVolume();
        DrumBlockEntity drum = place(helper, ModUtilities.DRUMS.get(DrumTier.STONE), new BlockPos(1, 1, 1));
        TestPlatform.FluidAccess fluids = platform().fluids(drum, Direction.UP);
        check(fluids.fill(Fluids.WATER, 5 * bucket, false) == 5 * bucket, "Five buckets of water");
        check(fluids.amount() == 5 * bucket && fluids.fluid() == Fluids.WATER, "Drum holds " + fluids.amount());
        check(fluids.fill(Fluids.LAVA, bucket, true) == 0, "A drum holds one fluid at a time");
        check(fluids.fill(Fluids.WATER, 100 * bucket, false) == 11 * bucket, "A stone drum stops at 16 buckets");
        int full = drum.signal();
        check(fluids.drain(2 * bucket, false) == 2 * bucket && fluids.amount() == 14 * bucket, "Drained two buckets");
        check(full == 15 && drum.signal() < full && drum.signal() > 0, "Comparator signal follows the contents");
        check(fluids.drain(100 * bucket, false) == 14 * bucket && fluids.fluid() == Fluids.EMPTY, "Drained dry");
        check(drum.signal() == 0, "An empty drum gives no signal");
        long[] buckets = {16, 256, 4_096, 65_536};
        for (DrumTier tier : DrumTier.values()) {
            check(tier.buckets() == buckets[tier.ordinal()], tier + " holds " + tier.buckets() + " buckets");
        }
        DrumBlockEntity gargantuan = place(helper, ModUtilities.DRUMS.get(DrumTier.GARGANTUAN), new BlockPos(4, 1, 4));
        check(gargantuan.tank().capacity() == 65_536L * bucket, "Gargantuan capacity " + gargantuan.tank().capacity());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void drumsKeepTheirFluidWhenBroken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long bucket = Platform.bucketVolume();
        BlockPos first = new BlockPos(1, 1, 1);
        DrumBlockEntity drum = place(helper, ModUtilities.DRUMS.get(DrumTier.IRON), first);
        platform().fluids(drum, Direction.NORTH).fill(Fluids.LAVA, 7 * bucket, false);
        BlockState state = helper.getBlockState(first);
        List<ItemStack> drops = Block.getDrops(state, level, helper.absolutePos(first), drum);
        check(drops.size() == 1 && drops.get(0).is(ModUtilities.DRUMS.get(DrumTier.IRON).get().asItem()), "Drops " + drops);
        ItemStack dropped = drops.get(0);
        check(BlockItem.getBlockEntityData(dropped) != null, "The dropped drum should carry its fluid");
        BlockPos second = new BlockPos(4, 1, 4);
        DrumBlockEntity placed = place(helper, ModUtilities.DRUMS.get(DrumTier.IRON), second);
        BlockItem.updateCustomBlockEntityTag(level, null, helper.absolutePos(second), dropped);
        check(placed.tank().fluid() == Fluids.LAVA && placed.tank().amount() == 7 * bucket,
                "Placed drum holds " + placed.tank().amount() + " of " + placed.tank().fluid());
        BlockPos third = new BlockPos(6, 1, 6);
        DrumBlockEntity unused = place(helper, ModUtilities.DRUMS.get(DrumTier.STONE), third);
        List<ItemStack> empty = Block.getDrops(helper.getBlockState(third), level, helper.absolutePos(third), unused);
        check(empty.size() == 1 && !empty.get(0).hasTag(), "An empty drum drops without NBT, so empty drums stack");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void drumsFillFromBuckets(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwK"));
        BlockPos pos = new BlockPos(2, 1, 2);
        DrumBlockEntity drum = place(helper, ModUtilities.DRUMS.get(DrumTier.STONE), pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        check(Platform.interactWithFluidStorage(player, InteractionHand.MAIN_HAND, helper.getLevel(), helper.absolutePos(pos), Direction.UP),
                "Emptying a bucket into the drum");
        check(drum.tank().amount() == Platform.bucketVolume() && drum.tank().fluid() == Fluids.WATER, "Drum holds " + drum.tank().amount());
        check(player.getMainHandItem().is(Items.BUCKET), "The player keeps the empty bucket: " + player.getMainHandItem());
        helper.succeed();
    }

    // --- Trash cans -------------------------------------------------------------------------------

    private static TrashCanBlockEntity trash(GameTestHelper helper, TrashKind kind, BlockPos pos) {
        return place(helper, ModUtilities.TRASH_CANS.get(kind), pos);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void trashCanDestroysHopperInput(GameTestHelper helper) {
        TrashCanBlockEntity can = trash(helper, TrashKind.ITEM, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.HOPPER);
        BlockEntity hopper = helper.getBlockEntity(new BlockPos(2, 2, 2));
        check(hopper instanceof HopperBlockEntity, "No hopper");
        ((HopperBlockEntity) hopper).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        helper.succeedWhen(() -> {
            check(((HopperBlockEntity) hopper).isEmpty(), "Hopper still holds items");
            check(can.destroyed() == 10, "Destroyed " + can.destroyed());
            check(can.isEmpty() && can.getItem(0).isEmpty(), "A trash can never holds anything");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void trashCansDestroyThroughLoaderApis(GameTestHelper helper) {
        TrashCanBlockEntity items = trash(helper, TrashKind.ITEM, new BlockPos(1, 1, 1));
        check(platform().offerItems(items, Direction.UP, new ItemStack(Items.STONE, 64)) == 64, "Item can takes a full stack");
        check(items.destroyed() == 64, "Item can destroyed " + items.destroyed());

        long bucket = Platform.bucketVolume();
        TrashCanBlockEntity fluids = trash(helper, TrashKind.FLUID, new BlockPos(3, 1, 3));
        TestPlatform.FluidAccess tank = platform().fluids(fluids, Direction.NORTH);
        check(tank.fill(Fluids.LAVA, 1_000 * bucket, false) == 1_000 * bucket, "Fluid can takes everything");
        check(tank.amount() == 0 && tank.drain(bucket, false) == 0, "Nothing comes back out");
        check(fluids.destroyed() == 1_000 * bucket, "Fluid can destroyed " + fluids.destroyed());

        TrashCanBlockEntity energy = trash(helper, TrashKind.ENERGY, new BlockPos(5, 1, 5));
        TestPlatform.EnergyAccess battery = platform().energy(energy, Direction.EAST);
        check(battery.insert(50_000, true) == 50_000 && energy.destroyed() == 0, "Simulated insertion destroys nothing");
        check(battery.insert(50_000, false) == 50_000, "Energy can takes everything");
        check(battery.stored() == 0 && battery.extract(1_000, false) == 0, "Nothing comes back out");
        check(energy.destroyed() == 50_000, "Energy can destroyed " + energy.destroyed());
        helper.succeed();
    }
}
