/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.capability;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures a handler type through an anonymous subclass: {@code new CapabilityToken<IFluidHandler>(){}}.
 */
public abstract class CapabilityToken<T> {

    protected CapabilityToken() {
    }

    /**
     * @return the binary name of the captured type, without its own type arguments
     */
    String getTypeName() {

        final Type superclass = this.getClass().getGenericSuperclass();

        if (!(superclass instanceof ParameterizedType)) {
            throw new IllegalStateException("A CapabilityToken must be created as an anonymous subclass with a type argument");
        }

        Type argument = ((ParameterizedType) superclass).getActualTypeArguments()[0];

        if (argument instanceof ParameterizedType) {
            argument = ((ParameterizedType) argument).getRawType();
        }

        if (!(argument instanceof Class<?>)) {
            throw new IllegalStateException("A CapabilityToken type argument must be a concrete type, got " + argument);
        }

        return ((Class<?>) argument).getName();
    }
}
