/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import static moze_intel.projecte.gametest.PEGameTestSupport.TEMPLATE;
import static moze_intel.projecte.gametest.PEGameTestSupport.check;

import com.google.common.collect.Multimap;
import java.util.Collection;
import moze_intel.projecte.api.capabilities.item.IItemCharge;
import moze_intel.projecte.gameObjs.block_entities.DMFurnaceBlockEntity;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.utils.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Dark and Red Matter tools and armour, and Gem armour: each item's core effect. */
public class MatterGameTests {

    private static final BlockPos CENTER = new BlockPos(3, 1, 3);

    /** Right-clicking an ore with a Dark Matter Pickaxe harvests the whole connected vein. */
    @GameTest(template = TEMPLATE)
    public static void pickaxeVeinMinesAnOre(GameTestHelper helper) {
        BlockPos[] vein = {CENTER, CENTER.east(), CENTER.east(2), CENTER.east(2).above()};
        for (BlockPos pos : vein) {
            helper.setBlock(pos, Blocks.IRON_ORE);
        }
        helper.setBlock(CENTER.west(), Blocks.STONE);
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_pick");
        useOn(helper, player, new ItemStack(PEItems.DARK_MATTER_PICKAXE.get()), CENTER, Direction.UP);
        for (BlockPos pos : vein) {
            helper.assertBlockPresent(Blocks.AIR, pos);
        }
        helper.assertBlockPresent(Blocks.STONE, CENTER.west());
        helper.assertItemEntityPresent(net.minecraft.world.item.Items.RAW_IRON, CENTER, 3);
        helper.succeed();
    }

    /** A charged Dark Matter Shovel turns a square of grass into paths around the clicked block. */
    @GameTest(template = TEMPLATE)
    public static void chargedShovelFlattensAnArea(GameTestHelper helper) {
        fill(helper, Blocks.GRASS_BLOCK, 1);
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_shovel");
        useOn(helper, player, charged(PEItems.DARK_MATTER_SHOVEL.get(), 1), CENTER, Direction.UP);
        assertFilled(helper, Blocks.DIRT_PATH, 1);
        helper.succeed();
    }

    /** A charged Dark Matter Hoe tills a square of dirt. */
    @GameTest(template = TEMPLATE)
    public static void chargedHoeTillsAnArea(GameTestHelper helper) {
        fill(helper, Blocks.DIRT, 1);
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_hoe");
        useOn(helper, player, charged(PEItems.DARK_MATTER_HOE.get(), 1), CENTER, Direction.UP);
        assertFilled(helper, Blocks.FARMLAND, 1);
        helper.succeed();
    }

    /** A charged Dark Matter Axe strips every log of the same axis in a square. */
    @GameTest(template = TEMPLATE)
    public static void chargedAxeStripsAnArea(GameTestHelper helper) {
        fill(helper, Blocks.OAK_LOG, 1);
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_axe");
        useOn(helper, player, charged(PEItems.DARK_MATTER_AXE.get(), 1), CENTER, Direction.UP);
        assertFilled(helper, Blocks.STRIPPED_OAK_LOG, 1);
        helper.succeed();
    }

    /** A charged Dark Matter Hammer digs a square of stone around the clicked block. */
    @GameTest(template = TEMPLATE)
    public static void chargedHammerDigsAnArea(GameTestHelper helper) {
        fill(helper, Blocks.STONE, 1);
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_hammer");
        ItemStack hammer = charged(PEItems.DARK_MATTER_HAMMER.get(), 1);
        useOn(helper, player, hammer, CENTER, Direction.UP);
        assertFilled(helper, Blocks.AIR, 1);
        helper.succeed();
    }

    /** Dark Matter Shears shear the sheep around the player. */
    @GameTest(template = TEMPLATE)
    public static void shearsShearNearbySheep(GameTestHelper helper) {
        Sheep sheep = helper.spawn(EntityType.SHEEP, CENTER.east());
        sheep.setNoAi(true);
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_shears");
        ItemStack shears = new ItemStack(PEItems.DARK_MATTER_SHEARS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, shears);
        shears.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(helper, sheep.isSheared(), "The sheep was not sheared");
        helper.succeed();
    }

    /** A charged sword hits through armour for its base damage plus its charge. */
    @GameTest(template = TEMPLATE)
    public static void chargedSwordHitsHarder(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_sword");
        Zombie plain = zombie(helper, CENTER);
        Zombie charged = zombie(helper, CENTER.east(2));
        ItemStack sword = new ItemStack(PEItems.DARK_MATTER_SWORD.get());
        sword.hurtEnemy(plain, player);
        ItemStack chargedSword = charged(PEItems.DARK_MATTER_SWORD.get(), 2);
        chargedSword.hurtEnemy(charged, player);
        float plainLoss = plain.getMaxHealth() - plain.getHealth();
        float chargedLoss = charged.getMaxHealth() - charged.getHealth();
        check(helper, plainLoss > 0, "The uncharged sword did no damage");
        check(helper, chargedLoss > plainLoss, "Charge added no damage: " + plainLoss + " then " + chargedLoss);
        helper.succeed();
    }

