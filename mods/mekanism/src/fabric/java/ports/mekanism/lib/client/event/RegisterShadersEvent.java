/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired on the mod buses each time the game renderer reloads its core shaders. Each registered shader is handed to
 * its callback once every shader has loaded.
 */
public class RegisterShadersEvent extends Event {

    private final ResourceManager resourceManager;
    private final List<Pair<ShaderInstance, Consumer<ShaderInstance>>> shaderList;

    public RegisterShadersEvent(ResourceManager resourceManager, List<Pair<ShaderInstance, Consumer<ShaderInstance>>> shaderList) {
        this.resourceManager = resourceManager;
        this.shaderList = shaderList;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public void registerShader(ShaderInstance shaderInstance, Consumer<ShaderInstance> onLoaded) {
        shaderList.add(Pair.of(shaderInstance, onLoaded));
    }
}
