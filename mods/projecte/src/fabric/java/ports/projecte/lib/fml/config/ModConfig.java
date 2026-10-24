/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.common.ForgeConfigSpec;
import ports.projecte.lib.fml.ModContainer;
import ports.projecte.lib.fml.event.config.ModConfigEvent;

public class ModConfig {

    public enum Type {
        COMMON,
        CLIENT,
        SERVER;

        public String extension() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Type type;
    private final ForgeConfigSpec spec;
    private final ModContainer container;
    private final String fileName;
    @Nullable
    private CommentedConfig configData;

    public ModConfig(Type type, ForgeConfigSpec spec, ModContainer container, String fileName) {
        this.type = type;
        this.spec = spec;
        this.container = container;
        this.fileName = fileName;
        ConfigTracker.INSTANCE.trackConfig(this);
    }

    public ModConfig(Type type, ForgeConfigSpec spec, ModContainer container) {
        this(type, spec, container, String.format(Locale.ROOT, "%s-%s.toml", container.getModId(), type.extension()));
    }

    public Type getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public ForgeConfigSpec getSpec() {
        return spec;
    }

    public String getModId() {
        return container.getModId();
    }

    public ConfigFileTypeHandler getHandler() {
        return ConfigFileTypeHandler.TOML;
    }

    @Nullable
    public CommentedConfig getConfigData() {
        return configData;
    }

    public Path getFullPath() {
        return configData instanceof FileConfig file ? file.getNioPath() : Path.of(fileName);
    }

    void setConfigData(@Nullable CommentedConfig configData) {
        this.configData = configData;
        spec.acceptConfig(configData);
    }

    void fireEvent(ModConfigEvent event) {
        container.getEventBus().post(event);
    }

    /**
     * Replaces a server config's values with the copy the server sent.
     */
    public void acceptSyncedConfig(byte[] bytes) {
        setConfigData(TomlFormat.instance().createParser().parse(new ByteArrayInputStream(bytes)));
        fireEvent(new ModConfigEvent.Reloading(this));
    }
}
