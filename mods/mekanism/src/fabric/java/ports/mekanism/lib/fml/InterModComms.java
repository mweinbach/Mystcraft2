/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Messages between the port's modules, sent during {@code InterModEnqueueEvent} and read during
 * {@code InterModProcessEvent}.
 */
public final class InterModComms {

    public record IMCMessage(String senderModId, String modId, String method, Supplier<?> messageSupplier) {

        @SuppressWarnings("unchecked")
        public <T> Supplier<T> getMessageSupplier() {
            return (Supplier<T>) messageSupplier;
        }
    }

    private static final Map<String, List<IMCMessage>> QUEUES = new ConcurrentHashMap<>();

    private InterModComms() {
    }

    public static boolean sendTo(String modId, String method, Supplier<?> thing) {
        if (!ModList.get().isLoaded(modId)) {
            return false;
        }
        QUEUES.computeIfAbsent(modId, id -> new CopyOnWriteArrayList<>())
              .add(new IMCMessage(ModLoadingContext.get().getActiveNamespace(), modId, method, thing));
        return true;
    }

    public static boolean sendTo(String senderModId, String modId, String method, Supplier<?> thing) {
        if (!ModList.get().isLoaded(modId)) {
            return false;
        }
        QUEUES.computeIfAbsent(modId, id -> new CopyOnWriteArrayList<>()).add(new IMCMessage(senderModId, modId, method, thing));
        return true;
    }

    public static Stream<IMCMessage> getMessages(String modId, Predicate<String> methodMatcher) {
        List<IMCMessage> queue = QUEUES.getOrDefault(modId, List.of());
        return queue.stream().filter(message -> methodMatcher.test(message.method()));
    }

    public static Stream<IMCMessage> getMessages(String modId) {
        return getMessages(modId, method -> true);
    }
}
