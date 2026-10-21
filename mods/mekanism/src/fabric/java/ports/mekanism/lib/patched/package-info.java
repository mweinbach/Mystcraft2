/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
/**
 * Constructors and static methods Forge patches into vanilla classes. Java cannot add them to the vanilla classes, so
 * the Fabric build rewrites Mekanism's calls to point here; see the rewrite table in {@code fabric/build.gradle}.
 */
package ports.mekanism.lib.patched;
