package dev.icbm.port.materialtest;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/** Isolated fake provider: items always exist, material memberships only exist with the pack. */
public final class MaterialFixtureMod implements ModInitializer {
  public static final String ID = "icbm_port_materialtest";
  public static final String PACK_NAME = ID + "/foreign_materials";
  public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

  @Override
  public void onInitialize() {
    for (String name :
        new String[] {
          "steel_ingot",
          "steel_ingot_nested",
          "copper_ingot",
          "copper_wire",
          "steel_plate",
          "sulfur_dust",
          "saltpeter_dust",
          "carbon_dust",
          "uranium_ingot",
          "basic_circuit",
          "unrelated"
        })
      ITEMS.put(
          name,
          Registry.register(
              Registry.ITEM, new Identifier(ID, name), new Item(new Item.Settings())));
    for (String pack : new String[] {"foreign_materials", "missing_circuit", "missing_components"})
      if (!ResourceManagerHelper.registerBuiltinResourcePack(
          new Identifier(ID, pack),
          FabricLoader.getInstance().getModContainer(ID).orElseThrow(),
          ResourcePackActivationType.NORMAL))
        throw new IllegalStateException("Material fixture pack registration failed");
  }
}
