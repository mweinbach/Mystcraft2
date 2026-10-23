package ports.jurassicraft.gametest;

import java.util.Collection;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

/** Registers the shared GameTests on Fabric, backed by the Transfer API and Fabric callbacks. */
public class JurassiCraftFabricGameTests {
    @GameTestGenerator
    public Collection<TestFunction> sharedTests() {
        TestPlatform.Installed.install(new FabricTestPlatform());
        return SharedGameTests.sharedTests();
    }
}
