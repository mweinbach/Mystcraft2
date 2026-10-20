/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.computer;

import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helpers to read the arguments of a computer method call and to shape its results the way Lua scripts expect
 * them.
 */
public final class LuaHelper {

    public static int getIntFromArgs(final Object[] arguments, final int index) {

        final Object value = argument(arguments, index);

        if (value instanceof Number number) {

            final double asDouble = number.doubleValue();

            if (asDouble != Math.floor(asDouble) || asDouble < Integer.MIN_VALUE || asDouble > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(String.format("Argument #%d must be an integer", index + 1));
            }

            return (int) asDouble;
        }

        throw wrongType(index, "number", value);
    }

    public static int getIntFromArgs(final Object[] arguments, final int index, final int min, final int max) {

        final int value = getIntFromArgs(arguments, index);

        if (value < min || value > max) {
            raiseIllegalArgumentRange(index, min, max);
        }

        return value;
    }

    public static boolean getBooleanFromArgs(final Object[] arguments, final int index) {

        final Object value = argument(arguments, index);

        if (value instanceof Boolean flag) {
            return flag;
        }

        throw wrongType(index, "boolean", value);
    }

    public static String getStringFromArgs(final Object[] arguments, final int index) {

        final Object value = argument(arguments, index);

        if (value instanceof String text) {
            return text;
        }

        throw wrongType(index, "string", value);
    }

    public static void raiseIllegalArgumentRange(final int index, final int min, final int max) {
        throw new IllegalArgumentException(String.format("Argument #%d must be between %d and %d", index + 1, min, max));
    }

    /**
     * Turn a method result into values a computer understands: empty optionals become nil, positions become
     * {x, y, z} tables, enums become their names, and maps and collections are converted element by element.
     */
    public static Object toLua(final Object value) {

        if (value instanceof Optional<?> optional) {
            return optional.map(LuaHelper::toLua).orElse(null);
        }

        if (value instanceof BlockPos position) {

            final Map<String, Object> table = Maps.newHashMapWithExpectedSize(3);

            table.put("x", position.getX());
            table.put("y", position.getY());
            table.put("z", position.getZ());
            return table;
        }

        if (value instanceof Enum<?> constant) {
            return constant.name();
        }

        if (value instanceof Map<?, ?> map) {

            final Map<Object, Object> table = Maps.newHashMapWithExpectedSize(map.size());

            map.forEach((k, v) -> table.put(toLua(k), toLua(v)));
            return table;
        }

        if (value instanceof Collection<?> collection) {

            final List<Object> list = new ArrayList<>(collection.size());

            collection.forEach(element -> list.add(toLua(element)));
            return list;
        }

        return value;
    }

    //region internals

    private static Object argument(final Object[] arguments, final int index) {

        if (index < 0 || index >= arguments.length) {
            throw new IllegalArgumentException(String.format("Missing argument #%d", index + 1));
        }

        return arguments[index];
    }

    private static IllegalArgumentException wrongType(final int index, final String expected, final Object value) {
        return new IllegalArgumentException(String.format("Argument #%d must be a %s, got %s", index + 1, expected,
                null == value ? "nil" : value.getClass().getSimpleName()));
    }

    private LuaHelper() {
    }

    //endregion
}
