package dev.icbm.port.payload;

import dev.icbm.port.simulation.RedmatterEntity;
import dev.icbm.port.simulation.SimulationContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in antimatter and red matter cost measurement: {@code ./scripts/gradle.sh
 * :icbm_port:runGametest -PicbmBenchmark [-PicbmBenchmarkRadius=275]}. Without the property the
 * test passes at once.
 *
 * <p>Terrain is solid stone (with ores and a bedrock floor) from the bottom of the world to y=63
 * and air above, like a typical overworld column; blasts are centred at y=64, a surface hit.
 *
 * <ol>
 *   <li>Antimatter with no time budget, timed as one tick; then the chunk packets watching players
 *       would be sent are built and timed; then the ticks until sky light reaches the crater floor.
 *   <li>Antimatter elsewhere with the production budget: ticks taken and the longest server tick.
 *   <li>A red matter core with the default horizon: growth progress and the longest server tick.
 * </ol>
 */
public final class ExtremeBenchmarkGameTests implements FabricGameTest {
  private static final Logger LOGGER = LoggerFactory.getLogger("icbm-benchmark");

  private static String ms(long nanos) {
    return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
  }

  /**
   * Completes once the light thread reaches a task queued now, after everything queued before it
   * for these (equally prioritised, force-loaded) chunks.
   */
  private static java.util.concurrent.CompletableFuture<?> lightBarrier(ServerWorld world, Vec3d at) {
    var lighting = world.getChunkManager().getLightingProvider();
    ChunkPos pos = new ChunkPos(new BlockPos(at));
    return lighting
        .retainData(world.getChunk(pos.x, pos.z))
        .thenRun(() -> lighting.setRetainData(pos, false));
  }

