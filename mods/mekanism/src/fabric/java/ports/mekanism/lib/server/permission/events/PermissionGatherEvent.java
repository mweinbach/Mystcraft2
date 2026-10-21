/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.server.permission.events;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import ports.mekanism.lib.eventbus.api.Event;
import ports.mekanism.lib.server.permission.nodes.PermissionNode;

public class PermissionGatherEvent extends Event {

    public static class Nodes extends PermissionGatherEvent {

        private final Set<PermissionNode<?>> nodes = new HashSet<>();

        public Collection<PermissionNode<?>> getNodes() {
            return Collections.unmodifiableSet(nodes);
        }

        public void addNodes(PermissionNode<?>... nodes) {
            Collections.addAll(this.nodes, nodes);
        }

        public void addNodes(Iterable<PermissionNode<?>> nodes) {
            nodes.forEach(this.nodes::add);
        }
    }
}
