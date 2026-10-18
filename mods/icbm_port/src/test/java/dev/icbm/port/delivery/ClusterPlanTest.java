package dev.icbm.port.delivery;

import static org.junit.jupiter.api.Assertions.*;

import dev.icbm.port.payload.PayloadType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

final class ClusterPlanTest {
  @Test
  void planCopiesInputAndExposesImmutableMixedManifest() {
    List<PayloadType> input = new ArrayList<>(List.of(PayloadType.SMOKE, PayloadType.SHRAPNEL));
    ClusterPlan plan = new ClusterPlan(input);
    input.clear();
    assertEquals(List.of(PayloadType.SMOKE, PayloadType.SHRAPNEL), plan.payloads());
    assertThrows(
        UnsupportedOperationException.class, () -> plan.payloads().add(PayloadType.CONDENSED));
  }

  @Test
  void oversizedPlansCannotCreateUnboundedEntities() {
    assertEquals(64, new ClusterPlan(Collections.nCopies(1000, PayloadType.SMOKE)).size());
    assertTrue(new ClusterPlan(List.of(PayloadType.NUCLEAR, PayloadType.ROT)).isEmpty());
  }

  @Test
  void nbtPreservesMixedOrderAndRejectsMalformedPayloads() {
    ClusterPlan plan =
        new ClusterPlan(List.of(PayloadType.SMOKE, PayloadType.INCENDIARY, PayloadType.SMOKE));
    NbtCompound nbt = new NbtCompound();
    plan.writeTo(nbt);
    assertEquals(plan.payloads(), ClusterPlan.read(nbt).payloads());
    NbtList entries = new NbtList();
    NbtCompound invalid = new NbtCompound();
    invalid.putString("Payload", "missing");
    invalid.putInt("Count", Integer.MAX_VALUE);
    entries.add(invalid);
    NbtCompound valid = new NbtCompound();
    valid.putString("Payload", "smoke");
    valid.putInt("Count", Integer.MAX_VALUE);
    entries.add(valid);
    nbt.put(ClusterPlan.NBT_KEY, entries);
    assertEquals(Collections.nCopies(64, PayloadType.SMOKE), ClusterPlan.read(nbt).payloads());
  }
}