  private static long lastTick(ServerWorld world) {
    var server = world.getServer();
    return server.lastTickLengths[(server.getTicks() - 1 + 100) % 100];
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 24000,
      batchId = "icbmBenchmark")
  public void antimatterAndRedmatterCost(TestContext c) {
    if (!Boolean.getBoolean("icbm.benchmark")) {
      c.complete();
      return;
    }
    ServerWorld world = c.getWorld();
    int radius = Integer.getInteger("icbm.benchmark.radius", PayloadDefaults.ANTIMATTER_RADIUS);
    int reach = (radius >> 4) + 1;
    Vec3d single = new Vec3d(-12288.5, 64, 12288.5);
    Vec3d budgeted = new Vec3d(-20480.5, 64, 12288.5);
    Vec3d core = new Vec3d(-28672.5, 64, 12288.5);
    List<ChunkPos> area = new ArrayList<>();
    long[] state = new long[8];
    int[] phase = {0};
    List<BlockPos> probes = new ArrayList<>();
    RedmatterEntity[] singularity = new RedmatterEntity[1];
    java.util.concurrent.CompletableFuture<?>[] barrier = new java.util.concurrent.CompletableFuture<?>[1];
    c.runAtEveryTick(
        () -> {
          switch (phase[0]) {
            case 0 -> {
              long start = System.nanoTime();
              area.addAll(ExtremePayloadGameTests.forceLoad(world, single.x, single.z, reach));
              ExtremePayloadGameTests.fillStone(world, area, world.getBottomSectionCoord(), 3);
              LOGGER.info(
                  "[icbm-benchmark] prepared {} chunks in {} ms", area.size(), ms(System.nanoTime() - start));
              barrier[0] = lightBarrier(world, single);
              state[7] = System.nanoTime();
              state[0] = world.getTime();
              phase[0] = 10;
            }
            case 10 -> {
              // Let the fixture's own light work finish so the crater is measured alone.
              if (!barrier[0].isDone()) return;
              LOGGER.info(
                  "[icbm-benchmark] fixture light work drained after {} ticks ({} ms)",
                  world.getTime() - state[0],
                  ms(System.nanoTime() - state[7]));
              phase[0] = 1;
            }
            case 1 -> {
              long saved = Scar.tickBudgetNanos;
              Scar.tickBudgetNanos = Long.MAX_VALUE;
              long start = System.nanoTime();
              boolean accepted =
                  Payloads.detonateScaled(world, single, PayloadType.ANTIMATTER, null, radius);
              long elapsed = System.nanoTime() - start;
              Scar.tickBudgetNanos = saved;
              LOGGER.info(
                  "[icbm-benchmark] r={} unbudgeted: accepted={}, whole sphere in one call {} ms,"
                      + " queued afterwards={}",
                  radius,
                  accepted,
                  ms(elapsed),
                  Payloads.queuedCount(world));
              long packetNanos = 0, viewNanos = 0, bytes = 0;
              int packets = 0, viewPackets = 0;
              ChunkPos centre = new ChunkPos(new BlockPos(single));
              for (ChunkPos pos : area) {
                if (!SphereEraser.touches(world, single, -1, radius, pos.x, pos.z)) continue;
                WorldChunk chunk = world.getChunk(pos.x, pos.z);
                long t = System.nanoTime();
                ChunkDataS2CPacket packet =
                    new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null, true);
                long d = System.nanoTime() - t;
                packetNanos += d;
                packets++;
                if (Math.max(Math.abs(pos.x - centre.x), Math.abs(pos.z - centre.z)) <= 12) {
                  viewNanos += d;
                  viewPackets++;
                }
                var buf =
                    new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
                packet.write(buf);
                bytes += buf.readableBytes();
                buf.release();
              }
              LOGGER.info(
                  "[icbm-benchmark] r={} chunk packets: {} built in {} ms ({} KiB); within view"
                      + " distance 12: {} in {} ms",
                  radius,
                  packets,
                  ms(packetNanos),
                  bytes / 1024,
                  viewPackets,
                  ms(viewNanos));
              int floor = world.getBottomY() + 1;
              for (double fraction : new double[] {0, .5, .9}) {
                double dx = radius * fraction;
                double half = Math.sqrt(Math.max(0, (double) radius * radius - dx * dx));
                int y = Math.max(floor, (int) Math.ceil(single.y - half + 1));
                probes.add(new BlockPos(single.x + dx, y, single.z));
              }
              state[0] = world.getTime();
              barrier[0] = lightBarrier(world, single);
              state[7] = System.nanoTime();
              state[6] = -1;
              phase[0] = 2;
            }
            case 2 -> {
              if (state[6] < 0 && barrier[0].isDone()) {
                state[6] = 0;
                LOGGER.info(
                    "[icbm-benchmark] r={} light work queued by the crater drained after {} ticks"
                        + " ({} ms)",
                    radius,
                    world.getTime() - state[0],
                    ms(System.nanoTime() - state[7]));
              }
              boolean lit =
                  probes.stream().allMatch(p -> world.getLightLevel(LightType.SKY, p) >= 15);
              long waited = world.getTime() - state[0];
              if ((!lit || state[6] < 0) && waited < 6000) return;
              state[6] = 0;
              LOGGER.info(
                  "[icbm-benchmark] r={} crater floor sky light {} after {} ticks (probes {})",
                  radius,
                  lit ? "reached 15" : "still incomplete",
                  waited,
                  probes.stream().map(p -> p.toShortString() + "=" + world.getLightLevel(LightType.SKY, p)).toList());
              ExtremePayloadGameTests.release(world, area);
              area.clear();
              long loadStart = System.nanoTime();
              area.addAll(ExtremePayloadGameTests.forceLoad(world, budgeted.x, budgeted.z, reach));
              LOGGER.info(
                  "[icbm-benchmark] next {}-chunk area loaded in {} ms after the crater",
                  area.size(),
                  ms(System.nanoTime() - loadStart));
              ExtremePayloadGameTests.fillStone(world, area, world.getBottomSectionCoord(), 3);
              phase[0] = 3;
            }
            case 3 -> {
              state[1] = world.getTime();
              long start = System.nanoTime();
              Payloads.detonateScaled(world, budgeted, PayloadType.ANTIMATTER, null, radius);
              state[2] = System.nanoTime() - start;
              phase[0] = 4;
            }
            case 4 -> {
              state[2] = Math.max(state[2], lastTick(world));
              if (Payloads.queuedCount(world) > 0) return;
              LOGGER.info(
                  "[icbm-benchmark] r={} with the {} ms production budget: done {} tick(s) after"
                      + " detonation, longest tick {} ms",
                  radius,
                  Scar.tickBudgetNanos / 1_000_000,
                  world.getTime() - state[1],
                  ms(state[2]));
              ExtremePayloadGameTests.release(world, area);
              area.clear();
              int limit = PayloadConfig.forType(PayloadType.REDMATTER).radius();
              area.addAll(
                  ExtremePayloadGameTests.forceLoad(world, core.x, core.z, (limit >> 4) + 1));
              ExtremePayloadGameTests.fillStone(world, area, world.getBottomSectionCoord(), 3);
              phase[0] = 5;
            }
            case 5 -> {
              RedmatterEntity entity = new RedmatterEntity(SimulationContent.REDMATTER, world);
              entity.refreshPositionAndAngles(core.x, core.y, core.z, 0, 0);
              world.spawnEntity(entity);
              singularity[0] = entity;
              state[3] = world.getTime();
              state[4] = 0;
              state[5] = 0;
              phase[0] = 6;
            }
            default -> {
              RedmatterEntity entity = singularity[0];
              long tick = lastTick(world);
              long age = world.getTime() - state[3];
              // The first ticks still carry the preparation's chunk saving and lighting.
              if (age > 2) {
                state[4] = Math.max(state[4], tick);
                state[5] += tick;
                if (tick > 50_000_000L) state[6]++;
              }
              boolean done = entity.horizonRadius() >= entity.horizonLimit();
              if (age % 200 == 0 || done || age >= 4000) {
                LOGGER.info(
                    "[icbm-benchmark] red matter after {} ticks: horizon {}/{}, size {}, debris"
                        + " captured {}, longest tick {} ms, mean tick {} ms, ticks over 50 ms {}",
                    age,
                    entity.horizonRadius(),
                    entity.horizonLimit(),
                    String.format(Locale.ROOT, "%.1f", entity.size()),
                    entity.capturedBlocks(),
                    ms(state[4]),
                    ms(state[5] / Math.max(1, age - 2)),
                    state[6]);
              }
              if (!done && age < 4000) return;
              // A backed-up light thread stalls chunk loading; time a fresh 5x5 load to prove it is not.
              long loadStart = System.nanoTime();
              var probe = ExtremePayloadGameTests.forceLoad(world, core.x + 2048, core.z, 2);
              LOGGER.info(
                  "[icbm-benchmark] fresh 25-chunk load right after red matter: {} ms",
                  ms(System.nanoTime() - loadStart));
              ExtremePayloadGameTests.release(world, probe);
              Payloads.removeNear(world, core, 400);
              ExtremePayloadGameTests.release(world, area);
              c.complete();
            }
          }
        });
  }
}
