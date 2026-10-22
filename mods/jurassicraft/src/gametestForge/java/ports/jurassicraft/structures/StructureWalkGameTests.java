package ports.jurassicraft.structures;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

/** Full visitor-centre, paddock and village walk. Paddock and village gaps are logged, not failed. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureWalkGameTests {
    private StructureWalkGameTests() {}

    @GameTest(template = "park_structure_test", templateNamespace = PortMod.MOD_ID, timeoutTicks = 72000, batch = "structure_walk")
    public static void walkLargeStructures(GameTestHelper helper) {
        List<String> failures = StructureWalkAudit.failures(helper.getLevel());
        if (!failures.isEmpty())
            helper.fail(String.join(" | ", failures.subList(0, Math.min(failures.size(), 6))));
        helper.succeed();
    }
}