    /** Charging a hammer raises its attack damage (Fabric: through Fabric API's stack-aware attribute hook). */
    @GameTest(template = TEMPLATE)
    public static void chargeRaisesAttackDamage(GameTestHelper helper) {
        double uncharged = attackDamage(new ItemStack(PEItems.DARK_MATTER_HAMMER.get()));
        double charged = attackDamage(charged(PEItems.DARK_MATTER_HAMMER.get(), 2));
        check(helper, charged > uncharged, "Charged hammer damage " + charged + " is not above " + uncharged);
        helper.succeed();
    }

    /** Changing a tool's charge goes through its charge capability, up to its number of charges. */
    @GameTest(template = TEMPLATE)
    public static void chargeChangesThroughTheCapability(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "dm_charge");
        ItemStack pick = new ItemStack(PEItems.DARK_MATTER_PICKAXE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        IItemCharge charge = (IItemCharge) pick.getItem();
        for (int i = 0; i < 4; i++) {
            charge.changeCharge(player, pick, InteractionHand.MAIN_HAND);
        }
        check(helper, charge.getCharge(pick) == charge.getNumCharges(pick),
              "Charge " + charge.getCharge(pick) + " after four presses, expected the maximum " + charge.getNumCharges(pick));
        helper.succeed();
    }

    /** Matter tools mine matter blocks of their tier or below almost instantly, and higher tiers at normal speed. */
    @GameTest(template = TEMPLATE)
    public static void matterToolsMineMatterBlocksOfTheirTier(GameTestHelper helper) {
        ItemStack darkPick = new ItemStack(PEItems.DARK_MATTER_PICKAXE.get());
        ItemStack redPick = new ItemStack(PEItems.RED_MATTER_PICKAXE.get());
        float darkOnDark = darkPick.getDestroySpeed(PEBlocks.DARK_MATTER.getBlock().defaultBlockState());
        float darkOnRed = darkPick.getDestroySpeed(PEBlocks.RED_MATTER.getBlock().defaultBlockState());
        float redOnRed = redPick.getDestroySpeed(PEBlocks.RED_MATTER.getBlock().defaultBlockState());
        check(helper, darkOnDark == 1_200_000 && redOnRed == 1_200_000, "Matter pickaxe speeds: " + darkOnDark + ", " + redOnRed);
        check(helper, darkOnRed < 1_200_000, "A Dark Matter Pickaxe mines Red Matter at full speed");
        helper.succeed();
    }

    /**
     * The Dark Matter furnace smelts with ordinary fuel and doubles ores with upstream's 50% chance. The roll uses the
     * level's shared random, which other tests in the batch also draw from, so the smelts run with the chance pinned:
     * one furnace always doubles (16 ores give exactly 32 ingots) and one never does (exactly 16).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void darkMatterFurnaceSmeltsAndDoublesOres(GameTestHelper helper) {
        PinnedDoublingFurnace always = pinnedDarkMatterFurnace(helper, CENTER, 1F);
        PinnedDoublingFurnace never = pinnedDarkMatterFurnace(helper, CENTER.west(2), 0F);
        check(helper, always.upstreamChance() == 0.5F, "The Dark Matter furnace doubles ores with chance " + always.upstreamChance() + ", not 0.5");
        loadOreAndFuel(helper, always);
        loadOreAndFuel(helper, never);
        helper.succeedWhen(() -> {
            requireSmelted(always, 32);
            requireSmelted(never, 16);
        });
    }

    /** The Red Matter furnace does the same, faster, and always doubles (chance 1), so the count is exact. */
    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void redMatterFurnaceSmeltsAndDoublesOres(GameTestHelper helper) {
        helper.setBlock(CENTER, PEBlocks.RED_MATTER_FURNACE.getBlock());
        DMFurnaceBlockEntity furnace = (DMFurnaceBlockEntity) helper.getBlockEntity(CENTER);
        check(helper, furnace != null, "No furnace block entity");
        loadOreAndFuel(helper, furnace);
        helper.succeedWhen(() -> requireSmelted(furnace, 32));
    }

