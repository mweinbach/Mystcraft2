package net.mystcraft.minecraft.age.generation;

/** Read-only loader mixin contract; never expands vanilla's permitted generation region. */
public interface RegionWriteBounds {
    int mystcraft$getWriteRadiusCutoff();
}
