/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Something a tool can do, such as stripping logs. Actions with the same name are the same instance.
 */
public final class ToolAction {

    private static final Map<String, ToolAction> ACTIONS = new ConcurrentHashMap<>();

    private final String name;

    private ToolAction(String name) {
        this.name = name;
    }

    public static Collection<ToolAction> getActions() {
        return Collections.unmodifiableCollection(ACTIONS.values());
    }

    public static ToolAction get(String name) {
        return ACTIONS.computeIfAbsent(name, ToolAction::new);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "ToolAction[" + name + "]";
    }
}
