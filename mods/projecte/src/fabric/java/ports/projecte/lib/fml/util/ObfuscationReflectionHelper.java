/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml.util;

import java.lang.reflect.Field;

/**
 * ProjectE only reflects into the port's own classes, whose names are not remapped.
 */
public final class ObfuscationReflectionHelper {

    private ObfuscationReflectionHelper() {
    }

    public static Field findField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            throw new UnableToFindFieldException(e);
        }
    }

    public static class UnableToFindFieldException extends RuntimeException {

        public UnableToFindFieldException(Exception e) {
            super(e);
        }
    }
}
