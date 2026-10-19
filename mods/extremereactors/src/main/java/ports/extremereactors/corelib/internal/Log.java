/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.internal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public final class Log {

    public static final Logger LOGGER = LogManager.getLogger("bigreactors-corelib");

    public static final Marker CORE = MarkerManager.getMarker("corelib");
    public static final Marker MULTIBLOCK = MarkerManager.getMarker("multiblock").addParents(CORE);
    public static final Marker NETWORK = MarkerManager.getMarker("network").addParents(CORE);
    public static final Marker CONFIG = MarkerManager.getMarker("config").addParents(CORE);

    private Log() {
    }
}
