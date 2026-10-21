/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.capabilities;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures a capability's interface type: {@code new CapabilityToken<IFoo>() {}}.
 */
public abstract class CapabilityToken<T> {

    protected final String getType() {
        Type superclass = getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[0];
            if (argument instanceof ParameterizedType generic) {
                argument = generic.getRawType();
            }
            return argument.getTypeName();
        }
        throw new IllegalStateException("CapabilityToken must be created as an anonymous subclass with a type argument");
    }

    @Override
    public String toString() {
        return "CapabilityToken[" + getType() + "]";
    }
}
