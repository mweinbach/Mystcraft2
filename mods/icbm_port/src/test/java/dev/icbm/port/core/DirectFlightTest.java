package dev.icbm.port.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DirectFlightTest {
  @Test
  void diagonalFlightPreservesConfiguredSpeed() {
    var velocity = DirectFlight.velocity(3, -4, 12, 1.5);
    assertEquals(1.5, Math.hypot(Math.hypot(velocity.x(), velocity.y()), velocity.z()), 1e-12);
    assertTrue(velocity.x() > 0 && velocity.y() < 0 && velocity.z() > 0);
  }

  @Test
  void coincidentTargetDoesNotProduceNaN() {
    assertThrows(IllegalArgumentException.class, () -> DirectFlight.velocity(0, 0, 0, 1.5));
  }

  @Test
  void invalidMotionIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> DirectFlight.velocity(Double.NaN, 0, 1, 1.5));
    assertThrows(
        IllegalArgumentException.class,
        () -> DirectFlight.velocity(1, 0, 1, Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> DirectFlight.velocity(1, 0, 1, -1));
  }

  @Test
  void largeCoordinatesDoNotOverflowNormalization() {
    var velocity = DirectFlight.velocity(1e200, 0, 1e200, 1.5);
    assertEquals(1.5, Math.hypot(velocity.x(), velocity.z()), 1e-12);
  }
}
