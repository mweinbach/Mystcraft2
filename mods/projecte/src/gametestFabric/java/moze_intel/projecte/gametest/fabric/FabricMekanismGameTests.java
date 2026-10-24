/*
 * ProjectE 1.19.2 port - in-world tests, Fabric side.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest.fabric;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.gametest.PEGameTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import ports.projecte.lib.common.capabilities.AttachedCapabilities;

/**
 * ProjectE beside the Mekanism port's Fabric jar ({@code -PwithMekanism}). Both carry a Forge-API layer that saves
 * entity capabilities under the same {@code ForgeCaps} compound and both answer furnace fuel questions, so each must
 * leave the other's data and fuels alone. Mekanism is not on the compile classpath; its classes are reached by name.
 */
public class FabricMekanismGameTests {

    private static final String FORGE_CAPS = AttachedCapabilities.TAG_KEY;
    private static final String KNOWLEDGE_KEY = "projecte:knowledge";
    private static final String RADIATION_KEY = "mekanism:radiation";

    /** A player's ProjectE knowledge and EMC and its Mekanism radiation all survive a save and a load. */
    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void playerKeepsProjectEKnowledgeAndMekanismRadiation(GameTestHelper helper) {
        ServerPlayer saved = PEGameTestSupport.player(helper, "mek_caps_saved");
        IKnowledgeProvider knowledge = PEGameTestSupport.knowledge(saved);
        knowledge.addKnowledge(new ItemStack(Items.DIAMOND));
        knowledge.setEmc(BigInteger.valueOf(123_456));
        radiate(saved, 5.0);
        double radiation = radiation(saved);
        PEGameTestSupport.check(helper, radiation >= 5.0, "Mekanism radiation was not applied: " + radiation);

        CompoundTag tag = saved.saveWithoutId(new CompoundTag());
        CompoundTag caps = tag.getCompound(FORGE_CAPS);
        PEGameTestSupport.check(helper, caps.contains(KNOWLEDGE_KEY), "The save lost ProjectE's knowledge; ForgeCaps keys: " + caps.getAllKeys());
        PEGameTestSupport.check(helper, caps.contains(RADIATION_KEY), "The save lost Mekanism's radiation; ForgeCaps keys: " + caps.getAllKeys());

        ServerPlayer loaded = PEGameTestSupport.player(helper, "mek_caps_loaded");
        PEGameTestSupport.check(helper, radiation(loaded) < 5.0, "The fresh player already had radiation");
        loaded.load(tag);
        IKnowledgeProvider loadedKnowledge = PEGameTestSupport.knowledge(loaded);
        PEGameTestSupport.check(helper, loadedKnowledge.hasKnowledge(new ItemStack(Items.DIAMOND)), "Reloading lost the learned diamond");
        PEGameTestSupport.check(helper, loadedKnowledge.getEmc().equals(BigInteger.valueOf(123_456)), "Reloading changed EMC to " + loadedKnowledge.getEmc());
        PEGameTestSupport.check(helper, radiation(loaded) == radiation, "Reloading changed radiation from " + radiation + " to " + radiation(loaded));
        helper.succeed();
    }

    /**
     * Each layer adds its providers to a {@code ForgeCaps} compound the other already wrote, in either order, and reads
     * only its own keys back.
     */
    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void eitherSaveOrderKeepsTheOtherLayersCapabilities(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "mek_caps_order");
        PEGameTestSupport.knowledge(player).setEmc(BigInteger.valueOf(42));
        radiate(player, 2.0);
        AttachedCapabilities projecte = ((AttachedCapabilities.Holder) player).projecte$capabilities();
        Object mekanism = mekanismCapabilities(player);

