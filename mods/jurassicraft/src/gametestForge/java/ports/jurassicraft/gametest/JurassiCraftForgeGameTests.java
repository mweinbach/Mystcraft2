package ports.jurassicraft.gametest;

import java.util.Collection;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;
import ports.jurassicraft.PortMod;

/** Registers the shared GameTests on Forge, backed by Forge capabilities and events. */
@GameTestHolder(PortMod.MOD_ID)
public class JurassiCraftForgeGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> sharedTests() {
        TestPlatform.Installed.install(new ForgeTestPlatform());
        return SharedGameTests.sharedTests();
    }
}
