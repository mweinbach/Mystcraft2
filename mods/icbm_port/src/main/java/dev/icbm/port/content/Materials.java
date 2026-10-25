package dev.icbm.port.content;

import dev.icbm.port.IcbmPort;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * Recipe components plus the upstream loot hooks.
 *
 * <p>Loot adapted from MIT ICBM-Classic {@code ICBMClassic.registerLoot} and {@code
 * prefab/item/LootEntryItemStack.java}: entries are added to the first ("main") pool of the
 * creeper/blaze drop tables and the abandoned mineshaft/simple dungeon chests. Upstream rolls a
 * stack of {@code random.nextInt(n)} items, i.e. uniform 0..n-1, which is what the uniform count
 * functions below reproduce. Upstream's own copper ingot is replaced by vanilla's copper ingot.
 * The {@code ConfigItems} loot switches live in {@code config/icbm-port-items.properties}; item
 * registration itself is always on (see PARITY.md).
 */
public final class Materials {
  private Materials() {}

  private static Properties settings = defaults();

  private static Properties defaults() {
    Properties p = new Properties();
    // Upstream ConfigItems toggles that affect loot (all default true upstream).
    p.setProperty("loot.chests", "true");
    p.setProperty("loot.sulfur", "true");
    p.setProperty("loot.ingots", "true");
    p.setProperty("loot.plates", "true");
    p.setProperty("loot.wires", "true");
    p.setProperty("loot.circuits", "true");
    return p;
  }

  static void loadSettings() {
    Path file = FabricLoader.getInstance().getConfigDir().resolve("icbm-port-items.properties");
    Properties loaded = defaults();
    try {
      if (Files.exists(file)) {
        try (Reader reader = Files.newBufferedReader(file)) {
          loaded.load(reader);
        }
      } else {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
          loaded.store(
              writer, "ICBM item loot switches (upstream ConfigItems). Restart to reload.");
        }
      }
    } catch (IOException e) {
      org.slf4j.LoggerFactory.getLogger("icbm_port")
          .warn("Using default ICBM item loot settings: {}", e.getMessage());
    }
    settings = loaded;
  }

  public static boolean enabled(String key) {
    return Boolean.parseBoolean(settings.getProperty(key, "true"));
  }

  private static Item item(String name) {
    return Registry.ITEM.get(IcbmPort.id(name));
  }

  /** Upstream LootEntryItemStack: weight, quality and a uniform 0..max-1 stack size. */
  private static void add(LootPool.Builder pool, Item item, int max, int weight, int quality) {
    pool.with(
        ItemEntry.builder(item)
            .weight(weight)
            .quality(quality)
            .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(0, max - 1))));
  }

  public static final Identifier MINESHAFT = new Identifier("minecraft", "chests/abandoned_mineshaft");
  public static final Identifier DUNGEON = new Identifier("minecraft", "chests/simple_dungeon");
  public static final Identifier CREEPER = new Identifier("minecraft", "entities/creeper");
  public static final Identifier BLAZE = new Identifier("minecraft", "entities/blaze");

  public static void register() {
    loadSettings();
    for (String name :
        new String[] {
          "steel_clump",
          "steel_ingot",
          "steel_plate",
          "iron_plate",
          "copper_wire",
          "gold_wire",
          "basic_circuit",
          "advanced_circuit",
          "elite_circuit",
          "battery",
          "poison_powder",
          "sulfur_dust",
          "saltpeter_ball",
          "saltpeter_dust",
          "missile_module",
          "empty_bomblet"
        }) {
      Registry.register(
          Registry.ITEM,
          IcbmPort.id(name),
          name.equals("battery")
              ? new dev.icbm.port.energy.BatteryItem()
              : new Item(new Item.Settings().group(IcbmPort.GROUP)));
    }
    LootTableEvents.MODIFY.register(
        (resourceManager, manager, id, builder, source) -> {
          boolean mob = id.equals(CREEPER) || id.equals(BLAZE);
          boolean chest = id.equals(MINESHAFT) || id.equals(DUNGEON);
          if ((mob && enabled("loot.sulfur")) || (chest && enabled("loot.chests"))) {
            int[] poolIndex = {0};
            builder.modifyPools(
                pool -> {
                  if (poolIndex[0]++ != 0) return;
                  if (mob) add(pool, item("sulfur_dust"), 10, 2, 0);
                  else {
                    if (enabled("loot.ingots")) {
                      add(pool, Items.COPPER_INGOT, 10, 15, 5);
                      add(pool, item("steel_ingot"), 10, 20, 3);
                    }
                    if (enabled("loot.plates")) add(pool, item("steel_plate"), 5, 30, 3);
                    if (enabled("loot.wires")) {
                      add(pool, item("copper_wire"), 20, 15, 5);
                      add(pool, item("gold_wire"), 15, 30, 3);
                    }
                    if (enabled("loot.circuits")) {
                      add(pool, item("basic_circuit"), 15, 15, 5);
                      add(pool, item("advanced_circuit"), 11, 30, 3);
                      add(pool, item("elite_circuit"), 8, 30, 3);
                    }
                  }
                });
          }
        });
  }
}
