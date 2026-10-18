package dev.icbm.port.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.icbm.port.IcbmPort;
import dev.icbm.port.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

public final class CommandApiGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean ok, String message) {
    if (!ok) c.throwGameTestException(message);
  }

  private static CommandDispatcher<ServerCommandSource> dispatcher() {
    var dispatcher = new CommandDispatcher<ServerCommandSource>();
    IcbmCommands.register(dispatcher);
    return dispatcher;
  }

  private static ServerCommandSource source(TestContext c) {
    return c.getWorld()
        .getServer()
        .getCommandSource()
        .withWorld(c.getWorld())
        .withPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(1, 1, 1))));
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void commandsRejectUnprivilegedAndUnboundedInput(TestContext c) {
    var d = dispatcher();
    var source = source(c);
    try {
      d.execute("icbm blast list", source.withLevel(0));
      c.throwGameTestException("Unprivileged command accepted");
    } catch (CommandSyntaxException expected) {
    }
    for (String command :
        new String[] {
          "icbm lag 1025",
          "icbm blast trigger condensed NaN",
          "icbm blast spread 5 2 condensed minecraft:overworld 0 64 0 6",
          "icbm remove players 4"
        }) {
      try {
        d.execute(command, source);
        c.throwGameTestException("Unsafe input accepted: " + command);
      } catch (CommandSyntaxException expected) {
      }
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void registeredActionCommandsAndCancellation(TestContext c) throws CommandSyntaxException {
    var id = new Identifier("icbm_port_test", "command_action");
    var calls = new AtomicInteger();
    var completions = new AtomicInteger();
    IcbmApi.registerAction(
        new IcbmApi.Descriptor(
            id,
            1,
            false,
            context -> {
              check(
                  c,
                  context.source().cause().getPath().equals("command"),
                  "Command attribution missing");
              calls.incrementAndGet();
              return true;
            }));
    IcbmApi.AFTER_ACTION.register(
        (context, accepted) -> {
          if (context.action().equals(id)) completions.incrementAndGet();
        });
    var d = dispatcher();
    var source = source(c);
    check(
        c,
        d.execute("icbm blast trigger icbm_port_test:command_action 6", source) == 1
            && calls.get() == 1,
        "Registered action not executed");
    IcbmApi.BEFORE_ACTION.register(context -> !context.action().equals(id));
    check(
        c,
        d.execute("icbm blast trigger icbm_port_test:command_action 6", source) == 0
            && calls.get() == 1
            && completions.get() == 2,
        "Cancellation or completion contract failed");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void gridSpreadUsesUpstreamCountSemantics(TestContext c) throws CommandSyntaxException {
    var id = new Identifier("icbm_port_test", "grid_action");
    var calls = new AtomicInteger();
    IcbmApi.registerAction(
        new IcbmApi.Descriptor(
            id,
            1,
            false,
            context -> {
              calls.incrementAndGet();
              return true;
            }));
    Vec3d p = source(c).getPosition();
    String command =
        "icbm blast spread 1 1 "
            + id
            + " "
            + c.getWorld().getRegistryKey().getValue()
            + " "
            + p.x
            + " "
            + p.y
            + " "
            + p.z
            + " 2";
    check(
        c,
        dispatcher().execute(command, source(c)) == 9 && calls.get() == 9,
        "Count 1 must create a three by three grid");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void removalScopesOnlyMatchingIcbmEntities(TestContext c) throws CommandSyntaxException {
    Vec3d p = source(c).getPosition();
    var missile = new dev.icbm.port.entity.MissileEntity(IcbmPort.MISSILE_ENTITY, c.getWorld());
    missile.setPosition(p);
    c.getWorld().spawnEntity(missile);
    var tnt = new net.minecraft.entity.TntEntity(c.getWorld(), p.x, p.y, p.z, null);
    c.getWorld().spawnEntity(tnt);
    check(
        c,
        dispatcher().execute("icbm remove missiles 4", source(c)) == 1
            && missile.isRemoved()
            && !tnt.isRemoved(),
        "Removal touched unrelated entity or missed missile");
    tnt.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void attributionAndRegistryContracts(TestContext c) {
    var uuid = java.util.UUID.randomUUID();
    var original =
        new ActionSource(
            uuid, uuid, new Identifier("icbm_port_test", "redstone"), new BlockPos(4, 5, 6));
    check(
        c, ActionSource.fromNbt(original.toNbt()).equals(original), "Attribution roundtrip failed");
    var id = new Identifier("icbm_port_test", "registration");
    var observed = new AtomicInteger();
    IcbmApi.observeRegistrations(
        entry -> {
          if (entry.id().equals(id)) observed.incrementAndGet();
        });
    var descriptor = new IcbmApi.Descriptor(id, 1, false, context -> true);
    IcbmApi.registerAction(descriptor);
    try {
      IcbmApi.registerAction(descriptor);
      c.throwGameTestException("Duplicate registration accepted");
    } catch (IllegalArgumentException expected) {
    }
    check(c, observed.get() == 1, "Registration observer contract failed");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void builtinProjectileFactorySpawnsPayload(TestContext c) {
    var world = c.getWorld();
    Vec3d p = source(c).getPosition();
    int before = 0;
    for (var e : world.iterateEntities())
      if (e instanceof dev.icbm.port.entity.MissileEntity) before++;
    check(
        c,
        IcbmApi.launch(
            new Identifier("icbm_port", "explosive_missile"),
            new IcbmApi.ProjectileContext(
                world,
                p,
                new Vec3d(0, .1, 0),
                new ItemStack(IcbmPort.CONDENSED_MISSILE),
                ActionSource.of(null))),
        "Projectile factory rejected registered payload");
    int after = 0;
    for (var e : world.iterateEntities())
      if (e instanceof dev.icbm.port.entity.MissileEntity) after++;
    check(c, after == before + 1, "Projectile factory did not spawn exactly once");
    world
        .getEntitiesByClass(
            dev.icbm.port.entity.MissileEntity.class, new Box(p, p).expand(1), e -> true)
        .forEach(net.minecraft.entity.Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void viewerExamplesMatchRealPayloadSerializers(TestContext c) {
    var recipes =
        dev.icbm.port.integration.JeiRecipeDisplays.forCargo(
            new ItemStack(net.minecraft.item.Items.IRON_PICKAXE));
    check(c, recipes.size() == 6, "Expected held/parachute/balloon loading and unloading examples");
    for (var display : recipes) {
      var handler =
          new net.minecraft.screen.ScreenHandler(null, 0) {
            public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
              return true;
            }

            public ItemStack transferSlot(
                net.minecraft.entity.player.PlayerEntity player, int index) {
              return ItemStack.EMPTY;
            }
          };
      var grid = new net.minecraft.inventory.CraftingInventory(handler, 3, 3);
      int slot = 0;
      for (var ingredient : display.getIngredients())
        grid.setStack(slot++, ingredient.getMatchingStacks()[0].copy());
      var actual =
          c.getWorld()
              .getRecipeManager()
              .getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, grid, c.getWorld());
      check(c, actual.isPresent(), "Viewer example is not actually craftable: " + display.getId());
      check(
          c,
          ItemStack.areEqual(actual.get().craft(grid), display.getOutput()),
          "Viewer NBT/output differs: " + display.getId());
      var expected = display.getRemainder(grid);
      var remainder = actual.get().getRemainder(grid);
      for (int i = 0; i < grid.size(); i++)
        check(
            c,
            ItemStack.areEqual(expected.get(i), remainder.get(i)),
            "Viewer container remainder differs: " + display.getId());
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void builtinPayloadHookCancelsBeforeQueueAdmission(TestContext c) {
    Vec3d pos = source(c).getPosition();
    var id = new Identifier("icbm_port", "smoke");
    var before = new AtomicInteger();
    var after = new AtomicInteger();
    IcbmApi.BEFORE_ACTION.register(
        context -> {
          if (context.world() == c.getWorld()
              && context.action().equals(id)
              && context.position().squaredDistanceTo(pos) < 1e-8) {
            before.incrementAndGet();
            return false;
          }
          return true;
        });
    IcbmApi.AFTER_ACTION.register(
        (context, accepted) -> {
          if (context.world() == c.getWorld()
              && context.action().equals(id)
              && context.position().squaredDistanceTo(pos) < 1e-8) {
            check(c, !accepted, "Canceled effect reported accepted");
            after.incrementAndGet();
          }
        });
    int count = dev.icbm.port.payload.Payloads.queuedCount(c.getWorld());
    check(
        c,
        !IcbmApi.execute(new ActionContext(c.getWorld(), pos, id, 4, ActionSource.of(null))),
        "Built-in cancellation was ignored");
    check(
        c,
        before.get() == 1
            && after.get() == 1
            && dev.icbm.port.payload.Payloads.queuedCount(c.getWorld()) == count,
        "Built-in hook duplicated or admitted canceled work");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void lagCleanupRetiresVirtualFlightWithoutResurrection(TestContext c)
      throws CommandSyntaxException {
    Vec3d pos = source(c).getPosition();
    var missile = new dev.icbm.port.entity.MissileEntity(IcbmPort.MISSILE_ENTITY, c.getWorld());
    missile.setPosition(pos);
    missile.configureTarget(new BlockPos(pos).east(20));
    c.getWorld().spawnEntity(missile);
    var old = missile.writeNbt(new net.minecraft.nbt.NbtCompound());
    var uuid = missile.getUuid();
    check(
        c,
        dev.icbm.port.flight.FlightLedger.tryCapture(missile, missile.flightPlan()),
        "Virtual flight admission failed");
    check(
        c,
        dispatcher().execute("icbm lag 4", source(c)) >= 1,
        "Lag cleanup did not count virtual flight");
    var ledger = dev.icbm.port.flight.FlightLedger.get(c.getWorld());
    check(c, ledger.generation(uuid) > 0, "Cleanup erased generation protection");
    var stale = new dev.icbm.port.entity.MissileEntity(IcbmPort.MISSILE_ENTITY, c.getWorld());
    stale.readNbt(old);
    check(
        c,
        dev.icbm.port.flight.FlightLedger.suppressDuplicate(stale, stale.flightPlan()),
        "Stale flight can resurrect after cleanup");
    c.complete();
  }
}
