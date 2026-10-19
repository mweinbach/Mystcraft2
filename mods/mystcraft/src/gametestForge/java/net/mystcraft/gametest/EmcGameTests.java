package net.mystcraft.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.mystcraft.integration.MystcraftBootstrap;

@GameTestHolder(MystcraftBootstrap.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmcGameTests {
    private EmcGameTests() {}

    @GameTest(template = "village/archivist_house", templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void everyItemHasAnEmcDecision(GameTestHelper h) {
        List<String> failures = EmcAudit.run(h.getLevel().getServer(), MystcraftBootstrap.MOD_ID);
        if (!failures.isEmpty()) h.fail(EmcAudit.summary(failures));
        h.succeed();
    }
}
