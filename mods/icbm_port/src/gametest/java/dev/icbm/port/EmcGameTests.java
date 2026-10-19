package dev.icbm.port;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public final class EmcGameTests implements FabricGameTest {
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void everyItemHasAnEmcDecision(TestContext c) {
    List<String> failures = EmcAudit.run(c.getWorld().getServer());
    if (!failures.isEmpty()) c.throwGameTestException(EmcAudit.summary(failures));
    c.complete();
  }
}
