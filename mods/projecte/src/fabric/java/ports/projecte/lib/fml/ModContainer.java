/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml;

import java.util.EnumMap;
import java.util.Map;
import ports.projecte.lib.eventbus.api.EventBus;
import ports.projecte.lib.eventbus.api.IEventBus;
import ports.projecte.lib.fml.config.ModConfig;
import ports.projecte.lib.forgespi.language.IModInfo;
import ports.projecte.platform.ModMetadata;

/**
 * An installed mod. Containers of the port's own modules also own a mod event bus.
 */
public class ModContainer {

    private final IModInfo modInfo;
    private final IEventBus eventBus;
    private final Map<ModConfig.Type, ModConfig> configs = new EnumMap<>(ModConfig.Type.class);

    public ModContainer(ModMetadata metadata) {
        this.modInfo = new IModInfo(metadata);
        this.eventBus = new EventBus(metadata.modId());
    }

    public String getModId() {
        return modInfo.getModId();
    }

    public String getNamespace() {
        return getModId();
    }

    public IModInfo getModInfo() {
        return modInfo;
    }

    public IEventBus getEventBus() {
        return eventBus;
    }

    public void addConfig(ModConfig modConfig) {
        configs.put(modConfig.getType(), modConfig);
    }

    public Map<ModConfig.Type, ModConfig> getConfigs() {
        return configs;
    }
}
