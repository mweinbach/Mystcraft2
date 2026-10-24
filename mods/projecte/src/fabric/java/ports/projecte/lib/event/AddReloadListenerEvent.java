/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import ports.projecte.lib.eventbus.api.Event;

/**
 * Collects server data reload listeners; they run after vanilla's for every data pack (re)load.
 */
public class AddReloadListenerEvent extends Event {

    private final List<PreparableReloadListener> listeners = new ArrayList<>();
    private final ReloadableServerResources serverResources;

    public AddReloadListenerEvent(ReloadableServerResources serverResources) {
        this.serverResources = serverResources;
    }

    public void addListener(PreparableReloadListener listener) {
        listeners.add(listener);
    }

    public List<PreparableReloadListener> getListeners() {
        return List.copyOf(listeners);
    }

    public ReloadableServerResources getServerResources() {
        return serverResources;
    }
}
