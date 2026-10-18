package dev.icbm.port.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GameFlightPathTest {
  @Test
  void arcReachesEndpointsAndClampsProgress() {
    var start = new GameFlightPath.Point(0, 64, 0);
    var end = new GameFlightPath.Point(100, 80, -20);
    assertEquals(start, GameFlightPath.sample(start, end, 20, -1));
    assertEquals(end, GameFlightPath.sample(start, end, 20, 2));
    assertEquals(new GameFlightPath.Point(50, 92, -10), GameFlightPath.sample(start, end, 20, .5));
  }

  @Test
  void rejectsCorruptCoordinates() {
    var p = new GameFlightPath.Point(0, 0, 0);
    assertThrows(IllegalArgumentException.class, () -> GameFlightPath.sample(p, p, 1, Double.NaN));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            GameFlightPath.sample(
                p, new GameFlightPath.Point(Double.POSITIVE_INFINITY, 0, 0), 1, .5));
    assertThrows(IllegalArgumentException.class, () -> GameFlightPath.sample(p, p, -1, .5));
  }
}
