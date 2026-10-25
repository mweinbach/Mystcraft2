package dev.icbm.port.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FragmentBlastTest {
  @Test
  void explosiveBurstsRespectTheSharedTotalCap() {
    // A full world of non-explosive fragments leaves no room for an explosive burst either.
    assertEquals(0, FragmentBlast.admitted(225, FragmentBlast.MAX_LIVE_FRAGMENTS, 0, true));
    // Near the total cap, the explosive burst is bounded by the remaining total capacity.
    assertEquals(10, FragmentBlast.admitted(225, FragmentBlast.MAX_LIVE_FRAGMENTS - 10, 0, true));
  }

  @Test
  void explosiveBurstsRespectTheirOwnCap() {
    assertEquals(
        24,
        FragmentBlast.admitted(225, 2000, FragmentBlast.MAX_LIVE_EXPLOSIVE - 24, true));
    // Non-explosive bursts ignore the explosive count.
    assertEquals(
        225, FragmentBlast.admitted(225, 2000, FragmentBlast.MAX_LIVE_EXPLOSIVE, false));
  }
}
