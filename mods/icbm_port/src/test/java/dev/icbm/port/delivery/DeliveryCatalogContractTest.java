package dev.icbm.port.delivery;

import static org.junit.jupiter.api.Assertions.*;

import dev.icbm.port.payload.PayloadType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Pure-Java regression contract against upstream ExplosiveInit and ConfigFuseTimes. */
final class DeliveryCatalogContractTest {
  @Test
  void onlyUpstreamTierOnePayloadsHaveGrenadeForms() {
    Set<String> actual =
        Arrays.stream(PayloadType.values())
            .filter(p -> p.tier() == 1 && p.hasContent())
            .map(PayloadType::id)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "condensed",
            "shrapnel",
            "incendiary",
            "debilitation",
            "chemical",
            "anvil",
            "repulsive",
            "attractive",
            "colors",
            "smoke"),
        actual);
  }

  @Test
  void fieldPayloadFusesRemainDistinctFromInstantBlockDetonation() {
    assertEquals(100, PayloadType.CONDENSED.cartFuseTicks());
    assertEquals(100, PayloadType.CONDENSED.grenadeFuseTicks());
    assertEquals(120, PayloadType.ATTRACTIVE.grenadeFuseTicks());
    assertEquals(120, PayloadType.REPULSIVE.cartFuseTicks());
  }
}
