/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.ArrayList;
import java.util.List;
import mekanism.common.config.value.CachedDoubleValue;
import mekanism.tools.common.MekanismTools;
import mekanism.tools.common.config.MekanismToolsConfig;
import mekanism.tools.common.config.ToolsConfig;
import mekanism.tools.common.config.ToolsConfig.ArmorSpawnChanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * Mekanism Tools rolls {@code mobArmorSpawnRate} when a zombie is finalized, then one material, then each piece.
 * A certain chance fills the sword and the four armor slots. A zero chance leaves them free of Mekanism gear.
 */
public class ToolsMobArmorGameTests {

    public static final String TEMPLATE = "mekanism:empty";
    private static final String[] MATERIALS = {
          "bronze", "lapis_lazuli", "osmium", "refined_glowstone", "refined_obsidian", "steel"
    };

    @GameTest(template = TEMPLATE)
    public static void zombieSpawnsWithMekanismGearWhenChanceIsCertain(GameTestHelper helper) {
        SpawnChances saved = SpawnChances.capture();
        helper.succeedWhen(() -> {
            saved.certain();
            try {
                check(saved.isCertain(), "Waiting for a certain Mekanism armor spawn chance");
                Zombie zombie = spawnZombie(helper);
                try {
                    // Zombie.finalizeSpawn can replace the main hand with an iron sword or shovel after the
                    // special-spawn event. That roll is 1% (5% on hard), so wait for a zombie it left alone.
                    assertMekanismSet(zombie);
                } finally {
                    zombie.discard();
                }
            } finally {
                saved.restore();
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void zombieSpawnsWithNoMekanismGearWhenChanceIsZero(GameTestHelper helper) {
        SpawnChances saved = SpawnChances.capture();
        helper.succeedWhen(() -> {
            saved.never();
            try {
                check(saved.isNever(), "Waiting for Mekanism armor spawns to be disabled");
                Zombie zombie = spawnZombie(helper);
                try {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        ItemStack stack = zombie.getItemBySlot(slot);
                        if (!stack.isEmpty()) {
                            String id = Registry.ITEM.getKey(stack.getItem()).toString();
                            check(!id.startsWith(MekanismTools.MODID + ":"), slot.getName() + " has " + id);
                        }
                    }
                } finally {
                    zombie.discard();
                }
            } finally {
                saved.restore();
            }
        });
    }

    /**
     * {@code EntityType.spawn} finalizes the mob. Forge posts the special-spawn event on that path, and Fabric posts
     * it from the head of {@code finalizeSpawn}.
     */
    private static Zombie spawnZombie(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        Zombie zombie = EntityType.ZOMBIE.spawn(helper.getLevel(), (CompoundTag) null, null, null, pos, MobSpawnType.NATURAL, false, false);
        check(zombie != null, "Zombie spawn returned nothing");
        return zombie;
    }

    private static void assertMekanismSet(Zombie zombie) {
        String sword = itemId(zombie.getMainHandItem());
        String material = null;
        for (String candidate : MATERIALS) {
            if (sword.equals(piece(candidate, "sword"))) {
                material = candidate;
                break;
            }
        }
        check(material != null, "Main hand is " + sword);
        check(itemId(zombie.getItemBySlot(EquipmentSlot.HEAD)).equals(piece(material, "helmet")),
              "Helmet is " + itemId(zombie.getItemBySlot(EquipmentSlot.HEAD)));
        check(itemId(zombie.getItemBySlot(EquipmentSlot.CHEST)).equals(piece(material, "chestplate")),
              "Chestplate is " + itemId(zombie.getItemBySlot(EquipmentSlot.CHEST)));
        check(itemId(zombie.getItemBySlot(EquipmentSlot.LEGS)).equals(piece(material, "leggings")),
              "Leggings are " + itemId(zombie.getItemBySlot(EquipmentSlot.LEGS)));
        check(itemId(zombie.getItemBySlot(EquipmentSlot.FEET)).equals(piece(material, "boots")),
              "Boots are " + itemId(zombie.getItemBySlot(EquipmentSlot.FEET)));
    }

    private static String piece(String material, String slot) {
        return MekanismTools.MODID + ":" + material + "_" + slot;
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "empty" : Registry.ITEM.getKey(stack.getItem()).toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static final class SpawnChances {

        private final double general;
        private final double[] pieces;

        private SpawnChances(double general, double[] pieces) {
            this.general = general;
            this.pieces = pieces;
        }

        static SpawnChances capture() {
            List<CachedDoubleValue> values = pieces();
            double[] saved = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                saved[i] = values.get(i).get();
            }
            return new SpawnChances(MekanismToolsConfig.tools.armorSpawnRate.get(), saved);
        }

        void certain() {
            MekanismToolsConfig.tools.armorSpawnRate.set(1);
            for (CachedDoubleValue value : pieces()) {
                value.set(1);
            }
        }

        void never() {
            MekanismToolsConfig.tools.armorSpawnRate.set(0);
        }

        void restore() {
            MekanismToolsConfig.tools.armorSpawnRate.set(general);
            List<CachedDoubleValue> values = pieces();
            for (int i = 0; i < values.size(); i++) {
                values.get(i).set(pieces[i]);
            }
        }

        boolean isCertain() {
            if (MekanismToolsConfig.tools.armorSpawnRate.get() != 1) {
                return false;
            }
            for (CachedDoubleValue value : pieces()) {
                if (value.get() != 1) {
                    return false;
                }
            }
            return true;
        }

        boolean isNever() {
            return MekanismToolsConfig.tools.armorSpawnRate.get() == 0;
        }

        private static List<CachedDoubleValue> pieces() {
            List<CachedDoubleValue> values = new ArrayList<>();
            ToolsConfig tools = MekanismToolsConfig.tools;
            for (ArmorSpawnChanceConfig material : new ArmorSpawnChanceConfig[]{
                  tools.bronzeSpawnRate, tools.lapisLazuliSpawnRate, tools.osmiumSpawnRate, tools.refinedGlowstoneSpawnRate,
                  tools.refinedObsidianSpawnRate, tools.steelSpawnRate
            }) {
                values.add(material.swordChance);
                values.add(material.helmetChance);
                values.add(material.chestplateChance);
                values.add(material.leggingsChance);
                values.add(material.bootsChance);
            }
            return values;
        }
    }
}
