/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.forge;

import com.electronwill.nightconfig.core.file.FileWatcher;
import java.io.IOException;
import java.util.List;
import mekanism.common.Mekanism;
import mekanism.gametest.SharedGameTests;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Registers the shared GameTests on Forge. A generator keeps their full {@code namespace:path} templates and Fabric's
 * {@code class.method} names, which Forge's {@code @GameTest} handling would prefix again.
 */
@GameTestHolder(Mekanism.MODID)
public class ForgeGameTests {

    @GameTestGenerator
    public static List<TestFunction> sharedTests() {
        // Config correction autosaves while NightConfig's watcher reloads the same file. A torn
        // additions.toml comes back as the default voice port, and a torn world.toml fails unrelated tests.
        try {
            FileWatcher.defaultInstance().stop();
        } catch (IOException ignored) {
        }
        return SharedGameTests.sharedTests();
    }
}
