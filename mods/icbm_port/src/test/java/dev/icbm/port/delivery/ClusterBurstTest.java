package dev.icbm.port.delivery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Geometry contract against upstream ActionCluster / RotationHelper (MIT ICBM-Classic). */
final class ClusterBurstTest {
  private static final double EPS = 1e-3;

  @Test
  void horizontalHeadingEjectsDiscPerpendicularToFlight() {
    // Missile flying +Z (yaw 0, pitch 0); no randomness.
    List<ClusterBurst.Ejection> plan = ClusterBurst.plan(10, 0, 0, () -> 0.5);
    assertEquals(10, plan.size());
    ClusterBurst.Ejection first = plan.get(0);
    // Upstream consumes the spawn list from the end.
    assertEquals(9, first.entry());
    // Slot 0: SOUTH rotated by pitch-90 = straight up, offset 0.25.
    assertEquals(0, first.offsetX(), EPS);
    assertEquals(0.25, first.offsetY(), EPS);
    assertEquals(0, first.offsetZ(), EPS);
    // Motion = (source + back) * 0.3 with back = rotateX(UP, -22.5) = (0, .924, -.383).
    assertEquals(0, first.motionX(), EPS);
    assertEquals((1 + Math.cos(Math.toRadians(22.5))) * 0.3, first.motionY(), EPS);
    assertEquals(-Math.sin(Math.toRadians(22.5)) * 0.3, first.motionZ(), EPS);
    // Every first-disc droplet sits on the XY plane, 0.25 from the burst point.
    for (ClusterBurst.Ejection e : plan) {
      assertEquals(0, e.offsetZ(), EPS);
      assertEquals(0.25, Math.hypot(e.offsetX(), e.offsetY()), EPS);
    }
  }

  @Test
  void laterDiscsRotateStackAndSpeedUp() {
    List<ClusterBurst.Ejection> plan = ClusterBurst.plan(21, 0, -90, () -> 0.5);
    assertEquals(21, plan.size());
    assertEquals(20, plan.get(0).entry());
    assertEquals(0, plan.get(20).entry());
    // Downward heading: discs are horizontal and later discs stack back up the flight path
    // (0, +1, 0), 0.1 per disc, as a level flight stacks them back along -heading.
    assertEquals(0, plan.get(0).offsetY(), EPS);
    assertEquals(0.1, plan.get(10).offsetY(), EPS);
    assertEquals(0.2, plan.get(20).offsetY(), EPS);
    // Second disc starts 22.5 degrees around from the first.
    double a0 = Math.atan2(plan.get(0).offsetZ(), plan.get(0).offsetX());
    double a1 = Math.atan2(plan.get(10).offsetZ(), plan.get(10).offsetX());
    assertEquals(22.5, Math.abs(Math.toDegrees(a1 - a0)), 0.01);
    // Motion = (source + back) * (0.3 + 0.1 * disc).
    double backY = Math.cos(Math.toRadians(22.5));
    double backZ = -Math.sin(Math.toRadians(22.5));
    for (int i = 0; i < plan.size(); i++) {
      var e = plan.get(i);
      int disc = i / 10;
      double scale = 0.3 + 0.1 * disc;
      double sx = e.offsetX() / 0.25;
      double sy = (e.offsetY() - 0.1 * disc) / 0.25;
      double sz = e.offsetZ() / 0.25;
      assertEquals(sx * scale, e.motionX(), EPS);
      assertEquals((sy + backY) * scale, e.motionY(), EPS);
      assertEquals((sz + backZ) * scale, e.motionZ(), EPS);
    }
  }

  @Test
  void randomSpreadIsBoundedByLayerScale() {
    double[] values = {0.99, 0.0};
    int[] i = {0};
    var plan = ClusterBurst.plan(1, 0, 0, () -> values[i[0]++ % 2]);
    var still = ClusterBurst.plan(1, 0, 0, () -> 0.5).get(0);
    assertEquals(0.099, plan.get(0).motionX() - still.motionX(), EPS);
    assertEquals(0.099, plan.get(0).motionY() - still.motionY(), EPS);
    assertEquals(0.099, plan.get(0).motionZ() - still.motionZ(), EPS);
  }

  @Test
  void headingAnglesFollowUpstreamProjectileConvention() {
    assertEquals(0, ClusterBurst.headingYaw(0, 0, 1), EPS);
    assertEquals(90, ClusterBurst.headingYaw(1, 0, 0), EPS);
    assertEquals(-90, ClusterBurst.headingPitch(0, -1, 0), EPS);
    assertTrue(ClusterBurst.plan(0, 0, 0, () -> 0.5).isEmpty());
  }

  /** Review fix: discs stay perpendicular to diagonal and pitched headings too. */
  @Test
  void discsArePerpendicularToAnyHeadingAndStackBackwards() {
    double[][] headings = {
      {Math.sqrt(.5), 0, Math.sqrt(.5)}, {-0.6, 0, 0.8}, {0.48, 0.6, -0.64}, {-0.3, -0.9, 0.316}
    };
    for (double[] h : headings) {
      double n = Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
      double hx = h[0] / n, hy = h[1] / n, hz = h[2] / n;
      var plan =
          ClusterBurst.plan(
              20,
              ClusterBurst.headingYaw(hx, hy, hz),
              ClusterBurst.headingPitch(hx, hy, hz),
              () -> 0.5);
      double[] mean = new double[3];
      for (int i = 0; i < 20; i++) {
        var e = plan.get(i);
        double along = e.offsetX() * hx + e.offsetY() * hy + e.offsetZ() * hz;
        // Disc 0 lies in the plane through the burst point; disc 1 is 0.1 further back.
        assertEquals(i < 10 ? 0 : -0.1, along, 2e-3, "offset along heading, droplet " + i);
        if (i >= 10) {
          mean[0] += e.offsetX() / 10;
          mean[1] += e.offsetY() / 10;
          mean[2] += e.offsetZ() / 10;
        }
      }
      assertEquals(-0.1 * hx, mean[0], 2e-3);
      assertEquals(-0.1 * hy, mean[1], 2e-3);
      assertEquals(-0.1 * hz, mean[2], 2e-3);
    }
  }
}
