package dev.icbm.port;

import dev.icbm.port.payload.PayloadType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

public final class ContentGameTests implements FabricGameTest {
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void everyPayloadRecipeLoads(TestContext c) {
    for (PayloadType payload : PayloadType.values())
      if (payload.hasContent()) {
        for (String suffix : new String[] {"_explosive", "_missile", "_bomb_cart"}) {
          if (c.getWorld().getRecipeManager().get(IcbmPort.id(payload.id() + suffix)).isEmpty())
            c.throwGameTestException("Missing recipe: " + payload.id() + suffix);
        }
      }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void sulfurLootHasConfiguredStackAndVanillaAlternative(TestContext c) {
    var world = c.getWorld();
    var creeper = EntityType.CREEPER.create(world);
    var table =
        world
            .getServer()
            .getLootManager()
            .getTable(new Identifier("minecraft", "entities/creeper"));
    var context =
        new LootContext.Builder(world)
            .parameter(LootContextParameters.THIS_ENTITY, creeper)
            .parameter(
                LootContextParameters.ORIGIN,
                Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 2, 2))))
            .parameter(LootContextParameters.DAMAGE_SOURCE, DamageSource.GENERIC)
            .random(42)
            .build(LootContextTypes.ENTITY);
    boolean sulfur = false, gunpowder = false;
    for (int i = 0; i < 60; i++)
      for (var stack : table.generateLoot(context)) {
        if (stack.isOf(Registry.ITEM.get(IcbmPort.id("sulfur_dust")))) {
          if (stack.getCount() != 10)
            c.throwGameTestException("Sulfur loot must preserve upstream stack size10");
          sulfur = true;
        }
        if (stack.isOf(net.minecraft.item.Items.GUNPOWDER)) gunpowder = true;
      }
    if (!sulfur || !gunpowder)
      c.throwGameTestException("Both sulfur and vanilla main-pool loot must remain obtainable");
    c.complete();
  }
}
