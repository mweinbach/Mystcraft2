package ports.buildcraft.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.buildcraft.BuildCraft;

@GameTestHolder(BuildCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmcGameTests {
    private EmcGameTests() {}

    @GameTest(template = BCTests.TEMPLATE)
    public static void everyItemHasAnEmcDecision(GameTestHelper h) {
        List<String> failures = EmcAudit.run(h.getLevel().getServer(), BuildCraft.MOD_ID);
        if (!failures.isEmpty()) h.fail(EmcAudit.summary(failures));
        h.succeed();
    }
}
