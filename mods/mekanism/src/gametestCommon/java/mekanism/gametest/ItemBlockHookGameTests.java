/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import mekanism.api.NBTConstants;
import mekanism.common.item.ItemConfigurator.ConfiguratorMode;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismItems;
import mekanism.common.resource.ore.OreType;
import mekanism.common.tile.TileEntityFluidTank;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla calls the item and block hooks Mekanism overrides, at the points Forge patches.
 */
public class ItemBlockHookGameTests {

    private static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void fluoriteOreDropsExperience(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, MekanismBlocks.ORES.get(OreType.FLUORITE).stone().getBlock());
        ServerPlayer player = survival(helper, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        check(player.gameMode.destroyBlock(helper.absolutePos(pos)), "The pickaxe did not break the fluorite ore");
        List<ExperienceOrb> orbs = helper.getLevel().getEntities(EntityType.EXPERIENCE_ORB, new AABB(helper.absolutePos(pos)).inflate(2), orb -> true);
        check(!orbs.isEmpty(), "Fluorite ore dropped no experience");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void charcoalBlockIsFurnaceFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
        ItemStack charcoal = new ItemStack(MekanismBlocks.CHARCOAL_BLOCK.asItem());
        check(AbstractFurnaceBlockEntity.isFuel(charcoal), "A charcoal block is not furnace fuel");
        check(AbstractFurnaceBlockEntity.isFuel(new ItemStack(Items.STICK)), "Sticks are no longer furnace fuel");
        check(!AbstractFurnaceBlockEntity.isFuel(new ItemStack(Items.DIRT)), "Dirt is furnace fuel");
        check(burnDuration(furnace, charcoal) == 16_000, "Charcoal block burn time was " + burnDuration(furnace, charcoal));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mekaToolEnchantmentsStayOffTheTable(GameTestHelper helper) {
        ItemStack tool = new ItemStack(MekanismItems.MEKA_TOOL.get());
        ListTag stored = new ListTag();
        stored.add(EnchantmentHelper.storeEnchantment(Registry.ENCHANTMENT.getKey(Enchantments.UNBREAKING), 3));
        ItemDataUtils.setList(tool, NBTConstants.ENCHANTMENTS, stored);
        check(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, tool) == 3, "Module unbreaking was not visible");
        List<EnchantmentInstance> offers = EnchantmentHelper.getAvailableEnchantmentResults(30, tool, false);
        check(offers.stream().noneMatch(offer -> offer.enchantment == Enchantments.UNBREAKING), "The enchanting table offered unbreaking on a Meka-Tool");
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        List<EnchantmentInstance> pickOffers = EnchantmentHelper.getAvailableEnchantmentResults(30, pickaxe, false);
        check(pickOffers.stream().anyMatch(offer -> offer.enchantment == Enchantments.UNBREAKING), "A diamond pickaxe lost unbreaking");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gearHooksOnVanillaMobs(GameTestHelper helper) {
        ServerPlayer player = survival(helper, BlockPos.ZERO);
        ItemStack elytra = new ItemStack(MekanismItems.HDPE_REINFORCED_ELYTRA.get());
        check(LivingEntity.getEquipmentSlotForItem(elytra) == EquipmentSlot.CHEST, "HDPE elytra did not equip on the chest");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(MekanismItems.FREE_RUNNERS.get()));
        check(PowderSnowBlock.canEntityWalkOnPowderSnow(player), "Free runners did not walk on powder snow");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        check(PowderSnowBlock.canEntityWalkOnPowderSnow(player), "Leather boots stopped walking on powder snow");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        check(!PowderSnowBlock.canEntityWalkOnPowderSnow(player), "Diamond boots walked on powder snow");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MekanismItems.MEKASUIT_HELMET.get()));
        check(PiglinAi.isWearingGold(player), "A Meka-Suit helmet did not pacify piglins");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
        check(PiglinAi.isWearingGold(player), "Golden boots stopped pacifying piglins");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        check(!PiglinAi.isWearingGold(player), "Iron boots pacified piglins");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void hdpeElytraStartsFlight(GameTestHelper helper) {
        ServerPlayer player = survival(helper, BlockPos.ZERO);
        player.setOnGround(false);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MekanismItems.HDPE_REINFORCED_ELYTRA.get()));
        check(player.tryToStartFallFlying(), "HDPE elytra did not start flight");
        player.stopFallFlying();
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        check(player.tryToStartFallFlying(), "Vanilla elytra did not start flight");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void redstoneHooks(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 1), Blocks.REDSTONE_WIRE);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.REDSTONE_WIRE);
        refreshWire(helper, new BlockPos(1, 2, 1));
        RedstoneSide climbed = helper.getBlockState(new BlockPos(1, 2, 1)).getValue(RedStoneWireBlock.EAST);
        check(climbed == RedstoneSide.UP, "Dust did not climb toward the dust on top of the block: " + climbed);

        helper.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 1), MekanismBlocks.LASER_AMPLIFIER.getBlock());
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.REDSTONE_WIRE);
        refreshWire(helper, new BlockPos(4, 2, 1));
        RedstoneSide connected = helper.getBlockState(new BlockPos(4, 2, 1)).getValue(RedStoneWireBlock.EAST);
        check(connected == RedstoneSide.SIDE, "Dust did not connect to the laser amplifier: " + connected);

        // Chest, solid block, comparator facing back through that block. Filling the chest calls
        // updateNeighbourForOutputSignal; the comparator stores its output two ticks later.
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.STONE);
        BlockPos chest = new BlockPos(1, 2, 4);
        BlockPos comparator = new BlockPos(3, 2, 4);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 2, 4), Blocks.STONE);
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        check(helper.getBlockEntity(comparator) instanceof ComparatorBlockEntity,
              "Comparator block entity missing, state " + helper.getBlockState(comparator));
        ((ChestBlockEntity) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIRT, 64));
        helper.succeedWhen(() -> {
            BlockEntity entity = helper.getBlockEntity(comparator);
            if (!(entity instanceof ComparatorBlockEntity comparatorEntity) || comparatorEntity.getOutputSignal() <= 0) {
                throw new GameTestAssertException("The comparator ignored the chest behind a solid block");
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fluidTankLightsFromLava(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.BASIC_FLUID_TANK.getBlock());
        BlockPos absolute = helper.absolutePos(pos);
        check(helper.getLevel().getLightEmission(absolute) == 0, "An empty fluid tank already emitted light");
        TestPlatform.fillBlockFluid((TileEntityFluidTank) helper.getBlockEntity(pos), Fluids.LAVA, 1_000);
        check(helper.getLevel().getLightEmission(absolute) > 0, "Lava in a fluid tank did not emit light");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void personalChestResistsExplosions(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.PERSONAL_CHEST.getBlock());
        BlockPos absolute = helper.absolutePos(pos);
        Explosion explosion = new Explosion(helper.getLevel(), null, absolute.getX(), absolute.getY(), absolute.getZ(), 4.0F);
        float resistance = new ExplosionDamageCalculator()
              .getBlockExplosionResistance(explosion, helper.getLevel(), absolute, helper.getBlockState(pos), Fluids.EMPTY.defaultFluidState())
              .orElse(-2.0F);
        float blockValue = helper.getBlockState(pos).getBlock().getExplosionResistance();
        // The chest hook reports -1. Vanilla takes the greater of that and the fluid, so the calculator reports 0
        // while the block property stays 16.
        check(blockValue == 16.0F, "Personal chest block resistance was " + blockValue);
        check(resistance == 0.0F, "Personal chest explosion resistance was " + resistance);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void refinedObsidianFormsAPortal(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), MekanismBlocks.REFINED_OBSIDIAN_BLOCK.getBlock());
            helper.setBlock(new BlockPos(x, 5, 1), MekanismBlocks.REFINED_OBSIDIAN_BLOCK.getBlock());
        }
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(1, y, 1), MekanismBlocks.REFINED_OBSIDIAN_BLOCK.getBlock());
            helper.setBlock(new BlockPos(4, y, 1), MekanismBlocks.REFINED_OBSIDIAN_BLOCK.getBlock());
        }
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.FIRE);
        helper.succeedWhen(() -> {
            boolean portal = false;
            for (int x = 2; x <= 3; x++) {
                for (int y = 2; y <= 4; y++) {
                    portal |= helper.getBlockState(new BlockPos(x, y, 1)).is(Blocks.NETHER_PORTAL);
                }
            }
            if (!portal) {
                throw new GameTestAssertException("Refined obsidian did not form a nether portal");
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void itemUseHooks(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(1, 1, 1);
        helper.setBlock(dirt, Blocks.DIRT);
        ServerPlayer player = survival(helper, dirt);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MekanismBlocks.CARDBOARD_BOX.asItem()));
        use(helper, player, dirt);
        check(helper.getBlockState(dirt).is(MekanismBlocks.CARDBOARD_BOX.getBlock()), "The cardboard box did not pick up the dirt");

        BlockPos lever = new BlockPos(3, 1, 1);
        helper.setBlock(lever, Blocks.LEVER);
        player.setShiftKeyDown(true);
        ItemStack wrench = new ItemStack(MekanismItems.CONFIGURATOR.get());
        ItemDataUtils.setInt(wrench, NBTConstants.STATE, ConfiguratorMode.WRENCH.ordinal());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        player.setItemInHand(InteractionHand.OFF_HAND, wrench.copy());
        use(helper, player, lever);
        check(helper.getBlockState(lever).getValue(LeverBlock.POWERED), "Sneaking with a wrench configurator did not use the lever");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void moduledMekaToolStaysInTheHotbar(GameTestHelper helper) {
        ServerPlayer player = survival(helper, BlockPos.ZERO);
        player.getInventory().selected = 0;
        ItemStack tool = new ItemStack(MekanismItems.MEKA_TOOL.get());
        ItemDataUtils.setCompound(tool, NBTConstants.MODULES, new CompoundTag());
        player.getInventory().setItem(0, tool);
        for (int slot = 1; slot < 9; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT));
        }
        check(player.getInventory().getSuitableHotbarSlot() == 1, "Pick replaced the moduled Meka-Tool, slot " + player.getInventory().getSuitableHotbarSlot());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void robitDropsItsInventory(GameTestHelper helper) {
        ItemStack robit = new ItemStack(MekanismItems.ROBIT.get());
        CompoundTag slot = new CompoundTag();
        slot.putByte(NBTConstants.SLOT, (byte) 0);
        slot.put(NBTConstants.ITEM, new ItemStack(Items.DIAMOND).save(new CompoundTag()));
        ListTag items = new ListTag();
        items.add(slot);
        ((IItemSustainedInventory) robit.getItem()).setInventory(items, robit);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), robit);
        helper.getLevel().addFreshEntity(entity);
        check(entity.hurt(DamageSource.CACTUS, 20), "The robit item ignored damage");
        List<ItemEntity> dropped = helper.getLevel().getEntities(EntityType.ITEM, new AABB(pos).inflate(2),
              item -> item.getItem().is(Items.DIAMOND));
        check(!dropped.isEmpty(), "Destroying the robit did not drop its diamond");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void freeRunnersHideAttributeTooltip(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        check(!mentionsArmor(new ItemStack(MekanismItems.FREE_RUNNERS.get()).getTooltipLines(player, TooltipFlag.Default.NORMAL)),
              "Free runners showed their attribute modifiers");
        check(mentionsArmor(new ItemStack(Items.LEATHER_BOOTS).getTooltipLines(player, TooltipFlag.Default.NORMAL)),
              "Leather boots hid their attribute modifiers");
        helper.succeed();
    }

    private static void refreshWire(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(absolute);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = absolute.relative(direction);
            state = state.updateShape(direction, helper.getLevel().getBlockState(neighbor), helper.getLevel(), absolute, neighbor);
        }
        helper.getLevel().setBlock(absolute, state, Block.UPDATE_CLIENTS);
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        // GameTestHelper.makeMockPlayer is a plain Player. Breaking and using blocks goes through ServerPlayerGameMode.
        // Forge's break event sends the player a block update, and an unconnected connection queues that packet.
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "hook-test"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        check(result.consumesAction(), "Item use did not act: " + result);
    }

    private static int burnDuration(AbstractFurnaceBlockEntity furnace, ItemStack stack) {
        try {
            Method method = AbstractFurnaceBlockEntity.class.getDeclaredMethod("getBurnDuration", ItemStack.class);
            method.setAccessible(true);
            return (int) method.invoke(furnace, stack);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not read furnace burn time: " + e.getMessage());
        }
    }

    private static boolean mentionsArmor(List<Component> lines) {
        for (Component line : lines) {
            String text = line.getString().toLowerCase(Locale.ROOT);
            if (text.contains("armor") || text.contains("attribute.modifier")) {
                return true;
            }
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
