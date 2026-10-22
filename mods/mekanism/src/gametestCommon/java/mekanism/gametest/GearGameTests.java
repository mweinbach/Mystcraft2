/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.IHUDElement;
import mekanism.api.gear.ModuleData;
import mekanism.api.gear.config.ModuleEnumData;
import mekanism.api.math.FloatingLong;
import mekanism.api.radiation.capability.IRadiationShielding;
import mekanism.common.Mekanism;
import mekanism.common.base.KeySync;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.content.gear.Module;
import mekanism.common.content.gear.ModuleConfigItem;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.content.gear.mekatool.ModuleExcavationEscalationUnit.ExcavationMode;
import mekanism.common.inventory.container.ModuleTweakerContainer;
import mekanism.common.item.gear.ItemFreeRunners;
import mekanism.common.item.gear.ItemFreeRunners.FreeRunnerMode;
import mekanism.common.item.gear.ItemJetpack;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.common.item.gear.ItemScubaTank;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import mekanism.common.network.to_server.PacketRemoveModule;
import mekanism.common.network.to_server.PacketUpdateModuleSettings;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import mekanism.common.tile.TileEntityModificationStation;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/**
 * Jetpacks, scuba gear, free runners and the MekaSuit and Meka-Tool modules do the work upstream gives them.
 */
public class GearGameTests {

    private static final String TEMPLATE = "mekanism:empty";

