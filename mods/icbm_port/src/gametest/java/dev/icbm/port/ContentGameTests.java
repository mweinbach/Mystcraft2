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

  /** The iron plate recipe must not shadow vanilla iron-ingot grids such as the trapdoor. */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void ironPlateRecipeLeavesVanillaTrapdoor(TestContext c) {
    var world = c.getWorld();
    var manager = world.getRecipeManager();
    // 2x2 iron ingots: only the vanilla trapdoor may match.
    var square = ironGrid(new int[] {0, 1, 3, 4});
    var squareMatches = manager.getAllMatches(net.minecraft.recipe.RecipeType.CRAFTING, square, world);
    if (squareMatches.size() != 1
        || !squareMatches.get(0).craft(square).isOf(net.minecraft.item.Items.IRON_TRAPDOOR))
      c.throwGameTestException("2x2 iron ingots must craft only the vanilla iron trapdoor");
    // Two stacked ingots: the ICBM iron plate, two of them.
    var stacked = ironGrid(new int[] {0, 3});
    var plate = manager.getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, stacked, world);
    if (plate.isEmpty()
        || !Registry.ITEM.getId(plate.get().craft(stacked).getItem()).equals(IcbmPort.id("iron_plate"))
        || plate.get().craft(stacked).getCount() != 2)
      c.throwGameTestException("Two stacked iron ingots must craft two ICBM iron plates");
    c.complete();
  }

  private static net.minecraft.inventory.CraftingInventory ironGrid(int[] slots) {
    var grid =
        new net.minecraft.inventory.CraftingInventory(
            new net.minecraft.screen.ScreenHandler(null, -1) {
              @Override
              public net.minecraft.item.ItemStack transferSlot(
                  net.minecraft.entity.player.PlayerEntity player, int index) {
                return net.minecraft.item.ItemStack.EMPTY;
              }

              @Override
              public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
                return false;
              }

              @Override
              public void onContentChanged(net.minecraft.inventory.Inventory inventory) {}
            },
            3,
            3);
    for (int slot : slots)
      grid.setStack(slot, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT));
    return grid;
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
    java.util.Set<Integer> counts = new java.util.HashSet<>();
    for (int i = 0; i < 60; i++)
      for (var stack : table.generateLoot(context)) {
        if (stack.isOf(Registry.ITEM.get(IcbmPort.id("sulfur_dust")))) {
          // Upstream LootEntryItemStack rolls random.nextInt(10): 0..9, where 0 drops nothing.
          if (stack.getCount() < 1 || stack.getCount() > 9)
            c.throwGameTestException("Sulfur loot must roll upstream's 0-9 stack size");
          counts.add(stack.getCount());
          sulfur = true;
        }
        if (stack.isOf(net.minecraft.item.Items.GUNPOWDER)) gunpowder = true;
      }
    if (!sulfur || !gunpowder)
      c.throwGameTestException("Both sulfur and vanilla main-pool loot must remain obtainable");
    if (counts.size() < 3)
      c.throwGameTestException("Sulfur stack size must vary like upstream, saw " + counts);
    c.complete();
  }
}
