package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.delivery.DeliveryContent;
import dev.icbm.port.delivery.HeldItemMissileItem;
import dev.icbm.port.flight.FlightLedger;
import dev.icbm.port.flight.FlightProfile;
import dev.icbm.port.flight.FlightTracked;
import dev.icbm.port.flight.StagedFlight;
import dev.icbm.port.flight.StagedFlight.Stage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Table-driven check of the flight state machine (docs/DELIVERY.md, "Flight state machine"). Each
 * row flies its own missile in its own lane: when the missile reaches the row's stage the event is
 * applied, then the expected outcome (re-plan, reject or keep going) must hold. Every live missile
 * also round-trips through NBT on every tick, so a save in any stage is checked as well.
 */
public class FlightStateMachineGameTests implements FabricGameTest {
  private static final int FAR = 600, NEAR = 100;

  private static void check(TestContext c, boolean condition, String message) {
    if (!condition) c.throwGameTestException(message);
  }

  /** One row: a missile, the stage that triggers the event, the event and its expectation. */
  private static final class Row {
    final String name;
    final Entity missile;
    final Predicate<StagedFlight> when;
    final Runnable event;
    /** Ticks since the event; true once the expectation holds (checks throw on violations). */
    final IntPredicate expect;

    final List<Entity> cleanup = new ArrayList<>();
    int eventTick = -1;
    boolean done;

    Row(
        String name,
        Entity missile,
        Predicate<StagedFlight> when,
        Runnable event,
        IntPredicate expect) {
      this.name = name;
      this.missile = missile;
      this.when = when;
      this.event = event;
      this.expect = expect;
    }

    StagedFlight plan() {
      return ((FlightTracked) missile).flightPlan();
    }
  }