    @GameTest(template = TEMPLATE)
    public static void jetpackLiftsAPlayer(GameTestHelper helper) {
        ServerPlayer pilot = survival(helper, new BlockPos(1, 3, 1), "jetpack");
        ItemStack jetpack = ChemicalUtil.getFilledVariant(new ItemStack(MekanismItems.JETPACK.get()), MekanismConfig.gear.jetpackMaxGas, MekanismGases.HYDROGEN);
        long filled = gas(jetpack);
        pilot.setItemSlot(EquipmentSlot.CHEST, jetpack);
        Mekanism.keyMap.add(pilot.getUUID(), KeySync.ASCEND);
        List<Component> hud = new ArrayList<>();
        ((IItemHUDProvider) jetpack.getItem()).addHUDStrings(hud, pilot, jetpack, EquipmentSlot.CHEST);
        check(hud.size() == 2, "The jetpack HUD wrote " + hud.size() + " lines");
        helper.succeedWhen(() -> {
            pilot.setOnGround(false);
            pilot.setDeltaMovement(0, 0, 0);
            pilot.doTick();
            check(pilot.getDeltaMovement().y > 0, "Jetpack thrust was " + pilot.getDeltaMovement().y);
            check(gas(pilot.getItemBySlot(EquipmentSlot.CHEST)) < filled, "The jetpack did not spend hydrogen");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void armoredJetpackHovers(GameTestHelper helper) {
        ServerPlayer pilot = survival(helper, new BlockPos(1, 3, 1), "armored-jetpack");
        ItemStack jetpack = ChemicalUtil.getFilledVariant(new ItemStack(MekanismItems.ARMORED_JETPACK.get()), MekanismConfig.gear.jetpackMaxGas, MekanismGases.HYDROGEN);
        ((ItemJetpack) jetpack.getItem()).setMode(jetpack, JetpackMode.HOVER);
        long filled = gas(jetpack);
        pilot.setItemSlot(EquipmentSlot.CHEST, jetpack);
        helper.succeedWhen(() -> {
            pilot.setOnGround(false);
            pilot.setShiftKeyDown(false);
            pilot.setDeltaMovement(0, -0.4, 0);
            pilot.doTick();
            double y = pilot.getDeltaMovement().y;
            check(y <= 0 && y > -0.4, "Hover left the velocity at " + y);
            check(gas(pilot.getItemBySlot(EquipmentSlot.CHEST)) < filled, "Hover did not spend hydrogen");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void scubaRefillsAir(GameTestHelper helper) {
        ServerPlayer diver = survival(helper, new BlockPos(1, 2, 1), "scuba");
        ItemStack tank = ChemicalUtil.getFilledVariant(new ItemStack(MekanismItems.SCUBA_TANK.get()), MekanismConfig.gear.scubaMaxGas, MekanismGases.OXYGEN);
        ((ItemScubaTank) tank.getItem()).setFlowing(tank, true);
        long filled = gas(tank);
        diver.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MekanismItems.SCUBA_MASK.get()));
        diver.setItemSlot(EquipmentSlot.CHEST, tank);
        diver.setAirSupply(0);
        List<Component> hud = new ArrayList<>();
        ((IItemHUDProvider) tank.getItem()).addHUDStrings(hud, diver, tank, EquipmentSlot.CHEST);
        check(hud.size() == 2, "The scuba HUD wrote " + hud.size() + " lines");

        ServerPlayer bare = survival(helper, new BlockPos(4, 2, 1), "bare-air");
        bare.setAirSupply(0);
        bare.doTick();
        check(bare.getAirSupply() < bare.getMaxAirSupply(), "A bare player refilled to " + bare.getAirSupply());
        helper.succeedWhen(() -> {
            diver.doTick();
            check(diver.getAirSupply() == diver.getMaxAirSupply(), "Scuba left the air at " + diver.getAirSupply());
            check(gas(diver.getItemBySlot(EquipmentSlot.CHEST)) < filled, "The tank did not spend oxygen");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void freeRunnersCancelFallDamageAndStep(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        ServerPlayer runner = survival(helper, new BlockPos(1, 2, 1), "free-runners");
        ItemStack boots = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.FREE_RUNNERS.get()), MekanismConfig.gear.freeRunnerMaxEnergy);
        runner.setItemSlot(EquipmentSlot.FEET, boots);
        clearSpawnProtection(runner);
        runner.setHealth(20);
        runner.invulnerableTime = 0;
        runner.causeFallDamage(10, 1, DamageSource.FALL);
        List<Component> hud = new ArrayList<>();
        ((IItemHUDProvider) boots.getItem()).addHUDStrings(hud, runner, boots, EquipmentSlot.FEET);
        check(hud.size() == 2, "The free runner HUD wrote " + hud.size() + " lines");

        ServerPlayer safety = survival(helper, new BlockPos(3, 2, 1), "safety-runners");
        ItemStack safetyBoots = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.FREE_RUNNERS.get()), MekanismConfig.gear.freeRunnerMaxEnergy);
        ((ItemFreeRunners) safetyBoots.getItem()).setMode(safetyBoots, FreeRunnerMode.SAFETY);
        safety.setItemSlot(EquipmentSlot.FEET, safetyBoots);
        clearSpawnProtection(safety);
        safety.setHealth(20);
        safety.invulnerableTime = 0;
        safety.causeFallDamage(10, 1, DamageSource.FALL);

        ServerPlayer disabled = survival(helper, new BlockPos(5, 2, 1), "disabled-runners");
        ItemStack disabledBoots = StorageUtils.getFilledEnergyVariant(new ItemStack(MekanismItems.FREE_RUNNERS.get()), MekanismConfig.gear.freeRunnerMaxEnergy);
        ((ItemFreeRunners) disabledBoots.getItem()).setMode(disabledBoots, FreeRunnerMode.DISABLED);
        disabled.setItemSlot(EquipmentSlot.FEET, disabledBoots);
        clearSpawnProtection(disabled);
        disabled.setHealth(20);
        disabled.invulnerableTime = 0;
        disabled.causeFallDamage(10, 1, DamageSource.FALL);
        helper.succeedWhen(() -> {
            runner.setShiftKeyDown(false);
            runner.doTick();
            safety.doTick();
            disabled.doTick();
            check(runner.getHealth() == 20, "Free runners left the player at " + runner.getHealth());
            check(step(runner) >= 0.5, "Free runners added " + step(runner) + " step height");
            check(safety.getHealth() == 20, "Safety mode left the player at " + safety.getHealth());
            check(step(safety) < 0.01, "Safety mode added step height " + step(safety));
            check(disabled.getHealth() < 20, "Disabled free runners cancelled the fall, health " + disabled.getHealth());
            check(step(disabled) < 0.01, "Disabled free runners added step height " + step(disabled));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void installedModuleChangesStepAssist(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.MODIFICATION_STATION.getBlock());
        TileEntityModificationStation station = (TileEntityModificationStation) helper.getBlockEntity(pos);
        station.getInventorySlots(null).get(0).setStack(new ItemStack(MekanismItems.MODULE_HYDRAULIC_PROPULSION.get()));
        station.getInventorySlots(null).get(1).setStack(new ItemStack(MekanismItems.MEKASUIT_BOOTS.get()));
        check(TestPlatform.insertEnergy(station, side(station, RelativeSide.BACK), 40_000) > 0, "The modification station accepted no energy");
        ServerPlayer player = survival(helper, new BlockPos(3, 2, 1), "module-install");
        boolean[] sawBoost = {false};
        helper.succeedWhen(() -> {
            ItemStack boots = station.getInventorySlots(null).get(1).getStack();
            boolean installed = hasModule(boots, MekanismModules.HYDRAULIC_PROPULSION_UNIT.get());
            if (installed && !sawBoost[0]) {
                player.setShiftKeyDown(false);
                player.setItemSlot(EquipmentSlot.FEET, boots.copy());
                player.doTick();
                check(step(player) >= 0.5, "Hydraulic boots added " + step(player) + " step height");
                sawBoost[0] = true;
            }
            if (sawBoost[0] && installed) {
                new PacketRemoveModule(helper.absolutePos(pos), MekanismModules.HYDRAULIC_PROPULSION_UNIT.get()).handle(context(player));
            }
            ItemStack after = station.getInventorySlots(null).get(1).getStack();
            check(sawBoost[0] && !hasModule(after, MekanismModules.HYDRAULIC_PROPULSION_UNIT.get()), "The boots still have the hydraulic module");
            player.setItemSlot(EquipmentSlot.FEET, after.copy());
            player.doTick();
            check(step(player) < 0.01, "Removing the module left step height " + step(player));
        });
    }

    @GameTest(template = TEMPLATE)
    public static void moduleTweakerChangesExcavation(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "tweaker");
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.EXCAVATION_ESCALATION_UNIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        check(ModuleTweakerContainer.hasTweakableItem(player), "The tweaker did not see the Meka-Tool");
        ModuleTweakerContainer container = new ModuleTweakerContainer(1, player.getInventory());
        check(!container.slots.isEmpty(), "The tweaker opened with no slots");
        for (Slot slot : container.slots) {
            check(!slot.mayPickup(player), "The tweaker let a slot be taken");
            check(!slot.mayPlace(new ItemStack(Items.DIRT)), "The tweaker let a slot accept dirt");
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        ItemStack bare = charge(new ItemStack(MekanismItems.MEKA_TOOL.get()));
        near(bare.getDestroySpeed(stone), MekanismConfig.gear.mekaToolBaseEfficiency.get(), "A bare Meka-Tool mined at an unexpected speed");
        near(tool.getDestroySpeed(stone), 16, "Excavation normal mined at an unexpected speed");
        Module<?> module = ModuleHelper.INSTANCE.load(tool, MekanismModules.EXCAVATION_ESCALATION_UNIT.get());
        int index = configIndex(module, "excavation_mode");
        PacketUpdateModuleSettings.create(0, MekanismModules.EXCAVATION_ESCALATION_UNIT.get(), index,
              new ModuleEnumData<>(ExcavationMode.OFF, 3)).handle(context(player));
        ItemStack tuned = player.getMainHandItem();
        helper.succeedWhen(() -> near(tuned.getDestroySpeed(stone), 0, "Excavation off still mined"));
    }

    @GameTest(template = TEMPLATE)
    public static void mekaSuitJetpackLifts(GameTestHelper helper) {
        ServerPlayer pilot = survival(helper, new BlockPos(1, 3, 1), "suit-jetpack");
        ItemStack chest = gear(new ItemStack(MekanismItems.MEKASUIT_BODYARMOR.get()), MekanismModules.JETPACK_UNIT.get());
        fillHydrogen(chest, 1_000);
        long filled = gas(chest);
        pilot.setItemSlot(EquipmentSlot.CHEST, chest);
        Mekanism.keyMap.add(pilot.getUUID(), KeySync.ASCEND);
        List<IHUDElement> elements = ((IModuleContainerItem) chest.getItem()).getHUDElements(pilot, chest);
        check(!elements.isEmpty(), "The suit jetpack added no HUD element");
        helper.succeedWhen(() -> {
            pilot.setOnGround(false);
            pilot.setDeltaMovement(0, 0, 0);
            pilot.doTick();
            check(pilot.getDeltaMovement().y > 0, "The suit jetpack thrust was " + pilot.getDeltaMovement().y);
            check(gas(pilot.getItemBySlot(EquipmentSlot.CHEST)) < filled, "The suit jetpack did not spend hydrogen");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void gravitationalModulationGrantsFlight(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "grav");
        ItemStack chest = gear(new ItemStack(MekanismItems.MEKASUIT_BODYARMOR.get()), MekanismModules.GRAVITATIONAL_MODULATING_UNIT.get());
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        FloatingLong before = energy(chest);
        helper.succeedWhen(() -> {
            player.doTick();
            check(player.getAbilities().mayfly, "Gravitational modulation did not grant flight");
            if (!player.getAbilities().flying) {
                player.getAbilities().flying = true;
                player.doTick();
            }
            check(energy(player.getItemBySlot(EquipmentSlot.CHEST)).smallerThan(before), "Flying did not spend energy");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void hydraulicJumpBoosts(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "jump");
        ItemStack boots = gear(new ItemStack(MekanismItems.MEKASUIT_BOOTS.get()), MekanismModules.HYDRAULIC_PROPULSION_UNIT.get());
        player.setItemSlot(EquipmentSlot.FEET, boots);
        Mekanism.keyMap.add(player.getUUID(), KeySync.BOOST);
        FloatingLong before = energy(boots);
        ServerPlayer bare = survival(helper, new BlockPos(3, 2, 1), "bare-jump");
        jump(bare);
        double bareJump = bare.getDeltaMovement().y;
        helper.succeedWhen(() -> {
            player.setDeltaMovement(0, 0, 0);
            jump(player);
            check(player.getDeltaMovement().y > bareJump + 0.4, "Hydraulic jump was " + player.getDeltaMovement().y + " against " + bareJump);
            check(energy(player.getItemBySlot(EquipmentSlot.FEET)).smallerThan(before), "The jump did not spend energy");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void locomotiveBoostMovesThePlayer(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "sprint");
        ItemStack legs = gear(new ItemStack(MekanismItems.MEKASUIT_PANTS.get()), MekanismModules.LOCOMOTIVE_BOOSTING_UNIT.get());
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setOnGround(true);
        player.setSprinting(true);
        FloatingLong before = energy(legs);
        helper.succeedWhen(() -> {
            player.setOnGround(true);
            player.setSprinting(true);
            player.setDeltaMovement(0, 0, 0);
            player.doTick();
            check(player.getDeltaMovement().horizontalDistanceSqr() > 0, "Locomotive boost left the player still");
            check(energy(player.getItemBySlot(EquipmentSlot.LEGS)).smallerThan(before), "Sprinting did not spend energy");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void magneticAttractionPullsAnItem(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(2, 2, 2), "magnet");
        ItemStack legs = gear(new ItemStack(MekanismItems.MEKASUIT_PANTS.get()), MekanismModules.MAGNETIC_ATTRACTION_UNIT.get());
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        BlockPos itemPos = helper.absolutePos(new BlockPos(4, 2, 2));
        ItemEntity dropped = new ItemEntity(helper.getLevel(), itemPos.getX() + 0.5, itemPos.getY(), itemPos.getZ() + 0.5, new ItemStack(Items.IRON_INGOT));
        dropped.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(dropped);
        FloatingLong before = energy(legs);
        helper.succeedWhen(() -> {
            player.doTick();
            check(dropped.getDeltaMovement().x < 0, "The ingot moved " + dropped.getDeltaMovement());
            check(energy(player.getItemBySlot(EquipmentSlot.LEGS)).smallerThan(before), "Attraction did not spend energy");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void nutritionalInjectionFeeds(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "food");
        ItemStack helmet = gear(new ItemStack(MekanismItems.MEKASUIT_HELMET.get()), MekanismModules.NUTRITIONAL_INJECTION_UNIT.get());
        int accepted = FluidUtil.getFluidHandler(helmet).orElseThrow(IllegalStateException::new)
              .fill(MekanismFluids.NUTRITIONAL_PASTE.getFluidStack(1_000), FluidAction.EXECUTE);
        check(accepted > 0, "The helmet accepted no nutritional paste");
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.getFoodData().setFoodLevel(10);
        helper.succeedWhen(() -> {
            player.doTick();
            check(player.getFoodData().getFoodLevel() > 10, "Nutritional injection left food at " + player.getFoodData().getFoodLevel());
        });
    }

    @GameTest(template = TEMPLATE)
    public static void inhalationPurificationSpeedsPoison(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "purify");
        ItemStack helmet = gear(new ItemStack(MekanismItems.MEKASUIT_HELMET.get()), MekanismModules.INHALATION_PURIFICATION_UNIT.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        helper.succeedWhen(() -> {
            player.doTick();
            MobEffectInstance poison = player.getEffect(MobEffects.POISON);
            check(poison != null && poison.getDuration() <= 191, "Poison duration was " + (poison == null ? "gone" : poison.getDuration()));
        });
    }

    @GameTest(template = TEMPLATE)
    public static void visionEnhancementSpendsEnergy(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "vision");
        ItemStack helmet = gear(new ItemStack(MekanismItems.MEKASUIT_HELMET.get()), MekanismModules.VISION_ENHANCEMENT_UNIT.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        Module<?> module = ModuleHelper.INSTANCE.load(helmet, MekanismModules.VISION_ENHANCEMENT_UNIT.get());
        check(!module.isEnabled(), "Vision enhancement started enabled");
        module.toggleEnabled(player, Component.literal("vision"));
        check(module.isEnabled(), "Vision enhancement stayed disabled");
        FloatingLong before = energy(helmet);
        List<IHUDElement> elements = ((IModuleContainerItem) helmet.getItem()).getHUDElements(player, helmet);
        check(!elements.isEmpty(), "Vision enhancement added no HUD element");
        helper.succeedWhen(() -> {
            player.doTick();
            check(energy(player.getItemBySlot(EquipmentSlot.HEAD)).smallerThan(before), "Vision enhancement did not spend energy");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void electrolyticBreathingSpendsEnergyInWater(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 2, 2);
        for (int y = 1; y <= 5; y++) {
            helper.setBlock(feet.offset(0, y - 2, 0), Blocks.WATER);
        }
        ServerPlayer player = survival(helper, feet, "electrolytic");
        ItemStack helmet = gear(new ItemStack(MekanismItems.MEKASUIT_HELMET.get()), MekanismModules.ELECTROLYTIC_BREATHING_UNIT.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setAirSupply(0);
        FloatingLong before = energy(helmet);
        helper.succeedWhen(() -> {
            player.setAirSupply(Math.min(player.getAirSupply(), player.getMaxAirSupply() - 20));
            player.doTick();
            check(energy(player.getItemBySlot(EquipmentSlot.HEAD)).smallerThan(before), "Electrolytic breathing did not spend energy");
            check(player.getAirSupply() > 0, "Electrolytic breathing left the player without air");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void radiationShieldingCoversTheChest(GameTestHelper helper) {
        ItemStack chest = gear(new ItemStack(MekanismItems.MEKASUIT_BODYARMOR.get()), MekanismModules.RADIATION_SHIELDING_UNIT.get());
        IRadiationShielding shielding = chest.getCapability(Capabilities.RADIATION_SHIELDING).resolve().orElse(null);
        check(shielding != null, "The chest has no radiation shielding capability");
        near(shielding.getRadiationShielding(), 0.4, "Chest shielding was " + shielding.getRadiationShielding());
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "dosimeter");
        ItemStack helmet = gear(new ItemStack(MekanismItems.MEKASUIT_HELMET.get()), MekanismModules.DOSIMETER_UNIT.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        List<IHUDElement> elements = ((IModuleContainerItem) helmet.getItem()).getHUDElements(player, helmet);
        check(!elements.isEmpty(), "The dosimeter added no HUD element");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void enchantmentModulesApplyTheirEnchantment(GameTestHelper helper) {
        ItemStack boots = gear(new ItemStack(MekanismItems.MEKASUIT_BOOTS.get()), MekanismModules.MOTORIZED_SERVO_UNIT.get(), MekanismModules.FROST_WALKER_UNIT.get());
        check(boots.getEnchantmentLevel(Enchantments.SWIFT_SNEAK) == 1, "Motorized servo applied swift sneak " + boots.getEnchantmentLevel(Enchantments.SWIFT_SNEAK));
        check(boots.getEnchantmentLevel(Enchantments.FROST_WALKER) == 1, "Frost walker was " + boots.getEnchantmentLevel(Enchantments.FROST_WALKER));
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.SILK_TOUCH_UNIT.get());
        check(tool.getEnchantmentLevel(Enchantments.SILK_TOUCH) == 1, "Silk touch was " + tool.getEnchantmentLevel(Enchantments.SILK_TOUCH));
        ItemStack fortune = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.FORTUNE_UNIT.get());
        check(fortune.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE) == 1, "Fortune was " + fortune.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void elytraUnitCanFly(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "elytra");
        ItemStack chest = gear(new ItemStack(MekanismItems.MEKASUIT_BODYARMOR.get()), MekanismModules.ELYTRA_UNIT.get());
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        ItemMekaSuitArmor armor = (ItemMekaSuitArmor) chest.getItem();
        check(armor.canElytraFly(chest, player), "The elytra unit refused to fly");
        FloatingLong before = energy(chest);
        check(armor.elytraFlightTick(chest, player, 19), "An elytra tick failed");
        check(energy(chest).smallerThan(before), "Elytra flight did not spend energy");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void chargeDistributionEqualizesArmor(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "charge");
        ItemStack helmet = new ItemStack(MekanismItems.MEKASUIT_HELMET.get());
        ItemStack chest = gear(new ItemStack(MekanismItems.MEKASUIT_BODYARMOR.get()), MekanismModules.CHARGE_DISTRIBUTION_UNIT.get());
        ItemStack legs = new ItemStack(MekanismItems.MEKASUIT_PANTS.get());
        ItemStack boots = new ItemStack(MekanismItems.MEKASUIT_BOOTS.get());
        setEnergy(chest, FloatingLong.create(100_000));
        Module<?> module = ModuleHelper.INSTANCE.load(chest, MekanismModules.CHARGE_DISTRIBUTION_UNIT.get());
        for (ModuleConfigItem<?> item : module.getConfigItems()) {
            if (item.getName().equals("charge_inventory")) {
                setBoolean(item, true);
            }
        }
        ItemStack tablet = new ItemStack(MekanismItems.ENERGY_TABLET.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        helper.succeedWhen(() -> {
            player.doTick();
            FloatingLong head = energy(player.getItemBySlot(EquipmentSlot.HEAD));
            FloatingLong body = energy(player.getItemBySlot(EquipmentSlot.CHEST));
            FloatingLong pants = energy(player.getItemBySlot(EquipmentSlot.LEGS));
            FloatingLong feet = energy(player.getItemBySlot(EquipmentSlot.FEET));
            check(body.smallerThan(FloatingLong.create(100_000)), "The chest kept " + body);
            check(head.greaterThan(FloatingLong.ZERO) && pants.greaterThan(FloatingLong.ZERO) && feet.greaterThan(FloatingLong.ZERO),
                  "Suit charge was " + head + ", " + body + ", " + pants + ", " + feet);
            check(energy(player.getMainHandItem()).greaterThan(FloatingLong.ZERO), "The energy tablet stayed empty");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void attackAmplificationAddsDamage(GameTestHelper helper) {
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.ATTACK_AMPLIFICATION_UNIT.get());
        double damage = 0;
        for (AttributeModifier modifier : tool.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            damage += modifier.getAmount();
        }
        near(damage, MekanismConfig.gear.mekaToolBaseDamage.get() + 8, "Attack amplification dealt " + damage);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void farmingTillsOneGrassBlock(GameTestHelper helper) {
        BlockPos grass = new BlockPos(2, 1, 2);
        BlockPos beside = new BlockPos(3, 1, 2);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        helper.setBlock(beside, Blocks.GRASS_BLOCK);
        ServerPlayer player = survival(helper, new BlockPos(2, 2, 2), "farmer");
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.FARMING_UNIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        BlockPos absolute = helper.absolutePos(grass);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), tool, InteractionHand.MAIN_HAND, hit);
        check(result.consumesAction(), "Farming returned " + result);
        check(helper.getBlockState(grass).is(Blocks.FARMLAND), "The grass became " + helper.getBlockState(grass));
        check(helper.getBlockState(beside).is(Blocks.GRASS_BLOCK), "Farming also tilled the neighbour");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shearingShearsEntitiesAndCarvesPumpkins(GameTestHelper helper) {
        ServerPlayer player = survival(helper, new BlockPos(2, 2, 2), "shear");
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.SHEARING_UNIT.get());
        FloatingLong before = energy(tool);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);

        Sheep sheep = helper.spawn(EntityType.SHEEP, 2, 2, 2);
        sheep.setNoAi(true);
        InteractionResult sheared = player.interactOn(sheep, InteractionHand.MAIN_HAND);
        check(sheared.consumesAction(), "Shearing the sheep returned " + sheared);
        check(sheep.isSheared(), "The sheep still has wool");

        MushroomCow mooshroom = helper.spawn(EntityType.MOOSHROOM, 3, 2, 2);
        mooshroom.setNoAi(true);
        player.moveTo(helper.absolutePos(new BlockPos(3, 2, 2)).getX() + 0.5, helper.absolutePos(new BlockPos(3, 2, 2)).getY(),
              helper.absolutePos(new BlockPos(3, 2, 2)).getZ() + 0.5, 0, 0);
        InteractionResult shaved = player.interactOn(mooshroom, InteractionHand.MAIN_HAND);
        check(shaved.consumesAction(), "Shearing the mooshroom returned " + shaved);
        check(!mooshroom.isAlive(), "The mooshroom was not sheared");

        SnowGolem golem = helper.spawn(EntityType.SNOW_GOLEM, 4, 2, 2);
        golem.setNoAi(true);
        BlockPos golemPos = helper.absolutePos(new BlockPos(4, 2, 2));
        player.moveTo(golemPos.getX() + 0.5, golemPos.getY(), golemPos.getZ() + 0.5, 0, 0);
        InteractionResult pumpkinOff = player.interactOn(golem, InteractionHand.MAIN_HAND);
        check(pumpkinOff.consumesAction(), "Shearing the snow golem returned " + pumpkinOff);
        check(!golem.hasPumpkin(), "The snow golem kept its pumpkin");

        BlockPos pumpkin = new BlockPos(2, 2, 4);
        helper.setBlock(pumpkin, Blocks.PUMPKIN);
        BlockPos pumpkinAbsolute = helper.absolutePos(pumpkin);
        player.moveTo(pumpkinAbsolute.getX() + 0.5, pumpkinAbsolute.getY(), pumpkinAbsolute.getZ() - 1.5, 0, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pumpkinAbsolute), Direction.NORTH, pumpkinAbsolute, false);
        InteractionResult carved = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        check(carved.consumesAction(), "Carving the pumpkin returned " + carved);
        check(helper.getBlockState(pumpkin).is(Blocks.CARVED_PUMPKIN), "The pumpkin became " + helper.getBlockState(pumpkin));
        check(energy(player.getMainHandItem()).smallerThan(before), "Shearing did not spend energy");
        helper.succeedWhen(() -> {
            check(dropped(helper, Items.WHITE_WOOL) >= 1, "The sheep dropped no wool");
            check(dropped(helper, Items.RED_MUSHROOM) >= 1, "The mooshroom dropped no mushrooms");
            check(dropped(helper, Items.CARVED_PUMPKIN) >= 1, "The snow golem dropped no pumpkin");
            check(dropped(helper, Items.PUMPKIN_SEEDS) >= 4, "The carved pumpkin dropped " + dropped(helper, Items.PUMPKIN_SEEDS) + " seeds");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void veinMiningMinesAnOreVein(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 2, 2);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.IRON_ORE);
        helper.setBlock(center, Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        ServerPlayer player = survival(helper, new BlockPos(3, 4, 2), "vein");
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.VEIN_MINING_UNIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        check(player.gameMode.destroyBlock(helper.absolutePos(center)), "The Meka-Tool did not break the ore");
        helper.succeedWhen(() -> {
            check(helper.getBlockState(center).isAir(), "The mined ore is still there");
            check(helper.getBlockState(new BlockPos(2, 2, 2)).isAir() && helper.getBlockState(new BlockPos(4, 2, 2)).isAir(), "The vein was left standing");
            check(helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.STONE), "Vein mining broke the stone beside the vein");
            check(dropped(helper, Items.RAW_IRON) >= 3, "The vein dropped " + dropped(helper, Items.RAW_IRON) + " raw iron");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void blastingBreaksAPlane(GameTestHelper helper) {
        BlockPos center = new BlockPos(3, 1, 3);
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.STONE);
        ServerPlayer player = survival(helper, new BlockPos(3, 2, 3), "blast");
        player.setXRot(90);
        player.setYRot(0);
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.BLASTING_UNIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        check(player.gameMode.destroyBlock(helper.absolutePos(center)), "The Meka-Tool did not break the stone");
        helper.succeedWhen(() -> {
            for (int x = 2; x <= 4; x++) {
                for (int z = 2; z <= 4; z++) {
                    check(helper.getBlockState(new BlockPos(x, 1, z)).isAir(), "Blasting left " + helper.getBlockState(new BlockPos(x, 1, z)) + " at " + x + "," + z);
                }
            }
            check(helper.getBlockState(new BlockPos(3, 1, 5)).is(Blocks.STONE), "Blasting broke stone outside the plane");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void teleportationMovesThePlayer(GameTestHelper helper) {
        helper.setBlock(new BlockPos(6, 3, 1), Blocks.STONE);
        ServerPlayer player = survival(helper, new BlockPos(1, 2, 1), "teleport");
        player.setYRot(-90);
        player.setXRot(0);
        ItemStack tool = gear(new ItemStack(MekanismItems.MEKA_TOOL.get()), MekanismModules.TELEPORTATION_UNIT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        double started = player.getX();
        InteractionResultHolder<ItemStack> result = ((ItemMekaTool) tool.getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(result.getResult().consumesAction(), "Teleport returned " + result.getResult());
        check(player.getX() > started + 3, "Teleport left the player at " + player.position());
        helper.succeed();
    }

    private static ItemStack gear(ItemStack stack, ModuleData<?>... modules) {
        IModuleContainerItem container = (IModuleContainerItem) stack.getItem();
        for (ModuleData<?> module : modules) {
            container.addModule(stack, module);
        }
        return charge(stack);
    }

    private static ItemStack charge(ItemStack stack) {
        IEnergyContainer container = StorageUtils.getEnergyContainer(stack, 0);
        check(container != null, stack.getItem() + " has no energy container");
        container.insert(container.getMaxEnergy(), Action.EXECUTE, AutomationType.MANUAL);
        return stack;
    }

    private static void setEnergy(ItemStack stack, FloatingLong amount) {
        IEnergyContainer container = StorageUtils.getEnergyContainer(stack, 0);
        check(container != null, stack.getItem() + " has no energy container");
        container.setEnergy(amount);
    }

    private static void fillHydrogen(ItemStack stack, long amount) {
        IGasHandler handler = stack.getCapability(Capabilities.GAS_HANDLER).resolve().orElse(null);
        check(handler != null, "The stack has no gas tank");
        long left = handler.insertChemical(MekanismGases.HYDROGEN.getStack(amount), Action.EXECUTE).getAmount();
        check(left < amount, "Hydrogen was rejected, " + left + " left");
    }

    private static boolean hasModule(ItemStack stack, ModuleData<?> module) {
        return stack.getItem() instanceof IModuleContainerItem container && container.hasModule(stack, module);
    }

    private static int configIndex(Module<?> module, String name) {
        List<ModuleConfigItem<?>> items = module.getConfigItems();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getName().equals(name)) {
                return index;
            }
        }
        throw new GameTestAssertException("No module config named " + name);
    }

    @SuppressWarnings("unchecked")
    private static void setBoolean(ModuleConfigItem<?> item, boolean value) {
        ((ModuleConfigItem<Boolean>) item).set(value);
    }

    private static void jump(Player player) {
        try {
            Method method = LivingEntity.class.getDeclaredMethod("jumpFromGround");
            method.setAccessible(true);
            method.invoke(player);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not jump: " + e.getMessage());
        }
    }

    private static double step(Player player) {
        return player.getAttributeValue(ForgeMod.STEP_HEIGHT_ADDITION.get());
    }

    private static long gas(ItemStack stack) {
        return stack.getCapability(Capabilities.GAS_HANDLER).resolve()
              .map(handler -> handler.getTanks() > 0 ? handler.getChemicalInTank(0).getAmount() : 0L)
              .orElse(0L);
    }

    private static FloatingLong energy(ItemStack stack) {
        IEnergyContainer container = StorageUtils.getEnergyContainer(stack, 0);
        check(container != null, "The stack has no energy container");
        return container.getEnergy().copy();
    }

    private static int dropped(GameTestHelper helper, Item item) {
        int count = 0;
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        AABB bounds = new AABB(origin).expandTowards(8, 8, 8).inflate(2);
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds)) {
            ItemStack stack = entity.getItem();
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static Direction side(TileEntityMekanism tile, RelativeSide relative) {
        return relative.getDirection(tile.getDirection());
    }

    private static NetworkEvent.Context context(ServerPlayer sender) {
        Class<?> type = NetworkEvent.Context.class;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            try {
                if (params.length == 2 && params[0] == NetworkDirection.class && params[1] == ServerPlayer.class) {
                    constructor.setAccessible(true);
                    return (NetworkEvent.Context) constructor.newInstance(NetworkDirection.PLAY_TO_SERVER, sender);
                }
                if (params.length == 3 && params[0] == Connection.class && params[1] == NetworkDirection.class && params[2] == int.class) {
                    constructor.setAccessible(true);
                    return (NetworkEvent.Context) constructor.newInstance(sender.connection.connection, NetworkDirection.PLAY_TO_SERVER, 0);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new GameTestAssertException("Could not build a packet context: " + e.getMessage());
            }
        }
        throw new GameTestAssertException("No packet context constructor");
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.getAbilities().invulnerable = false;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, player.getYRot(), player.getXRot());
        return player;
    }

    private static void clearSpawnProtection(ServerPlayer player) {
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not clear spawn protection: " + e.getMessage());
        }
    }

    private static void near(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.001D) {
            throw new GameTestAssertException(message + " (expected " + expected + ", was " + actual + ")");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
