package dev.icbm.port.launcher;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Upstream MIT ICBM-Classic {@code LauncherCapability} launch rules. */
class LauncherRulesTest {
  @Test
  void minimumRangeNeedsBothAxesInsideTen() {
    assertTrue(LauncherRules.tooClose(0.5, 0.5, 9.5, -8.5));
    assertFalse(LauncherRules.tooClose(0.5, 0.5, 10.5, 0.5));
    assertFalse(LauncherRules.tooClose(0.5, 0.5, 0.5, -9.5 - 1));
  }

  @Test
  void maximumRangeIsASquare() {
    assertFalse(LauncherRules.tooFar(0, 0, 10_000, 10_000, 10_000));
    assertTrue(LauncherRules.tooFar(0, 0, 10_001, 0, 10_000));
    assertTrue(LauncherRules.tooFar(0, 0, 0, -10_001, 10_000));
  }

  @Test
  void cruiseMinimumIsTwentyBlocksStraightLine() {
    assertTrue(LauncherRules.cruiseTooClose(19.99));
    assertFalse(LauncherRules.cruiseTooClose(20));
  }

  @Test
  void inaccuracyMatchesUpstreamFormula() {
    assertEquals(2, LauncherRules.inaccuracy(0, 10_000, 1), 1e-9);
    assertEquals(12, LauncherRules.inaccuracy(10_000.0 * 10_000.0, 10_000, 1), 1e-9);
    assertEquals(2 + 2.5 + 3, LauncherRules.inaccuracy(5_000.0 * 5_000.0, 10_000, 4), 1e-9);
    assertEquals(2, LauncherRules.inaccuracy(0, 10_000, 0), 1e-9);
  }

  @Test
  void offsetStaysInsideTheInaccuracyCircle() {
    for (int yaw = 0; yaw < 360; yaw += 15) {
      double[] offset = LauncherRules.offset(6, 0.75, yaw);
      assertEquals(4.5, Math.hypot(offset[0], offset[1]), 1e-9);
    }
    double[] none = LauncherRules.offset(6, 0, 123);
    assertEquals(0, Math.hypot(none[0], none[1]), 1e-12);
  }

  @Test
  void settingsAreBounded() {
    assertEquals(3, LauncherRules.clampLockHeight(-4));
    assertEquals(64, LauncherRules.clampLockHeight(500));
    assertEquals(0, LauncherRules.clampFiringDelay(-1));
    assertEquals(1200, LauncherRules.clampFiringDelay(5000));
  }
}
