/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml.util.thread;

/**
 * Forge runs the logical server in its own thread group, and upstream compares {@code getThreadGroup()} against it.
 * Fabric does not, so the build rewrites those comparisons to {@link #isServerThread()}.
 */
public final class SidedThreadGroups {

    private SidedThreadGroups() {
    }

    public static boolean isServerThread() {
        return EffectiveSide.get().isServer();
    }
}
