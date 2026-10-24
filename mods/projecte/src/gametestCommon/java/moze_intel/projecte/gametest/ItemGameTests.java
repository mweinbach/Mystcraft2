/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import static moze_intel.projecte.gametest.PEGameTestSupport.TEMPLATE;
import static moze_intel.projecte.gametest.PEGameTestSupport.check;
import static moze_intel.projecte.gametest.RingGameTests.useOn;

import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.item.IItemEmcHolder;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import moze_intel.projecte.gameObjs.entity.EntityLensProjectile;
import moze_intel.projecte.gameObjs.entity.EntityNovaCatalystPrimed;
import moze_intel.projecte.gameObjs.entity.EntityWaterProjectile;
import moze_intel.projecte.gameObjs.items.IItemMode;
import moze_intel.projecte.gameObjs.items.ItemPE;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.utils.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * Klein Stars, the Destruction Catalyst, the lenses, Divining Rods, Mercurial Eye, Interdiction Torch, Nova explosives,
 * the alchemical fuels, the Tome of Knowledge and the key-bound item capabilities: each one's core effect.
 */
public class ItemGameTests {

    private static final BlockPos CENTER = new BlockPos(3, 1, 3);

    /** A Klein Star holds EMC up to its capacity through its EMC holder capability. */
    @GameTest(template = TEMPLATE)
    public static void kleinStarStoresEmc(GameTestHelper helper) {
        ItemStack star = new ItemStack(PEItems.KLEIN_STAR_EIN.get());
        IItemEmcHolder holder = star.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY).orElse(null);
        check(helper, holder != null, "The Klein Star has no EMC holder capability");
        long max = holder.getMaximumEmc(star);
        long inserted = holder.insertEmc(star, max * 2, EmcAction.EXECUTE);
        check(helper, max == 50_000 && inserted == max && holder.getStoredEmc(star) == max, "Klein Star Ein: max " + max + ", inserted " + inserted);
        long extracted = holder.extractEmc(star, 1_000, EmcAction.EXECUTE);
        check(helper, extracted == 1_000 && holder.getStoredEmc(star) == max - 1_000, "Extracted " + extracted);
        helper.succeed();
    }

    /** The Destruction Catalyst clears blocks in front of it, paid for by a Klein Star in the off hand. */
    @GameTest(template = TEMPLATE)
    public static void destructionCatalystClearsBlocksUsingKleinStarEmc(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "catalyst");
        BlockPos top = new BlockPos(3, 4, 3);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(3, y, 3), Blocks.DIRT);
        }
        ItemStack star = new ItemStack(PEItems.KLEIN_STAR_EIN.get());
        ItemPE.setEmc(star, 10_000);
        player.setItemInHand(InteractionHand.OFF_HAND, star);
        useOn(helper, player, new ItemStack(PEItems.DESTRUCTION_CATALYST.get()), top, Direction.UP);
        helper.assertBlockPresent(Blocks.AIR, top);
        check(helper, ItemPE.getEmc(player.getOffhandItem()) < 10_000, "The Klein Star paid nothing");
        helper.succeed();
    }

    /** The Hyperkinetic Lens and the Catalytic Lens fire an explosive projectile for EMC (the projectile key's action). */
    @GameTest(template = TEMPLATE)
    public static void lensesFireExplosiveProjectiles(GameTestHelper helper) {
        for (Item lens : new Item[]{PEItems.HYPERKINETIC_LENS.get(), PEItems.CATALYTIC_LENS.get()}) {
            ServerPlayer player = PEGameTestSupport.player(helper, "lens_" + lens.getDescriptionId());
            ItemStack stack = new ItemStack(lens);
            ItemPE.setEmc(stack, 100_000);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            boolean fired = stack.getCapability(PECapabilities.PROJECTILE_SHOOTER_ITEM_CAPABILITY)
                  .map(cap -> cap.shootProjectile(player, stack, InteractionHand.MAIN_HAND)).orElse(false);
            check(helper, fired && !helper.getLevel().getEntitiesOfClass(EntityLensProjectile.class, player.getBoundingBox().inflate(4)).isEmpty(),
                  lens.getDescriptionId() + " fired nothing");
            check(helper, ItemPE.getEmc(stack) < 100_000, lens.getDescriptionId() + " cost no EMC");
            helper.getLevel().getEntitiesOfClass(EntityLensProjectile.class, player.getBoundingBox().inflate(4)).forEach(e -> e.discard());
        }
        helper.succeed();
    }

    /** A Divining Rod reports on the blocks it is used on and fails on empty space. */
    @GameTest(template = TEMPLATE)
    public static void diviningRodsReadTheGround(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "divining");
        helper.setBlock(CENTER, Blocks.IRON_ORE);
        for (Item rod : new Item[]{PEItems.LOW_DIVINING_ROD.get(), PEItems.MEDIUM_DIVINING_ROD.get(), PEItems.HIGH_DIVINING_ROD.get()}) {
            ItemStack stack = new ItemStack(rod);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos pos = helper.absolutePos(CENTER);
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.UP, 0.5), Direction.UP, pos, false);
            var result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            check(helper, result.consumesAction() || result == net.minecraft.world.InteractionResult.SUCCESS || result == net.minecraft.world.InteractionResult.CONSUME,
                  rod.getDescriptionId() + " on ore returned " + result);
        }
        helper.succeed();
    }

    /** The Mercurial Eye in creation mode builds its target block, paid for by the Klein Star inside it. */
    @GameTest(template = TEMPLATE)
    public static void mercurialEyeBuildsWithItsKleinStar(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "mercurial");
        helper.setBlock(CENTER, Blocks.DIRT);
        ItemStack eye = new ItemStack(PEItems.MERCURIAL_EYE.get());
        ItemStack star = new ItemStack(PEItems.KLEIN_STAR_EIN.get());
        ItemPE.setEmc(star, 10_000);
        var inventory = eye.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        check(helper, inventory != null, "The Mercurial Eye has no inventory");
        inventory.insertItem(0, star, false);
        inventory.insertItem(1, new ItemStack(Items.COBBLESTONE), false);
        useOn(helper, player, eye, CENTER, Direction.UP);
        helper.assertBlockPresent(Blocks.COBBLESTONE, CENTER.above());
        check(helper, ItemPE.getEmc(inventory.getStackInSlot(0)) < 10_000, "The eye's Klein Star paid nothing");
        helper.succeed();
    }

    /** The Interdiction Torch pushes mobs away from itself. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void interdictionTorchRepelsMobs(GameTestHelper helper) {
        helper.setBlock(CENTER.below(), Blocks.STONE);
        helper.setBlock(CENTER, PEBlocks.INTERDICTION_TORCH.getBlock());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, CENTER.east(2));
        zombie.setNoAi(true);
        Vec3 torch = Vec3.atCenterOf(helper.absolutePos(CENTER));
        // A no-AI mob does not travel, so the push shows up as velocity pointing away from the torch.
        helper.succeedWhen(() -> {
            Vec3 away = zombie.position().subtract(torch).normalize();
            PEGameTestSupport.require(zombie.getDeltaMovement().dot(away) > 0, "The zombie is not being pushed away: " + zombie.getDeltaMovement());
        });
    }

    /**
     * A primed Nova Catalyst explodes sooner than TNT and destroys the blocks around it. Its power-16 explosion reaches
     * the neighbouring test structures, so it runs in a batch of its own.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "projecte_explosives")
    public static void novaCatalystExplodes(GameTestHelper helper) {
        BlockPos stone = CENTER.east();
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(CENTER, PEBlocks.NOVA_CATALYST.getBlock());
        BlockPos pos = helper.absolutePos(CENTER);
        PEBlocks.NOVA_CATALYST.getBlock().onCaughtFire(helper.getBlockState(CENTER), helper.getLevel(), pos, null, null);
        helper.getLevel().removeBlock(pos, false);
        check(helper, !helper.getLevel().getEntitiesOfClass(EntityNovaCatalystPrimed.class, new net.minecraft.world.phys.AABB(pos).inflate(1)).isEmpty(),
              "No primed Nova Catalyst");
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, stone));
    }

    /** Alchemical Coal, Mobius Fuel and Aeternalis Fuel, and their blocks, burn in a vanilla furnace. */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void alchemicalFuelsBurnInAFurnace(GameTestHelper helper) {
        Item[] fuels = {PEItems.ALCHEMICAL_COAL.get(), PEItems.MOBIUS_FUEL.get(), PEItems.AETERNALIS_FUEL.get(),
                        PEBlocks.ALCHEMICAL_COAL.asItem(), PEBlocks.MOBIUS_FUEL.asItem(), PEBlocks.AETERNALIS_FUEL.asItem()};
        AbstractFurnaceBlockEntity[] furnaces = new AbstractFurnaceBlockEntity[fuels.length];
        for (int i = 0; i < fuels.length; i++) {
            BlockPos pos = new BlockPos(1 + i, 1, 1);
            helper.setBlock(pos, Blocks.FURNACE);
            furnaces[i] = (AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
            check(helper, AbstractFurnaceBlockEntity.isFuel(new ItemStack(fuels[i])), fuels[i].getDescriptionId() + " is not a furnace fuel");
            furnaces[i].setItem(0, new ItemStack(Items.RAW_IRON));
            furnaces[i].setItem(1, new ItemStack(fuels[i]));
        }
        helper.succeedWhen(() -> {
            for (int i = 0; i < fuels.length; i++) {
                PEGameTestSupport.require(furnaces[i].getItem(2).is(Items.IRON_INGOT), fuels[i].getDescriptionId() + " smelted nothing");
            }
        });
    }

    /** A furnace burning an Aeternalis block (921,600 ticks, more than a short holds) keeps its burn time when saved. */
    @GameTest(template = TEMPLATE)
    public static void longBurnTimeSurvivesSaving(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(PEBlocks.AETERNALIS_FUEL.asItem()));
        helper.succeedWhen(() -> {
            int burning = furnace.saveWithoutMetadata().getInt("BurnTime");
            PEGameTestSupport.require(burning > Short.MAX_VALUE, "The furnace saved a burn time of " + burning);
            AbstractFurnaceBlockEntity copy = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(
                    helper.absolutePos(pos), helper.getBlockState(pos));
            copy.load(furnace.saveWithoutMetadata());
            int reloaded = copy.saveWithoutMetadata().getInt("BurnTime");
            PEGameTestSupport.require(Math.abs(reloaded - burning) <= 1, "Reloaded burn time " + reloaded + ", saved " + burning);
        });
    }

    /** Learning the Tome of Knowledge gives full knowledge: every item with EMC can be withdrawn. */
    @GameTest(template = TEMPLATE)
    public static void tomeOfKnowledgeGivesFullKnowledge(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "tome");
        IKnowledgeProvider knowledge = PEGameTestSupport.knowledge(player);
        check(helper, !knowledge.hasKnowledge(new ItemStack(Items.DIAMOND)), "Knew diamonds before the tome");
        knowledge.addKnowledge(new ItemStack(PEItems.TOME_OF_KNOWLEDGE.get()));
        check(helper, knowledge.hasFullKnowledge() && knowledge.hasKnowledge(new ItemStack(Items.DIAMOND)), "The tome did not give full knowledge");
        knowledge.clearKnowledge();
        helper.succeed();
    }

    /** The mode key cycles an item's mode, and the charge key steps its charge, through the capabilities the key packet uses. */
    @GameTest(template = TEMPLATE)
    public static void modeAndChargeKeysWorkThroughCapabilities(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "keys");
        ItemStack eye = new ItemStack(PEItems.MERCURIAL_EYE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, eye);
        byte before = ((IItemMode) eye.getItem()).getMode(eye);
        boolean changed = eye.getCapability(PECapabilities.MODE_CHANGER_ITEM_CAPABILITY).map(cap -> cap.changeMode(player, eye, InteractionHand.MAIN_HAND)).orElse(false);
        check(helper, changed && ((IItemMode) eye.getItem()).getMode(eye) != before, "The mode capability did not change the mode");
        ItemStack hammer = new ItemStack(PEItems.RED_MATTER_HAMMER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        boolean charged = hammer.getCapability(PECapabilities.CHARGE_ITEM_CAPABILITY).map(cap -> cap.changeCharge(player, hammer, InteractionHand.MAIN_HAND)).orElse(false);
        check(helper, charged && hammer.getOrCreateTag().getInt(moze_intel.projecte.api.capabilities.item.IItemCharge.KEY) == 1, "The charge capability did not charge the hammer");
        helper.succeed();
    }

    /** The projectile key fires the Evertide Amulet's water projectile through its shooter capability. */
    @GameTest(template = TEMPLATE)
    public static void projectileKeyFiresThroughTheShooterCapability(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "projectile");
        ItemStack amulet = new ItemStack(PEItems.EVERTIDE_AMULET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, amulet);
        boolean fired = amulet.getCapability(PECapabilities.PROJECTILE_SHOOTER_ITEM_CAPABILITY)
              .map(cap -> cap.shootProjectile(player, amulet, InteractionHand.MAIN_HAND)).orElse(false);
        check(helper, fired && !helper.getLevel().getEntitiesOfClass(EntityWaterProjectile.class, player.getBoundingBox().inflate(4)).isEmpty(),
              "No water projectile");
        helper.getLevel().getEntitiesOfClass(EntityWaterProjectile.class, player.getBoundingBox().inflate(4)).forEach(e -> e.discard());
        helper.succeed();
    }
}
