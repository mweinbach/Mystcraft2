/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.network.simple;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.network.NetworkDirection;
import ports.mekanism.lib.network.NetworkEvent;
import ports.mekanism.lib.network.PacketDistributor;
import ports.mekanism.platform.Platform;

/**
 * Indexed messages over one custom payload channel, carried by the loader's networking API.
 */
public class SimpleChannel {

    private record Message<MSG>(int index, Class<MSG> type, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder,
                                BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler, Optional<NetworkDirection> direction) {
    }

    private final ResourceLocation name;
    private final Int2ObjectMap<Message<?>> byIndex = new Int2ObjectOpenHashMap<>();
    private final Map<Class<?>, Message<?>> byType = new HashMap<>();

    public SimpleChannel(ResourceLocation name) {
        this.name = name;
        Platform.registerChannel(name, this::receive);
    }

    public <MSG> void registerMessage(int index, Class<MSG> messageType, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder,
          BiConsumer<MSG, Supplier<NetworkEvent.Context>> messageConsumer, Optional<NetworkDirection> networkDirection) {
        Message<MSG> message = new Message<>(index, messageType, encoder, decoder, messageConsumer, networkDirection);
        if (byIndex.putIfAbsent(index, message) != null || byType.putIfAbsent(messageType, message) != null) {
            throw new IllegalArgumentException("Duplicate message " + index + " " + messageType.getName() + " on " + name);
        }
    }

    public <MSG> void registerMessage(int index, Class<MSG> messageType, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder,
          BiConsumer<MSG, Supplier<NetworkEvent.Context>> messageConsumer) {
        registerMessage(index, messageType, encoder, decoder, messageConsumer, Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private <MSG> FriendlyByteBuf encode(MSG message) {
        Message<MSG> registration = (Message<MSG>) byType.get(message.getClass());
        if (registration == null) {
            throw new IllegalArgumentException("Unregistered message " + message.getClass().getName() + " on " + name);
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(registration.index());
        registration.encoder().accept(message, buffer);
        return buffer;
    }

    private void receive(FriendlyByteBuf buffer, NetworkDirection direction, @Nullable ServerPlayer sender) {
        int index = buffer.readVarInt();
        Message<?> registration = byIndex.get(index);
        if (registration == null) {
            throw new IllegalStateException("Unknown message index " + index + " on " + name);
        }
        if (registration.direction().isPresent() && registration.direction().get() != direction) {
            throw new IllegalStateException("Message " + registration.type().getName() + " received in the wrong direction");
        }
        handle(registration, buffer, new NetworkEvent.Context(direction, sender));
    }

    private static <MSG> void handle(Message<MSG> registration, FriendlyByteBuf buffer, NetworkEvent.Context context) {
        MSG message = registration.decoder().apply(buffer);
        registration.handler().accept(message, () -> context);
    }

    public <MSG> void sendToServer(MSG message) {
        Platform.sendToServer(name, encode(message));
    }

    public <MSG> void send(PacketDistributor.PacketTarget target, MSG message) {
        for (ServerPlayer player : target.players()) {
            Platform.sendToPlayer(name, player, encode(message));
        }
    }
}
