/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.eventbus.api;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A synchronous event bus. A listener registered for an event class also receives that class's subclasses, and
 * listeners run by priority, then in registration order.
 */
public final class EventBus implements IEventBus {

    private record Listener(Class<?> type, EventPriority priority, boolean receiveCanceled, Consumer<Object> consumer, long order) {
    }

    private final String name;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, List<Listener>> byType = new ConcurrentHashMap<>();
    private final Set<Object> registered = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private long order;

    public EventBus(String name) {
        this.name = name;
    }

    /**
     * Registering a target that is already registered does nothing, as on Forge.
     */
    @Override
    public void register(Object target) {
        if (!registered.add(target)) {
            return;
        }
        boolean isClass = target instanceof Class<?>;
        Class<?> type = isClass ? (Class<?>) target : target.getClass();
        for (Method method : type.getMethods()) {
            SubscribeEvent subscription = method.getAnnotation(SubscribeEvent.class);
            if (subscription == null || Modifier.isStatic(method.getModifiers()) != isClass) {
                continue;
            }
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("Invalid @SubscribeEvent method " + method);
            }
            Object instance = isClass ? null : target;
            add(method.getParameterTypes()[0], subscription.priority(), subscription.receiveCanceled(), event -> {
                try {
                    method.invoke(instance, event);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException runtime) {
                        throw runtime;
                    } else if (e.getCause() instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(e.getCause());
                }
            });
        }
    }

    @Override
    public <T extends Event> void addListener(EventListener<T> consumer) {
        addListener(EventPriority.NORMAL, false, consumer);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, EventListener<T> consumer) {
        addListener(priority, false, consumer);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, EventListener<T> consumer) {
        addListener(priority, receiveCancelled, eventType(consumer), consumer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType, Consumer<T> consumer) {
        add(eventType, priority, receiveCancelled, (Consumer<Object>) (Consumer<?>) consumer);
    }

    private synchronized void add(Class<?> type, EventPriority priority, boolean receiveCanceled, Consumer<Object> consumer) {
        listeners.add(new Listener(type, priority, receiveCanceled, consumer, order++));
        byType.clear();
    }

    @Override
    public boolean post(Event event) {
        List<Listener> matching = byType.computeIfAbsent(event.getClass(), this::listenersFor);
        for (Listener listener : matching) {
            if (!event.isCanceled() || listener.receiveCanceled()) {
                listener.consumer().accept(event);
            }
        }
        return event.isCanceled();
    }

    private List<Listener> listenersFor(Class<?> eventClass) {
        List<Listener> matching = new ArrayList<>();
        for (Listener listener : listeners) {
            if (listener.type().isAssignableFrom(eventClass)) {
                matching.add(listener);
            }
        }
        matching.sort(Comparator.comparing(Listener::priority).thenComparingLong(Listener::order));
        return List.copyOf(matching);
    }

    /**
     * The event class a lambda or method reference accepts, read from its serialized form.
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> eventType(EventListener<T> listener) {
        try {
            Method writeReplace = listener.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(listener);
            String signature = lambda.getInstantiatedMethodType();
            String type = signature.substring(signature.indexOf("L") + 1, signature.indexOf(";")).replace('/', '.');
            return (Class<T>) Class.forName(type, false, listener.getClass().getClassLoader());
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalArgumentException("Cannot determine the event type of " + listener
                                               + "; pass the event class explicitly", e);
        }
    }

    @Override
    public String toString() {
        return "EventBus[" + name + "]";
    }
}