        CompoundTag projecteFirst = foreignOwner();
        projecte.saveInto(projecteFirst);
        invoke(mekanism, "saveInto", projecteFirst);
        CompoundTag mekanismFirst = foreignOwner();
        invoke(mekanism, "saveInto", mekanismFirst);
        projecte.saveInto(mekanismFirst);
        for (CompoundTag owner : new CompoundTag[]{projecteFirst, mekanismFirst}) {
            CompoundTag caps = owner.getCompound(FORGE_CAPS);
            PEGameTestSupport.check(helper, caps.contains(KNOWLEDGE_KEY) && caps.contains(RADIATION_KEY) && caps.contains("othermod:data"),
                  "A layer replaced ForgeCaps instead of adding to it: " + caps.getAllKeys());
        }
        helper.succeed();
    }

    /**
     * In a vanilla furnace, Mekanism's charcoal block (an item {@code getBurnTime}, answered by Mekanism's furnace hook)
     * and ProjectE's fuels (Fabric's fuel registry) burn for their own times, and both start a smelt.
     */
    @GameTest(template = PEGameTestSupport.TEMPLATE, timeoutTicks = 300)
    public static void mekanismAndProjectEFuelsBurnInAVanillaFurnace(GameTestHelper helper) {
        Item charcoalBlock = Registry.ITEM.get(new ResourceLocation("mekanism", "block_charcoal"));
        PEGameTestSupport.check(helper, charcoalBlock != Items.AIR, "Mekanism's charcoal block is not registered");
        Map<Item, Integer> fuels = new LinkedHashMap<>();
        fuels.put(charcoalBlock, 16_000);
        fuels.put(Items.COAL, 1_600);
        fuels.put(PEItems.ALCHEMICAL_COAL.get(), 6_400);
        fuels.put(PEItems.MOBIUS_FUEL.get(), 25_600);
        fuels.put(PEItems.AETERNALIS_FUEL.get(), 102_400);
        fuels.put(PEBlocks.ALCHEMICAL_COAL.asItem(), 57_600);
        fuels.put(PEBlocks.MOBIUS_FUEL.asItem(), 230_400);
        fuels.put(PEBlocks.AETERNALIS_FUEL.asItem(), 921_600);

        BlockPos probePos = new BlockPos(1, 1, 1);
        helper.setBlock(probePos, Blocks.FURNACE);
        AbstractFurnaceBlockEntity probe = (AbstractFurnaceBlockEntity) helper.getBlockEntity(probePos);
        fuels.forEach((fuel, ticks) -> {
            ItemStack stack = new ItemStack(fuel);
            PEGameTestSupport.check(helper, AbstractFurnaceBlockEntity.isFuel(stack), fuel.getDescriptionId() + " is not a furnace fuel");
            int burn = burnDuration(probe, stack);
            PEGameTestSupport.check(helper, burn == ticks, fuel.getDescriptionId() + " burns for " + burn + " ticks, not " + ticks);
        });

        Item[] smelted = {charcoalBlock, PEItems.ALCHEMICAL_COAL.get(), PEBlocks.AETERNALIS_FUEL.asItem()};
        AbstractFurnaceBlockEntity[] furnaces = new AbstractFurnaceBlockEntity[smelted.length];
        for (int i = 0; i < smelted.length; i++) {
            BlockPos pos = new BlockPos(1 + i, 1, 3);
            helper.setBlock(pos, Blocks.FURNACE);
            furnaces[i] = (AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
            furnaces[i].setItem(0, new ItemStack(Items.RAW_IRON));
            furnaces[i].setItem(1, new ItemStack(smelted[i]));
        }
        helper.succeedWhen(() -> {
            for (int i = 0; i < smelted.length; i++) {
                PEGameTestSupport.require(furnaces[i].getItem(2).is(Items.IRON_INGOT), smelted[i].getDescriptionId() + " smelted nothing");
            }
        });
    }

    private static CompoundTag foreignOwner() {
        CompoundTag caps = new CompoundTag();
        caps.put("othermod:data", StringTag.valueOf("kept"));
        CompoundTag owner = new CompoundTag();
        owner.put(FORGE_CAPS, caps);
        return owner;
    }

    private static Object mekanismCapabilities(Entity entity) {
        try {
            return entity.getClass().getMethod("mekanism$capabilities").invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("The Mekanism port's capability layer is not loaded: " + e);
        }
    }

    private static Object radiationCapability(Entity entity) {
        try {
            Object capabilities = mekanismCapabilities(entity);
            Class<?> capabilityClass = Class.forName("ports.mekanism.lib.common.capabilities.Capability");
            Object radiationEntity = Class.forName("mekanism.common.capabilities.Capabilities").getField("RADIATION_ENTITY").get(null);
            Object lazy = capabilities.getClass().getMethod("getCapability", capabilityClass, Direction.class).invoke(capabilities, radiationEntity, null);
            Optional<?> resolved = (Optional<?>) lazy.getClass().getMethod("resolve").invoke(lazy);
            return resolved.orElseThrow(() -> new GameTestAssertException(entity.getScoreboardName() + " has no Mekanism radiation capability"));
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not reach Mekanism's radiation capability: " + e);
        }
    }

    private static void radiate(Entity entity, double magnitude) {
        try {
            Class<?> radiationEntity = Class.forName("mekanism.api.radiation.capability.IRadiationEntity");
            radiationEntity.getMethod("radiate", double.class).invoke(radiationCapability(entity), magnitude);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not radiate " + entity.getScoreboardName() + ": " + e);
        }
    }

    private static double radiation(Entity entity) {
        try {
            Class<?> radiationEntity = Class.forName("mekanism.api.radiation.capability.IRadiationEntity");
            return (double) radiationEntity.getMethod("getRadiation").invoke(radiationCapability(entity));
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not read " + entity.getScoreboardName() + "'s radiation: " + e);
        }
    }

    private static void invoke(Object capabilities, String method, CompoundTag owner) {
        try {
            capabilities.getClass().getMethod(method, CompoundTag.class).invoke(capabilities, owner);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not call Mekanism's AttachedCapabilities." + method + ": " + e);
        }
    }

    /** Vanilla's protected fuel lookup, which both layers' furnace hooks and Fabric's fuel registry answer. */
    private static int burnDuration(AbstractFurnaceBlockEntity furnace, ItemStack stack) {
        try {
            Method method = AbstractFurnaceBlockEntity.class.getDeclaredMethod("getBurnDuration", ItemStack.class);
            method.setAccessible(true);
            return (int) method.invoke(furnace, stack);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Could not read furnace burn time: " + e.getMessage());
        }
    }
}
