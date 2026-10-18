package dev.icbm.port.content;

import dev.icbm.port.IcbmPort;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.item.Item;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/** Recipe components. Sulfur is an alternative entry in the original mob drop pool. */
public final class Materials {
  private Materials() {}

  public static void register() {
    for (String name :
        new String[] {
          "steel_clump",
          "steel_ingot",
          "steel_plate",
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
          if (id.equals(new Identifier("minecraft", "entities/creeper"))
              || id.equals(new Identifier("minecraft", "entities/blaze"))) {
            int[] poolIndex = {0};
            builder.modifyPools(
                pool -> {
                  if (poolIndex[0]++ == 0)
                    pool.with(
                        ItemEntry.builder(Registry.ITEM.get(IcbmPort.id("sulfur_dust")))
                            .weight(2)
                            .apply(
                                SetCountLootFunction.builder(
                                    ConstantLootNumberProvider.create(10))));
                });
          }
        });
  }
}