    /** A full set of Dark Matter armour takes 80% off damage that bypasses vanilla armour. */
    @GameTest(template = TEMPLATE)
    public static void darkMatterArmourReducesDamage(GameTestHelper helper) {
        Zombie bare = zombie(helper, CENTER);
        Zombie armoured = zombie(helper, CENTER.east(2));
        armoured.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PEItems.DARK_MATTER_HELMET.get()));
        armoured.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PEItems.DARK_MATTER_CHESTPLATE.get()));
        armoured.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PEItems.DARK_MATTER_LEGGINGS.get()));
        armoured.setItemSlot(EquipmentSlot.FEET, new ItemStack(PEItems.DARK_MATTER_BOOTS.get()));
        bare.hurt(DamageSource.MAGIC, 10);
        armoured.hurt(DamageSource.MAGIC, 10);
        float bareLoss = bare.getMaxHealth() - bare.getHealth();
        float armouredLoss = armoured.getMaxHealth() - armoured.getHealth();
        check(helper, bareLoss >= 9.9F, "The unarmoured zombie lost only " + bareLoss);
        check(helper, armouredLoss <= 2.1F, "Dark Matter armour let through " + armouredLoss + " of 10");
        helper.succeed();
    }

    /** The Gem Helmet's night vision toggle and the Gem Boots' fall protection act on the armour tick. */
    @GameTest(template = TEMPLATE)
    public static void gemArmourActsOnTheArmourTick(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "gem_armour");
        ItemStack helmet = new ItemStack(PEItems.GEM_HELMET.get());
        helmet.getOrCreateTag().putBoolean(Constants.NBT_KEY_NIGHT_VISION, true);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(PEItems.GEM_BOOTS.get()));
        player.fallDistance = 12;
        player.getInventory().tick();
        check(helper, player.hasEffect(MobEffects.NIGHT_VISION), "The Gem Helmet gave no night vision");
        check(helper, player.fallDistance == 0, "The Gem Boots left a fall distance of " + player.fallDistance);
        helper.succeed();
    }

    /** Gem Boots double movement speed through their stack attribute modifiers. */
    @GameTest(template = TEMPLATE)
    public static void gemBootsAddMovementSpeed(GameTestHelper helper) {
        Multimap<Attribute, AttributeModifier> modifiers = new ItemStack(PEItems.GEM_BOOTS.get()).getAttributeModifiers(EquipmentSlot.FEET);
        Collection<AttributeModifier> speed = modifiers.get(Attributes.MOVEMENT_SPEED);
        check(helper, speed.stream().anyMatch(modifier -> modifier.getAmount() == 1.0), "Gem Boots speed modifiers: " + speed);
        check(helper, !modifiers.get(Attributes.ARMOR).isEmpty(), "Gem Boots lost their armour modifier");
        helper.succeed();
    }

    /** A Dark Matter furnace placed at {@code relative} whose ore-doubling chance is {@code chance}. */
    private static PinnedDoublingFurnace pinnedDarkMatterFurnace(GameTestHelper helper, BlockPos relative, float chance) {
        helper.setBlock(relative, PEBlocks.DARK_MATTER_FURNACE.getBlock());
        BlockPos pos = helper.absolutePos(relative);
        PinnedDoublingFurnace furnace = new PinnedDoublingFurnace(pos, helper.getLevel().getBlockState(pos), chance);
        helper.getLevel().setBlockEntity(furnace);
        check(helper, helper.getBlockEntity(relative) == furnace, "The pinned furnace did not replace the placed one");
        return furnace;
    }

    private static void loadOreAndFuel(GameTestHelper helper, DMFurnaceBlockEntity furnace) {
        check(helper, furnace.getFuel().insertItem(0, new ItemStack(Items.COAL, 4), false).isEmpty(), "Fuel refused");
        check(helper, furnace.getInput().insertItem(0, new ItemStack(Blocks.IRON_ORE, 16), false).isEmpty(), "Ore refused");
    }

    private static void requireSmelted(DMFurnaceBlockEntity furnace, int expectedIngots) {
        int oreLeft = 0;
        for (int slot = 0; slot < furnace.getInput().getSlots(); slot++) {
            oreLeft += furnace.getInput().getStackInSlot(slot).getCount();
        }
        PEGameTestSupport.require(oreLeft == 0, oreLeft + " ore left to smelt");
        int ingots = 0;
        for (int slot = 0; slot < furnace.getOutput().getSlots(); slot++) {
            ItemStack stack = furnace.getOutput().getStackInSlot(slot);
            if (stack.is(Items.IRON_INGOT)) {
                ingots += stack.getCount();
            }
        }
        PEGameTestSupport.require(ingots == expectedIngots, "Smelted 16 ores into " + ingots + " ingots, not " + expectedIngots);
    }

    /**
     * A Dark Matter furnace whose doubling roll ({@code random.nextFloat() < chance}) always succeeds at chance 1 and
     * never at chance 0, whatever the level's random returns. Everything else is the real furnace.
     */
    private static final class PinnedDoublingFurnace extends DMFurnaceBlockEntity {

        private final float chance;

        PinnedDoublingFurnace(BlockPos pos, BlockState state, float chance) {
            super(pos, state);
            this.chance = chance;
        }

        float upstreamChance() {
            return super.getOreDoubleChance();
        }

        @Override
        protected float getOreDoubleChance() {
            return chance;
        }
    }

    private static ItemStack charged(Item item, int charge) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt(IItemCharge.KEY, charge);
        return stack;
    }

    private static double attackDamage(ItemStack stack) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
              .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION)
              .mapToDouble(AttributeModifier::getAmount).sum();
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
        zombie.setNoAi(true);
        return zombie;
    }

    private static void fill(GameTestHelper helper, Block block, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                helper.setBlock(CENTER.offset(dx, 0, dz), block);
            }
        }
    }

    private static void assertFilled(GameTestHelper helper, Block block, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                helper.assertBlockPresent(block, CENTER.offset(dx, 0, dz));
            }
        }
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relative, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
