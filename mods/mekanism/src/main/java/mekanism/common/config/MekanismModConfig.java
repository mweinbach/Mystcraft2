package mekanism.common.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import java.nio.file.Path;
import java.util.function.Function;
import mekanism.common.Mekanism;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Custom {@link ModConfig} implementation that allows for rerouting the server config from being in the world's folder to being in the normal config folder. This allows
 * for us to use the builtin sync support, without the extra hassle of having to explain to people where the config file is, or require people in single player to edit
 * the config each time they make a new world.
 */
public class MekanismModConfig extends ModConfig {

    private static final MekanismConfigFileTypeHandler MEK_TOML = new MekanismConfigFileTypeHandler();

    private final IMekanismConfig mekanismConfig;

    public MekanismModConfig(ModContainer container, IMekanismConfig config) {
        super(config.getConfigType(), config.getConfigSpec(), container, Mekanism.MOD_NAME + "/" + config.getFileName() + ".toml");
        this.mekanismConfig = config;
    }

    @Override
    public ConfigFileTypeHandler getHandler() {
        return MEK_TOML;
    }

    public void clearCache() {
        mekanismConfig.clearCache();
    }

    private static class MekanismConfigFileTypeHandler extends ConfigFileTypeHandler {

        private static Path getPath(Path configBasePath) {
            //Intercept server config path reading for Mekanism configs and reroute it to the normal config directory
            if (configBasePath.endsWith("serverconfig")) {
                return FMLPaths.CONFIGDIR.get();
            }
            return configBasePath;
        }

        @Override
        public Function<ModConfig, CommentedFileConfig> reader(Path configBasePath) {
            Function<ModConfig, CommentedFileConfig> reader = super.reader(getPath(configBasePath));
            // Forge registers the watch before it corrects or saves. A GameTest that calls ConfigValue.set
            // autosaves on this thread while the watcher reloads the same file. Those two writes tear
            // additions.toml and world.toml ("table declared twice", ConcurrentModificationException).
            // The walkie test hits this directly: it sets VoicePort to a free port, the watcher reloads a
            // torn additions.toml, and the voice socket binds the default 36123. The Forge GameTest run
            // sets this property and has already written a complete file, so nothing needs the watch.
            // Production runs leave it in place.
            if (!Boolean.getBoolean("mekanism.gametest.skipConfigWatcher")) {
                return reader;
            }
            return config -> {
                CommentedFileConfig loaded = reader.apply(config);
                FileWatcher.defaultInstance().removeWatch(loaded.getNioPath());
                return loaded;
            };
        }

        @Override
        public void unload(Path configBasePath, ModConfig config) {
            if (Boolean.getBoolean("mekanism.gametest.skipConfigWatcher")) {
                // reader() already dropped the watch. Removing it again looks up a directory
                // NightConfig has forgotten and throws.
                return;
            }
            super.unload(getPath(configBasePath), config);
        }
    }
}