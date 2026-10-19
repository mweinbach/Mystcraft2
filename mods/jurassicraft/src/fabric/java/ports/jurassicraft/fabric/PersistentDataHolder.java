package ports.jurassicraft.fabric;

import net.minecraft.nbt.CompoundTag;

/** Implemented on every entity by {@link ports.jurassicraft.fabric.mixin.EntityPersistentDataMixin}. */
public interface PersistentDataHolder {
    CompoundTag jurassicraft$persistentData();
}
