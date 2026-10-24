/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.fml.event.config.ModConfigEvent;

/**
 * Every config the port's modules registered. Client and common configs load at startup; server configs load when a
 * server starts and are sent to joining clients, which use the server's values until they disconnect.
 */
public final class ConfigTracker {

    public static final ConfigTracker INSTANCE = new ConfigTracker();

    private final Map<String, ModConfig> fileMap = new ConcurrentHashMap<>();
    private final Map<ModConfig.Type, List<ModConfig>> configSets = new EnumMap<>(ModConfig.Type.class);

    private ConfigTracker() {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            configSets.put(type, new CopyOnWriteArrayList<>());
        }
    }

    public void trackConfig(ModConfig config) {
        if (fileMap.putIfAbsent(config.getFileName(), config) != null) {
            throw new IllegalStateException("Config file " + config.getFileName() + " is already registered");
        }
        configSets.get(config.getType()).add(config);
    }

    public List<ModConfig> configs(ModConfig.Type type) {
        return List.copyOf(configSets.get(type));
    }

    public void loadConfigs(ModConfig.Type type, Path configBasePath) {
        for (ModConfig config : configSets.get(type)) {
            loadConfig(config, configBasePath);
        }
    }

    /**
     * Opens one config file. A second call leaves the already loaded file in place, so biome modifications can read
     * server values during {@code MinecraftServer} construction and the later server-starting load does not reopen it.
     */
    public void loadConfig(ModConfig config, Path configBasePath) {
        if (config.getConfigData() != null) {
            return;
        }
        CommentedFileConfig configData = config.getHandler().reader(configBasePath).apply(config);
        config.setConfigData(configData);
        config.fireEvent(new ModConfigEvent.Loading(config));
    }

    public void unloadConfigs(ModConfig.Type type, Path configBasePath) {
        for (ModConfig config : configSets.get(type)) {
            if (config.getConfigData() != null) {
                config.getHandler().unload(configBasePath, config);
                config.setConfigData(null);
                config.fireEvent(new ModConfigEvent.Unloading(config));
            }
        }
    }

    /**
     * The file contents of every loaded server config, keyed by file name, for sending to a client.
     */
    public Map<String, byte[]> syncConfigs() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (ModConfig config : configSets.get(ModConfig.Type.SERVER)) {
            if (config.getConfigData() instanceof CommentedFileConfig fileConfig) {
                try {
                    result.put(config.getFileName(), Files.readAllBytes(fileConfig.getNioPath()));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read server config " + config.getFileName(), e);
                }
            }
        }
        return result;
    }

    public void receiveSyncedConfig(String fileName, byte[] bytes) {
        ModConfig config = fileMap.get(fileName);
        if (config != null && config.getType() == ModConfig.Type.SERVER) {
            config.acceptSyncedConfig(bytes);
        }
    }

    @Nullable
    public ModConfig byFileName(String fileName) {
        return fileMap.get(fileName);
    }

    /**
     * Forgets the server values a client received, when it leaves the server.
     */
    public void clearSyncedConfigs() {
        List<ModConfig> synced = new ArrayList<>();
        for (ModConfig config : configSets.get(ModConfig.Type.SERVER)) {
            if (config.getConfigData() != null && !(config.getConfigData() instanceof CommentedFileConfig)) {
                synced.add(config);
            }
        }
        for (ModConfig config : synced) {
            config.setConfigData(null);
            config.fireEvent(new ModConfigEvent.Unloading(config));
        }
    }
}
