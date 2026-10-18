package dev.icbm.port;

import dev.icbm.port.delivery.ClusterPlan;
import dev.icbm.port.delivery.DeliveryContent;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.payload.PayloadType;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class MissileGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean condition, String message) {
    if (!condition) c.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void targetedFlightContinuesAfterReload(TestContext c) {
    var world = c.getWorld();
    var start = c.getAbsolutePos(new BlockPos(2, 30, 2));
    var missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
    missile.setPosition(Vec3d.ofCenter(start));
    missile.setPayload(PayloadType.SMOKE);
    missile.configureTarget(start.east(6));
    missile.tick();
    var restored = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
    restored.readNbt(missile.writeNbt(new NbtCompound()));
    check(c, restored.getPayload() == PayloadType.SMOKE, "Payload must survive reload");
    missile.tick();
    restored.tick();
    check(
        c,
        restored.getPos().squaredDistanceTo(missile.getPos()) < 1e-8,
        "Arc must continue at saved progress");
    while (!restored.isRemoved()) restored.tick();
    check(
        c,
        restored.getPos().squaredDistanceTo(Vec3d.ofCenter(start.east(6))) < 1e-8,
        "Arc must reach target");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void empAndMixedManifestSurviveReload(TestContext c) {
    var missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, c.getWorld());
    missile.setCluster(true);
    missile.setClusterPlan(new ClusterPlan(List.of(PayloadType.SMOKE, PayloadType.SHRAPNEL)));
    missile.disableByEmp(200);
    var restored = new MissileEntity(IcbmPort.MISSILE_ENTITY, c.getWorld());
    restored.readNbt(missile.writeNbt(new NbtCompound()));
    check(
        c, restored.isEmpDisabled() && restored.isCluster(), "EMP and delivery mode must persist");
    check(
        c,
        restored
            .getClusterPlan()
            .payloads()
            .equals(List.of(PayloadType.SMOKE, PayloadType.SHRAPNEL)),
        "Mixed order must persist");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void clusterAirburstUsesPaidManifest(TestContext c) {
    var world = c.getWorld();
    var pos = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 30, 2)));
    var missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
    missile.setPosition(pos);
    missile.setCluster(true);
    missile.setClusterPlan(new ClusterPlan(List.of(PayloadType.SMOKE, PayloadType.COLOR)));
    for (int i = 0; i < 40; i++) missile.tick();
    check(c, missile.isRemoved(), "Cluster must release at airburst timer");
    var bomblets =
        world.getEntitiesByType(DeliveryContent.BOMBLET, new Box(pos, pos).expand(2), e -> true);
    check(c, bomblets.size() == 2, "Only loaded bomblets may spawn");
    bomblets.forEach(e -> e.discard());
    c.complete();
  }
}
