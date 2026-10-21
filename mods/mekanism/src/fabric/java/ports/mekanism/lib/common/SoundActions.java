/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common;

/**
 * Names for the sounds a fluid type plays.
 */
public record SoundActions(String name) {

    public static final SoundActions BUCKET_FILL = new SoundActions("bucket_fill");
    public static final SoundActions BUCKET_EMPTY = new SoundActions("bucket_empty");
    public static final SoundActions FLUID_VAPORIZE = new SoundActions("fluid_vaporize");
}
