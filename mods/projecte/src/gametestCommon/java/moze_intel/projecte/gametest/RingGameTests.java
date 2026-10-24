/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import static moze_intel.projecte.gametest.PEGameTestSupport.TEMPLATE;
import static moze_intel.projecte.gametest.PEGameTestSupport.check;

import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.gameObjs.block_entities.DMPedestalBlockEntity;
import moze_intel.projecte.gameObjs.entity.EntityHomingArrow;
import moze_intel.projecte.gameObjs.items.ItemPE;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.handlers.InternalAbilities;
import moze_intel.projecte.utils.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Rings, amulets and stones, in a player's hotbar and on a Dark Matter Pedestal: each one's core effect. */
public class RingGameTests {

    private static final BlockPos CENTER = new BlockPos(3, 1, 3);

    /** An active Soul Stone with EMC heals its holder. */
    @GameTest(template = TEMPLATE)
    public static void soulStoneHealsItsHolder(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "soul_stone");
        player.setHealth(10);
        hotbarTick(helper, player, active(PEItems.SOUL_STONE.get(), 1_000));
        check(helper, player.getHealth() > 10, "Health stayed at " + player.getHealth());
        helper.succeed();
    }

    /** An active Body Stone with EMC feeds its holder. */
    @GameTest(template = TEMPLATE)
    public static void bodyStoneFeedsItsHolder(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "body_stone");
        player.getFoodData().setFoodLevel(10);
        hotbarTick(helper, player, active(PEItems.BODY_STONE.get(), 1_000));
        check(helper, player.getFoodData().getFoodLevel() > 10, "Food stayed at " + player.getFoodData().getFoodLevel());
        helper.succeed();
    }

    /** An active Life Stone both heals and feeds. */
    @GameTest(template = TEMPLATE)
    public static void lifeStoneHealsAndFeeds(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "life_stone");
        player.setHealth(10);
        player.getFoodData().setFoodLevel(10);
        hotbarTick(helper, player, active(PEItems.LIFE_STONE.get(), 1_000));
        check(helper, player.getHealth() > 10, "Health stayed at " + player.getHealth());
        check(helper, player.getFoodData().getFoodLevel() > 10, "Food stayed at " + player.getFoodData().getFoodLevel());
        helper.succeed();
    }

    /** An active Mind Stone draws its holder's experience in; inactive, right-clicking gives it back. */
    @GameTest(template = TEMPLATE)
    public static void mindStoneStoresAndReturnsExperience(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "mind_stone");
        player.giveExperiencePoints(200);
        int before = player.totalExperience;
        ItemStack stone = active(PEItems.MIND_STONE.get(), 0);
        hotbarTick(helper, player, stone);
        int stored = storedXp(stone);
        check(helper, stored > 0 && player.totalExperience < before, "Stored " + stored + " XP; the player went from " + before + " to " + player.totalExperience);
        stone.getOrCreateTag().putBoolean(Constants.NBT_KEY_ACTIVE, false);
        int drained = player.totalExperience;
        stone.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(helper, player.totalExperience > drained && storedXp(stone) < stored, "Right-clicking returned no experience");
        helper.succeed();
    }

    /** Right-clicking the Zero Ring freezes the water around the player. */
    @GameTest(template = TEMPLATE)
    public static void zeroRingFreezesWater(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "zero_ring");
        BlockPos water = new BlockPos(2, 1, 1);
        helper.setBlock(water.below(), Blocks.STONE);
        helper.setBlock(water, Blocks.WATER);
        ItemStack ring = new ItemStack(PEItems.ZERO_RING.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ring);
        ring.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertBlockPresent(Blocks.ICE, water);
        helper.succeed();
    }

    /** The Ring of Ignition lights the campfire it is used on (upstream lights campfires and ignites flammable blocks in place). */
    @GameTest(template = TEMPLATE)
    public static void ignitionRingLightsCampfires(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "ignition_ring");
        helper.setBlock(CENTER, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        useOn(helper, player, new ItemStack(PEItems.IGNITION_RING.get()), CENTER, Direction.UP);
        check(helper, helper.getBlockState(CENTER).getValue(CampfireBlock.LIT), "The campfire was not lit");
        helper.succeed();
    }

    /** The Evertide Amulet places water and the Volcanite Amulet places lava. */
    @GameTest(template = TEMPLATE)
    public static void amuletsPlaceTheirFluids(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "amulets");
        BlockPos waterBase = new BlockPos(1, 1, 3);
        BlockPos lavaBase = new BlockPos(5, 1, 3);
        helper.setBlock(waterBase, Blocks.STONE);
        helper.setBlock(lavaBase, Blocks.STONE);
        useOn(helper, player, new ItemStack(PEItems.EVERTIDE_AMULET.get()), waterBase, Direction.UP);
        ItemStack volcanite = new ItemStack(PEItems.VOLCANITE_AMULET.get());
        ItemPE.setEmc(volcanite, 1_000);
        useOn(helper, player, volcanite, lavaBase, Direction.UP);
        helper.assertBlockPresent(Blocks.WATER, waterBase.above());
        helper.assertBlockPresent(Blocks.LAVA, lavaBase.above());
        helper.succeed();
    }

    /** Sneak-using the Harvest Goddess Band spends four bone meal to grow the crops around the clicked block. */
    @GameTest(template = TEMPLATE)
    public static void harvestGoddessBandGrowsCrops(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "harvest_goddess");
        helper.setBlock(CENTER.below(), Blocks.FARMLAND);
        helper.setBlock(CENTER, Blocks.WHEAT);
        player.getInventory().setItem(1, new ItemStack(Items.BONE_MEAL, 8));
        player.setShiftKeyDown(true);
        useOn(helper, player, new ItemStack(PEItems.HARVEST_GODDESS_BAND.get()), CENTER, Direction.UP);
        player.setShiftKeyDown(false);
        int age = helper.getBlockState(CENTER).getValue(CropBlock.AGE);
        check(helper, age > 0, "The wheat did not grow");
        check(helper, player.getInventory().getItem(1).getCount() == 4, "Bone meal left: " + player.getInventory().getItem(1).getCount());
        helper.succeed();
    }

    /** An active Black Hole Band, and the Void Ring that contains one, pull nearby items toward their holder. */
    @GameTest(template = TEMPLATE)
    public static void blackHoleBandAndVoidRingPullItems(GameTestHelper helper) {
        for (Item ring : new Item[]{PEItems.BLACK_HOLE_BAND.get(), PEItems.VOID_RING.get()}) {
            ServerPlayer player = PEGameTestSupport.player(helper, "pull_" + ring.getDescriptionId());
            ItemEntity item = helper.spawnItem(Items.COBBLESTONE, 5.5F, 2, 5.5F);
            item.setNoGravity(true);
            double before = item.distanceToSqr(player);
            hotbarTick(helper, player, active(ring, 0));
            item.move(net.minecraft.world.entity.MoverType.SELF, item.getDeltaMovement());
            check(helper, item.distanceToSqr(player) < before, ring.getDescriptionId() + " did not pull the item");
            item.discard();
        }
        helper.succeed();
    }

    /** The Swiftwolf's Rending Gale, with EMC, lets its holder fly. */
    @GameTest(template = TEMPLATE)
    public static void swiftwolfsRendingGaleGrantsFlight(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "swrg");
        player.getAbilities().mayfly = false;
        ItemStack ring = new ItemStack(PEItems.SWIFTWOLF_RENDING_GALE.get());
        ItemPE.setEmc(ring, 1_000);
        hotbarTick(helper, player, ring);
        player.getCapability(InternalAbilities.CAPABILITY).ifPresent(InternalAbilities::tick);
        check(helper, player.getAbilities().mayfly, "The player cannot fly");
        player.getInventory().clearContent();
        player.getCapability(InternalAbilities.CAPABILITY).ifPresent(InternalAbilities::tick);
        check(helper, !player.getAbilities().mayfly, "Flight stayed after the ring was removed");
        helper.succeed();
    }

    /** Right-clicking the Archangel's Smite fires a homing arrow. */
    @GameTest(template = TEMPLATE)
    public static void archangelsSmiteFiresHomingArrows(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "archangel");
        ItemStack ring = new ItemStack(PEItems.ARCHANGEL_SMITE.get());
        // Each arrow costs an arrow's EMC, from the ring or the player's fuel.
        ItemPE.setEmc(ring, 1_000);
        player.setItemInHand(InteractionHand.MAIN_HAND, ring);
        ring.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(helper, !helper.getLevel().getEntitiesOfClass(EntityHomingArrow.class, player.getBoundingBox().inflate(4)).isEmpty(), "No homing arrow");
        helper.getLevel().getEntitiesOfClass(EntityHomingArrow.class, player.getBoundingBox().inflate(4)).forEach(arrow -> arrow.discard());
        helper.succeed();
    }

    /** The Arcana Ring acts as the ring its mode selects: in Ignition mode it lights a campfire, in Zero mode it does not. */
    @GameTest(template = TEMPLATE)
    public static void arcanaRingUsesItsSelectedMode(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "arcana");
        BlockPos zeroFire = CENTER.west(2);
        helper.setBlock(CENTER, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        helper.setBlock(zeroFire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        ItemStack ignition = new ItemStack(PEItems.ARCANA_RING.get());
        ignition.getOrCreateTag().putByte(Constants.NBT_KEY_MODE, (byte) 1);
        useOn(helper, player, ignition, CENTER, Direction.UP);
        ItemStack zero = new ItemStack(PEItems.ARCANA_RING.get());
        zero.getOrCreateTag().putByte(Constants.NBT_KEY_MODE, (byte) 0);
        useOn(helper, player, zero, zeroFire, Direction.UP);
        check(helper, helper.getBlockState(CENTER).getValue(CampfireBlock.LIT), "Ignition mode did not light the campfire");
        check(helper, !helper.getBlockState(zeroFire).getValue(CampfireBlock.LIT), "Zero mode lit a campfire");
        helper.succeed();
    }

    /** The Repair Talisman repairs damaged tools in its holder's inventory. */
    @GameTest(template = TEMPLATE)
    public static void repairTalismanRepairsTools(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "repair_talisman");
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(100);
        player.getInventory().setItem(5, pick);
        hotbarTick(helper, player, new ItemStack(PEItems.REPAIR_TALISMAN.get()));
        check(helper, player.getInventory().getItem(5).getDamageValue() < 100, "The pickaxe was not repaired");
        helper.succeed();
    }

    /** An active Gem of Eternal Density condenses cheaper items in the inventory into its target (iron by default). */
    @GameTest(template = TEMPLATE)
    public static void gemOfEternalDensityCondensesItems(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "density");
        player.getInventory().setItem(5, new ItemStack(Items.COAL, 64));
        ItemStack gem = active(PEItems.GEM_OF_ETERNAL_DENSITY.get(), 0);
        player.getInventory().setItem(0, gem);
        for (int i = 0; i < 8; i++) {
            gem.inventoryTick(helper.getLevel(), player, 0, true);
        }
        check(helper, player.getInventory().countItem(Items.IRON_INGOT) > 0, "No iron was condensed; coal left: " + player.getInventory().countItem(Items.COAL));
        helper.succeed();
    }

    /** The Watch of Flowing Time, active, speeds up block entities around its holder. */
    @GameTest(template = TEMPLATE)
    public static void watchOfFlowingTimeSpeedsUpBlockEntities(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "watch");
        BlockPos furnacePos = new BlockPos(1, 1, 1);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace =
              (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(furnacePos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));
        ItemStack watch = active(PEItems.WATCH_OF_FLOWING_TIME.get(), 1_000_000);
        player.getInventory().setItem(0, watch);
        // A furnace needs 200 ticks; the watch adds 8 per tick at its lowest charge.
        for (int i = 0; i < 30; i++) {
            watch.inventoryTick(helper.getLevel(), player, 0, true);
        }
        check(helper, furnace.getItem(2).is(Items.IRON_INGOT), "The furnace did not finish in 30 sped-up ticks");
        helper.succeed();
    }

    /** A Zero Ring placed on a Dark Matter Pedestal and switched on freezes the water around the pedestal. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pedestalRunsItsItem(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "pedestal");
        helper.setBlock(CENTER, PEBlocks.DARK_MATTER_PEDESTAL.getBlock());
        BlockPos water = CENTER.east(2);
        helper.setBlock(water.below(), Blocks.STONE);
        helper.setBlock(water, Blocks.WATER);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PEItems.ZERO_RING.get()));
        usePedestal(helper, player);
        DMPedestalBlockEntity pedestal = (DMPedestalBlockEntity) helper.getBlockEntity(CENTER);
        check(helper, pedestal.getInventory().getStackInSlot(0).is(PEItems.ZERO_RING.get()), "The ring was not placed on the pedestal");
        check(helper, pedestal.getInventory().getStackInSlot(0).getCapability(PECapabilities.PEDESTAL_ITEM_CAPABILITY).isPresent(),
              "The ring has no pedestal capability");
        usePedestal(helper, player);
        check(helper, pedestal.getActive(), "An empty-handed click did not switch the pedestal on");
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.ICE, water));
    }

    private static void usePedestal(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(CENTER);
        helper.getLevel().getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static int storedXp(ItemStack stone) {
        return stone.getOrCreateTag().getInt(Constants.NBT_KEY_STORED_XP);
    }

    static ItemStack active(Item item, long emc) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putBoolean(Constants.NBT_KEY_ACTIVE, true);
        if (emc > 0) {
            ItemPE.setEmc(stack, emc);
        }
        return stack;
    }

    /** Puts the stack in the first hotbar slot and runs one inventory tick on it, as the player's own tick would. */
    static void hotbarTick(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
        player.getInventory().setItem(0, stack);
        stack.inventoryTick(helper.getLevel(), player, 0, true);
    }

    static void useOn(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relative, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