  private static MissileEntity missile(TestContext c, int lane, int east) {
    BlockPos start = c.getAbsolutePos(new BlockPos(1, 2, 1 + 3 * lane));
    MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, c.getWorld());
    missile.setPosition(Vec3d.ofBottomCenter(start));
    missile.configureTarget(start.east(east));
    check(c, c.getWorld().spawnEntity(missile), "Missile must spawn in lane " + lane);
    return missile;
  }

  private static PigEntity pig(TestContext c, Entity at) {
    PigEntity pig = EntityType.PIG.create(c.getWorld());
    pig.refreshPositionAndAngles(at.getX(), at.getY(), at.getZ(), 0, 0);
    c.getWorld().spawnEntity(pig);
    return pig;
  }

  private static boolean captured(TestContext c, Entity missile) {
    return missile.isRemoved() && FlightLedger.get(c.getWorld()).generation(missile.getUuid()) > 0;
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmFlightStateMachine",
      tickLimit = 1800)
  public void flightStateMachineTable(TestContext c) {
    var world = c.getWorld();
    List<Row> rows = new ArrayList<>();
    int exit = FlightProfile.exitHeight(world);

    // ASCENT x player boards -> re-plan to the physical arc from where it is (the review case).
    {
      MissileEntity m = missile(c, 0, FAR);
      PlayerEntity player = c.createMockPlayer();
      double[] boardedX = new double[1];
      rows.add(
          new Row(
              "ASCENT, player boards",
              m,
              p -> p.stage() == Stage.ASCENT,
              () -> {
                boardedX[0] = m.getX();
                check(c, player.startRiding(m, true), "The player must board");
              },
              t -> {
                check(c, !m.isRemoved(), "A re-planned flight must not be discarded or captured");
                check(c, m.flightPlan().stage() == Stage.ARC, "Boarding in ASCENT must re-plan ARC");
                if (t < 20) return false;
                check(c, m.getX() > boardedX[0] + 1, "The re-planned arc must head for the target");
                player.stopRiding();
                return true;
              }));
    }
    // The same rule for a held-item missile, which shares StagedFlight.
    {
      BlockPos start = c.getAbsolutePos(new BlockPos(1, 2, 1 + 3 * 1));
      ItemStack ammo = new ItemStack(DeliveryContent.HELD_ITEM_MISSILE);
      HeldItemMissileItem.setCargo(ammo, new ItemStack(Items.COBBLESTONE));
      var held =
          DeliveryContent.launchHeldEntity(
              world, null, ammo, Vec3d.ofBottomCenter(start), Vec3d.ZERO, start.east(FAR));
      check(c, held != null, "Held-item missile must launch");
      PlayerEntity player = c.createMockPlayer();
      rows.add(
          new Row(
              "ASCENT, player boards a held-item missile",
              held,
              p -> p.stage() == Stage.ASCENT,
              () -> check(c, player.startRiding(held, true), "The player must board"),
              t -> {
                check(c, !held.isRemoved(), "The held-item missile must keep flying");
                check(c, held.flightPlan().stage() == Stage.ARC, "Boarding in ASCENT must re-plan");
                player.stopRiding();
                return true;
              }));
    }
    // ASCENT x mob boards -> keep going: virtual flight carries the mob.
    {
      MissileEntity m = missile(c, 2, FAR);
      PigEntity pig = pig(c, m);
      rows.add(
          new Row(
              "ASCENT, mob boards",
              m,
              p -> p.stage() == Stage.ASCENT,
              () -> check(c, pig.startRiding(m, true), "The pig must board"),
              t -> {
                check(c, m.flightPlan().stage() == Stage.ASCENT, "A capturable rider keeps ASCENT");
                return t >= 3;
              }));
      rows.get(rows.size() - 1).cleanup.add(pig);
    }
    // ASCENT x mob rider dies -> keep going.
    {
      MissileEntity m = missile(c, 3, FAR);
      PigEntity pig = pig(c, m);
      check(c, pig.startRiding(m, true), "The pig must board on the pad");
      rows.add(
          new Row(
              "ASCENT, mob rider dies",
              m,
              p -> p.stage() == Stage.ASCENT,
              pig::kill,
              t -> {
                check(c, !m.isRemoved() || captured(c, m), "The missile must keep flying");
                check(
                    c,
                    m.isRemoved() || m.flightPlan().stage() == Stage.ASCENT,
                    "Losing a rider keeps ASCENT");
                return t >= 3;
              }));
      rows.get(rows.size() - 1).cleanup.add(pig);
    }
    // ARC x player dismounts above the exit height -> keep going in virtual flight. Launched high
    // so the arc crosses the exit height a few blocks from the test (mock players load no chunks).
    {
      BlockPos lane = c.getAbsolutePos(new BlockPos(1, 2, 1 + 3 * 4));
      BlockPos start = new BlockPos(lane.getX(), world.getTopY() - 70, lane.getZ());
      MissileEntity m = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
      m.setPosition(Vec3d.ofBottomCenter(start));
      m.configureTarget(new BlockPos(start.getX() + 110, world.getTopY() - 1, start.getZ()));
      check(c, world.spawnEntity(m), "Missile must spawn in lane 4");
      PlayerEntity player = c.createMockPlayer();
      check(c, player.startRiding(m, true), "The player must board on the pad");
      rows.add(
          new Row(
              "ARC above exit height, player dismounts",
              m,
              p -> p.stage() == Stage.ARC && m.getY() >= exit,
              player::stopRiding,
              t -> {
                if (!m.isRemoved()) {
                  check(c, t < 3, "A riderless arc above the exit height must be captured");
                  return false;
                }
                check(c, captured(c, m), "The missile must enter virtual flight, not be discarded");
                FlightLedger.get(world)
                    .removeNear(world, Vec3d.ofCenter(m.flightPlan().target()), 8);
                return true;
              }));
    }
    // DESCENT x player boards -> keep going; descent is never captured.
    {
      BlockPos target = c.getAbsolutePos(new BlockPos(1, 1, 1 + 3 * 5));
      MissileEntity m = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
      m.setPosition(Vec3d.ofCenter(target.up(30)));
      m.configureTarget(target);
      m.flightPlan().prepareReentry(m.getPos());
      check(c, world.spawnEntity(m), "Descending missile must spawn");
      PlayerEntity player = c.createMockPlayer();
      double[] boardedY = new double[1];
      rows.add(
          new Row(
              "DESCENT, player boards",
              m,
              p -> p.stage() == Stage.DESCENT,
              () -> {
                boardedY[0] = m.getY();
                check(c, player.startRiding(m, true), "The player must board");
              },
              t -> {
                check(c, !m.isRemoved(), "A descending missile must keep flying");
                check(c, m.flightPlan().stage() == Stage.DESCENT, "Boarding keeps DESCENT");
                if (t < 5) return false;
                check(c, m.getY() < boardedY[0] - 3, "The missile must keep descending");
                player.stopRiding();
                return true;
              }));
    }
    // ASCENT x EMP engine kill -> the flight ends; it falls physically and is never captured.
    {
      MissileEntity m = missile(c, 6, FAR);
      double[] vy = new double[1];
      rows.add(
          new Row(
              "ASCENT, EMP engine kill",
              m,
              p -> p.stage() == Stage.ASCENT,
              () -> {
                vy[0] = m.getVelocity().y;
                m.killEngine();
              },
              t -> {
                check(
                    c,
                    !m.flightPlan().targeted() && m.flightPlan().stage() == Stage.DIRECT,
                    "An engine kill must end the guided flight");
                check(c, !m.hasNoGravity(), "A dead engine must fall");
                check(
                    c,
                    !FlightLedger.tryCapture(m, m.flightPlan()),
                    "An unguided missile must never enter virtual flight");
                if (t < 2) return false;
                check(c, m.getVelocity().y < vy[0], "Gravity must take over");
                return true;
              }));
    }
    // WARMUP x retarget -> allowed; CLIMB x retarget -> rejected.
    {
      MissileEntity m = missile(c, 7, NEAR);
      BlockPos origin = m.getBlockPos();
      m.configureTarget(origin.east(NEAR + 10));
      check(
          c,
          m.flightPlan().target().equals(origin.east(NEAR + 10)),
          "A missile still on the pad may be retargeted");
      boolean[] rejected = new boolean[1];
      rows.add(
          new Row(
              "CLIMB, target changes",
              m,
              p -> p.stage() == Stage.CLIMB,
              () -> {
                try {
                  m.configureTarget(origin.east(NEAR + 20));
                } catch (IllegalArgumentException expected) {
                  rejected[0] = true;
                }
              },
              t -> {
                check(c, rejected[0], "Retargeting a missile in flight must be rejected");
                check(
                    c,
                    m.flightPlan().target().equals(origin.east(NEAR + 10))
                        && m.flightPlan().stage() != Stage.WARMUP,
                    "A rejected retarget must leave the flight unchanged");
                return true;
              }));
    }
    // ARC x blast damage -> keep going while health lasts; destroyed at zero health.
    {
      MissileEntity m = missile(c, 8, NEAR);
      rows.add(
          new Row(
              "ARC, hit by a blast",
              m,
              p -> p.stage() == Stage.ARC && p.stageTicks() >= 5,
              () -> {
                check(c, m.damage(DamageSource.explosion((net.minecraft.entity.LivingEntity) null), 1),
                    "Blast damage must apply");
              },
              t -> {
                if (t == 1) {
                  check(
                      c,
                      !m.isRemoved() && m.flightPlan().stage() == Stage.ARC,
                      "A damaged missile must keep flying its arc");
                  m.damage(DamageSource.explosion((net.minecraft.entity.LivingEntity) null), 1e4f);
                  return false;
                }
                check(c, m.isRemoved(), "A missile out of health must be destroyed");
                return true;
              }));
    }
    // Leash: a leashed mob that boards drops its lead (vanilla MobEntity#startRiding); missiles
    // are not mobs, so they cannot be leashed themselves.
    {
      MissileEntity m = missile(c, 9, FAR);
      PigEntity pig = pig(c, m);
      pig.attachLeash(c.createMockPlayer(), false);
      rows.add(
          new Row(
              "WARMUP, leashed mob boards",
              m,
              p -> p.stage() == Stage.WARMUP,
              () -> check(c, pig.startRiding(m, true), "The leashed pig must board"),
              t -> {
                check(c, !pig.isLeashed(), "Boarding must drop the lead");
                check(c, pig.getVehicle() == m, "The pig must ride the missile");
                return true;
              }));
      rows.get(rows.size() - 1).cleanup.add(pig);
    }

    EnumSet<Stage> saved = EnumSet.noneOf(Stage.class);
    int[] tick = {0};
    boolean[] ready = {false};
    c.runAtEveryTick(
        () -> {
          // Expectations count test ticks after an event, which only match the missiles' own ticks
          // once their chunks tick entities. A busy server promotes a fresh test area late, so the
          // table starts when every lane does (forced test chunks then stay entity-ticking).
          if (!ready[0]) {
            for (Row row : rows)
              if (!row.missile.isRemoved() && !world.shouldTickEntity(row.missile.getBlockPos()))
                return;
            ready[0] = true;
          }
          tick[0]++;
          boolean all = true;
          for (Row row : rows) {
            if (row.done) continue;
            all = false;
            Entity missile = row.missile;
            // Save/load in every stage: the plan must come back unchanged.
            if (!missile.isRemoved() && missile instanceof MissileEntity live) {
              var reloaded = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
              reloaded.readNbt(live.writeNbt(new NbtCompound()));
              var a = live.flightPlan();
              var b = reloaded.flightPlan();
              check(
                  c,
                  a.stage() == b.stage()
                      && a.stageTicks() == b.stageTicks()
                      && java.util.Objects.equals(a.target(), b.target()),
                  row.name + ": flight plan changed across a save in " + a.stage());
              saved.add(a.stage());
            }
            if (row.eventTick < 0) {
              if (missile.isRemoved())
                c.throwGameTestException(row.name + ": missile ended before its event");
              if (row.when.test(row.plan())) {
                row.event.run();
                row.eventTick = tick[0];
              }
              continue;
            }
            if (row.expect.test(tick[0] - row.eventTick)) {
              row.done = true;
              for (Entity extra : row.cleanup) {
                extra.stopRiding();
                extra.discard();
              }
              missile.removeAllPassengers();
              missile.discard();
            }
          }
          if (!all) return;
          check(
              c,
              saved.containsAll(
                  EnumSet.of(
                      Stage.WARMUP,
                      Stage.CLIMB,
                      Stage.LOCK_HEIGHT,
                      Stage.ASCENT,
                      Stage.ARC,
                      Stage.DESCENT)),
              "Save/load must be checked in every flight stage: " + saved);
          world
              .getEntitiesByClass(
                  ItemEntity.class, new Box(c.getAbsolutePos(BlockPos.ORIGIN)).expand(40), e -> true)
              .forEach(Entity::discard);
          c.complete();
        });
  }
}
