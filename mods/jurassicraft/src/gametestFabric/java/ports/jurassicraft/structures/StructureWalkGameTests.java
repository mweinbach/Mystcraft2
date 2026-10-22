package ports.jurassicraft.structures;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Full visitor-centre, paddock and village walk. Paddock and village gaps are logged, not failed. */
public final class StructureWalkGameTests implements FabricGameTest {
    public StructureWalkGameTests() {}

    @GameTest(template = "jurassicraft:park_structure_test", timeoutTicks = 72000, batch = "structure_walk")
    public void walkLargeStructures(GameTestHelper helper) {
        List<String> failures = StructureWalkAudit.failures(helper.getLevel());
        if (!failures.isEmpty())
            helper.fail(String.join(" | ", failures.subList(0, Math.min(failures.size(), 6))));
        helper.succeed();
    }
}
