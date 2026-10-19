package ports.jurassicraft.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmcGameTests {
    private EmcGameTests() {}

    @GameTest(template = "mural_test", templateNamespace = PortMod.MOD_ID)
    public static void everyItemHasAnEmcDecision(GameTestHelper h) {
        List<String> failures = EmcAudit.run(h.getLevel().getServer(), PortMod.MOD_ID);
        if (!failures.isEmpty()) h.fail(EmcAudit.summary(failures));
        h.succeed();
    }
}
