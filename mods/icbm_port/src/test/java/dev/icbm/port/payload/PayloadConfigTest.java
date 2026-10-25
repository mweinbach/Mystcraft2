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
    assertEquals(PayloadDefaults.ANTIMATTER_RADIUS, config.get(PayloadType.ANTIMATTER).radius());
  }

  @Test
  void antimatterAndRedmatterDefaultsAreDeliberatelyLarger() {
    var config = PayloadConfig.parse("{}");
    // Five times upstream's 55, the radius the measured tick cost allows (see PORTING-NOTES).
    assertEquals(275, PayloadDefaults.ANTIMATTER_RADIUS);
    assertEquals(275, config.get(PayloadType.ANTIMATTER).radius());
    assertEquals(300, config.get(PayloadType.REDMATTER).radius());
    assertTrue(
        config.get(PayloadType.REDMATTER).radius() >= config.get(PayloadType.ANTIMATTER).radius(),
        "Red matter's horizon must reach at least as far as antimatter");
    assertEquals(6000, config.get(PayloadType.REDMATTER).durationTicks());
    assertEquals(2000, config.get(PayloadType.REDMATTER).entityDamage());
    assertEquals(50, config.get(PayloadType.NUCLEAR).radius(), "Other families are unchanged");
  }

  @Test
  void onlyAntimatterAndRedmatterMayExceedEighty() {
    assertEquals(384, PayloadType.ANTIMATTER.maxRadius());
    assertEquals(384, PayloadType.REDMATTER.maxRadius());
    assertEquals(80, PayloadType.NUCLEAR.maxRadius());
    assertEquals(
        384,
        PayloadConfig.parse("{\"antimatter\":{\"radius\":384}}").get(PayloadType.ANTIMATTER).radius());
    assertEquals(
        40,
        PayloadConfig.parse("{\"redmatter\":{\"radius\":40}}").get(PayloadType.REDMATTER).radius());
    for (String json :
        new String[] {
          "{\"antimatter\":{\"radius\":385}}",
          "{\"redmatter\":{\"radius\":385}}",
          "{\"thermobaric\":{\"radius\":81}}",
          "{\"config_version\":4}",
          "{\"config_version\":0}",
          "{\"config_version\":\"2\"}"
        }) assertThrows(RuntimeException.class, () -> PayloadConfig.parse(json), json);
  }

  @Test
  void versionOneFilesMigrateOnlyUntouchedOldDefaults() {
    var old =
        PayloadConfig.parseAll(
            "{\"antimatter\":{\"radius\":55},\"redmatter\":{\"radius\":12,\"durationTicks\":2400},"
                + "\"nuclear\":{\"radius\":50}}");
    assertTrue(old.migrated());
    assertEquals(275, old.payloads().get(PayloadType.ANTIMATTER).radius());
    assertEquals(300, old.payloads().get(PayloadType.REDMATTER).radius());
    assertEquals(6000, old.payloads().get(PayloadType.REDMATTER).durationTicks());
    assertEquals(50, old.payloads().get(PayloadType.NUCLEAR).radius());

    var custom =
        PayloadConfig.parseAll(
            "{\"antimatter\":{\"radius\":30},\"redmatter\":{\"radius\":20,\"durationTicks\":600}}");
    assertTrue(custom.migrated(), "The file is still rewritten with a version");
    assertEquals(30, custom.payloads().get(PayloadType.ANTIMATTER).radius());
    assertEquals(20, custom.payloads().get(PayloadType.REDMATTER).radius());
    assertEquals(600, custom.payloads().get(PayloadType.REDMATTER).durationTicks());

    var current =
        PayloadConfig.parseAll("{\"config_version\":3,\"antimatter\":{\"radius\":55}}");
    assertFalse(current.migrated());
    assertEquals(55, current.payloads().get(PayloadType.ANTIMATTER).radius(), "v3 values are kept");

    var second =
        PayloadConfig.parseAll("{\"config_version\":2,\"antimatter\":{\"radius\":55}}");
    assertTrue(second.migrated());
    assertEquals(55, second.payloads().get(PayloadType.ANTIMATTER).radius(), "v2 antimatter is kept");

    var rewritten = PayloadConfig.parseAll(PayloadConfig.serialize(old));
    assertFalse(rewritten.migrated());
    assertEquals(old.payloads(), rewritten.payloads());
  }

  @Test
  void versionTwoFilesMoveRotMutationAndEnderToUpstreamSizes() {
    var defaults = PayloadConfig.parse("{}");
    assertEquals(3, defaults.get(PayloadType.ROT).radius());
    assertEquals(3, defaults.get(PayloadType.MUTATION).radius());
    assertEquals(20, defaults.get(PayloadType.ENDER).radius());
    var old =
        PayloadConfig.parseAll(
            "{\"config_version\":2,\"rot\":{\"radius\":80},\"mutation\":{\"radius\":80},"
                + "\"ender\":{\"radius\":30},\"exothermic\":{\"radius\":30}}");
    assertTrue(old.migrated());
    assertEquals(3, old.payloads().get(PayloadType.ROT).radius());
    assertEquals(3, old.payloads().get(PayloadType.MUTATION).radius());
    assertEquals(20, old.payloads().get(PayloadType.ENDER).radius());
    assertEquals(30, old.payloads().get(PayloadType.EXOTHERMIC).radius(), "unchanged family");
    var custom =
        PayloadConfig.parseAll(
            "{\"config_version\":2,\"rot\":{\"radius\":12},\"ender\":{\"radius\":25}}");
    assertEquals(12, custom.payloads().get(PayloadType.ROT).radius(), "edited values are kept");
    assertEquals(25, custom.payloads().get(PayloadType.ENDER).radius());
    var unversioned = PayloadConfig.parseAll("{\"rot\":{\"radius\":80}}");
    assertEquals(3, unversioned.payloads().get(PayloadType.ROT).radius(), "v1 also migrates");
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

  @Test
  void empEnergyDrainDefaultsOnAndAcceptsOverrides() {
    assertEquals(
        PayloadConfig.EmpEnergyDrain.DEFAULT,
        PayloadConfig.parseAll("{\"emp\":{\"radius\":40}}").empEnergyDrain());
    assertTrue(PayloadConfig.EmpEnergyDrain.DEFAULT.enabled());
    var parsed =
        PayloadConfig.parseAll(
            "{\"emp_energy_drain\":{\"enabled\":false,\"maxBlocks\":8},\"emp\":{\"radius\":40}}");
    assertFalse(parsed.empEnergyDrain().enabled());
    assertEquals(8, parsed.empEnergyDrain().maxBlocks());
    assertEquals(
        PayloadConfig.EmpEnergyDrain.DEFAULT.maxEntities(), parsed.empEnergyDrain().maxEntities());
    assertEquals(40, parsed.payloads().get(PayloadType.EMP).radius());
  }

  @Test
  void invalidEmpEnergyDrainIsRejected() {
    for (String json :
        new String[] {
          "{\"emp_energy_drain\":{\"enabled\":\"no\"}}",
          "{\"emp_energy_drain\":{\"maxBlocks\":-1}}",
          "{\"emp_energy_drain\":{\"maxBlocks\":8193}}",
          "{\"emp_energy_drain\":{\"maxEntities\":1.5}}",
          "{\"emp_energy_drain\":{\"radius\":4}}",
          "{\"emp_energy_drain\":true}"
        }) {
      assertThrows(RuntimeException.class, () -> PayloadConfig.parseAll(json), json);
    }
  }
}
