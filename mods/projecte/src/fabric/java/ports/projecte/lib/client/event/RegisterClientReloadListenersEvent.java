/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.event;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import ports.projecte.lib.eventbus.api.Event;

/**
 * Fired once on the mod buses during client setup. Listeners run after vanilla's client reload listeners, in
 * registration order.
 */
public class RegisterClientReloadListenersEvent extends Event {

    private final String namespace;
    private int count;

    public RegisterClientReloadListenersEvent(String namespace) {
        this.namespace = namespace;
    }

    public void registerReloadListener(PreparableReloadListener reloadListener) {
        ResourceLocation id = new ResourceLocation(namespace, "client_reload_listener_" + count++);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new Identified(id, reloadListener));
    }

    private record Identified(ResourceLocation id, PreparableReloadListener listener) implements IdentifiableResourceReloadListener {

        @Override
        public ResourceLocation getFabricId() {
            return id;
        }

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
              Executor backgroundExecutor, Executor gameExecutor) {
            return listener.reload(barrier, manager, prepareProfiler, applyProfiler, backgroundExecutor, gameExecutor);
        }

        @Override
        public String getName() {
            return listener.getName();
        }
    }
}
