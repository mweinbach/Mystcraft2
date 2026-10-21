package mekanism.common.config;

import java.nio.file.Path;
import mekanism.common.Mekanism;
import ports.mekanism.lib.fml.ModContainer;
import ports.mekanism.lib.fml.loading.FMLPaths;

public class MekanismConfigHelper {

    private MekanismConfigHelper() {
    }

    public static final Path CONFIG_DIR = FMLPaths.getOrCreateGameRelativePath(FMLPaths.CONFIGDIR.get().resolve(Mekanism.MOD_NAME), Mekanism.MOD_NAME);

    /**
     * Creates a mod config so that {@link ports.mekanism.lib.fml.config.ConfigTracker} will track it and sync server configs from server to client.
     */
    public static void registerConfig(ModContainer modContainer, IMekanismConfig config) {
        MekanismModConfig modConfig = new MekanismModConfig(modContainer, config);
        if (config.addToContainer()) {
            modContainer.addConfig(modConfig);
        }
    }
}