package dev.icbm.port.payload;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class PayloadTypeTest {
  @Test
  void pinnedUpstreamRegistryHasAllContentAndReservedSlots() {
    assertEquals(27, PayloadType.values().length);
    assertEquals(23, Arrays.stream(PayloadType.values()).filter(PayloadType::hasContent).count());
    assertEquals(10, Arrays.stream(PayloadType.values()).filter(p -> p.tier() == 1).count());
    Set<String> ids = new HashSet<>();
    for (PayloadType type : PayloadType.values()) {
      assertTrue(ids.add(type.id()), "Duplicate registry ID");
      assertEquals(type, PayloadType.fromId(type.id()));
      assertEquals(type.hasContent(), type.tier() > 0);
    }
  }

  @Test
  void legacyTimingDistinguishesDeliveryForms() {
    assertEquals(0, PayloadType.CONDENSED.blockFuseTicks());
    assertEquals(100, PayloadType.CONDENSED.cartFuseTicks());
    assertEquals(100, PayloadType.CONDENSED.grenadeFuseTicks());
    assertEquals(0, PayloadType.BREACHING.blockFuseTicks());
    assertEquals(300, PayloadType.ANTIMATTER.blockFuseTicks());
    assertEquals(120, PayloadType.ATTRACTIVE.blockFuseTicks());
  }
}
