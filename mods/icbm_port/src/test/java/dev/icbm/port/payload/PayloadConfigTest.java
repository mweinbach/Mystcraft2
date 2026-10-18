package dev.icbm.port.payload;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PayloadConfigTest {
  @Test
  void partialConfigKeepsDefaultsAndAppliesIndependentFields() {
    var config =
        PayloadConfig.parse(
            "{\"shrapnel\":{\"fragments\":7,\"entityDamage\":4},\"nuclear\":{\"enabled\":false},\"condensed\":{\"blockFuseTicks\":42}}");
    assertEquals(7, config.get(PayloadType.SHRAPNEL).fragments());
    assertEquals(4, config.get(PayloadType.SHRAPNEL).entityDamage());
    assertFalse(config.get(PayloadType.NUCLEAR).enabled());
    assertEquals(42, config.get(PayloadType.CONDENSED).blockFuseTicks());
    assertEquals(100, config.get(PayloadType.CONDENSED).cartFuseTicks());
    assertEquals(55, config.get(PayloadType.ANTIMATTER).radius());
  }

  @Test
  void unsafeOrMistypedConfigIsRejected() {
    for (String json :
        new String[] {
          "[]",
          "{\"typo\":{}}",
          "{\"nuclear\":{\"radius\":81}}",
          "{\"shrapnel\":{\"fragments\":129}}",
          "{\"smoke\":{\"durationTicks\":-1}}",
          "{\"condensed\":{\"radius\":1.5}}",
          "{\"emp\":{\"enabled\":\"false\"}}",
          "{\"emp\":{\"typo\":1}}",
          "{\"nuclear\":{\"energy\":\"NaN\"}}"
        }) {
      assertThrows(RuntimeException.class, () -> PayloadConfig.parse(json), json);
    }
  }

  @Test
  void failedParsingCannotMutateActiveConfiguration() {
    var before = PayloadConfig.forType(PayloadType.SHRAPNEL);
    assertThrows(
        RuntimeException.class,
        () ->
            PayloadConfig.parse("{\"shrapnel\":{\"fragments\":0},\"nuclear\":{\"radius\":9999}}"));
    assertEquals(before, PayloadConfig.forType(PayloadType.SHRAPNEL));
  }
}
