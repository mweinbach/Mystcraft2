/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.nbt;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public final class NBTHelper {

    public static <E extends Enum<E>> CompoundTag nbtSetEnum(final CompoundTag data, final String key, final E value) {

        data.putString(key, value.name());
        return data;
    }

    public static <E extends Enum<E>> E nbtGetEnum(final CompoundTag data, final String key,
                                                   final Function<String, E> parser, final E defaultValue) {

        if (data.contains(key)) {

            try {
                return parser.apply(data.getString(key));
            } catch (IllegalArgumentException ignored) {
                // unknown constant: fall back to the default
            }
        }

        return defaultValue;
    }

    public static CompoundTag nbtSetBlockPos(final CompoundTag data, final String key, final BlockPos position) {

        data.put(key, NbtUtils.writeBlockPos(position));
        return data;
    }

    public static BlockPos nbtGetBlockPos(final CompoundTag data, final String key) {
        return NbtUtils.readBlockPos(data.getCompound(key));
    }

    private NBTHelper() {
    }
}
