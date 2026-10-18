package dev.icbm.port.delivery;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;

/** Source-style ban/allow lists, with bounded physics and interaction settings. */
public final class DeliveryConfig {
  private static Properties properties = defaults();

  private static Properties defaults() {
    Properties p = new Properties();
    p.setProperty("cargo.items.ban", "true");
    p.setProperty("cargo.items.list", "");
    p.setProperty("cargo.entities.ban", "true");
    p.setProperty(
        "cargo.entities.list", "minecraft:player,minecraft:ender_dragon,minecraft:wither");
    p.setProperty("held.items.ban", "true");
    p.setProperty("held.items.list", "");
    p.setProperty("held.blockDamageMultiplier", "4");
    p.setProperty("held.genericInteractions", "true");
    p.setProperty("balloon.liftTicks", "600");
    return p;
  }

  public static void load() {
    Path file = FabricLoader.getInstance().getConfigDir().resolve("icbm-port-delivery.properties");
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
              writer,
              "ICBM delivery: comma-separated registry ids; ban=false makes list an allow list."
                  + " Restart to reload.");
        }
      }
      properties = loaded;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load ICBM delivery configuration", e);
    }
  }

  private static boolean allowed(String key, String id) {
    boolean contained =
        Arrays.stream(properties.getProperty(key + ".list", "").split(","))
            .map(String::trim)
            .anyMatch(id::equals);
    return Boolean.parseBoolean(properties.getProperty(key + ".ban", "true"))
        ? !contained
        : contained;
  }

  public static boolean cargoAllowed(ItemStack stack) {
    return !stack.isEmpty()
        && allowed("cargo.items", Registry.ITEM.getId(stack.getItem()).toString());
  }

  public static boolean entityAllowed(Entity entity) {
    return allowed("cargo.entities", Registry.ENTITY_TYPE.getId(entity.getType()).toString());
  }

  public static boolean heldAllowed(ItemStack stack) {
    return !stack.isEmpty()
        && allowed("held.items", Registry.ITEM.getId(stack.getItem()).toString());
  }

  public static boolean genericInteractions() {
    return Boolean.parseBoolean(properties.getProperty("held.genericInteractions", "true"));
  }

  private static double number(String key, double fallback, double min, double max) {
    try {
      double value = Double.parseDouble(properties.getProperty(key));
      return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  public static double blockDamageMultiplier() {
    return number("held.blockDamageMultiplier", 4, 0, 64);
  }

  public static int balloonLiftTicks() {
    return (int) number("balloon.liftTicks", 600, 0, 72000);
  }

  private DeliveryConfig() {}
}
