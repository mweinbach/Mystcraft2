/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block;

import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.internal.Log;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Routes named commands to handlers bound to one block entity. Server handlers run for commands sent by a client,
 * client handlers for commands sent by the server.
 */
public final class TileCommandDispatcher<T> implements ITileCommandHandler {

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public void handleCommand(final LogicalSide sourceSide, final String name, final CompoundTag parameters) {

        final Map<String, BiConsumer<T, CompoundTag>> handlers = LogicalSide.CLIENT == sourceSide ? this._serverHandlers : this._clientHandlers;
        final BiConsumer<T, CompoundTag> handler = handlers.get(name);

        if (null != handler) {
            handler.accept(this._target, parameters);
        } else {
            Log.LOGGER.warn(Log.CORE, "Ignoring unknown command {} for {}", name, this._target);
        }
    }

    public static final class Builder<T> {

        public Builder<T> addServerHandler(final String name, final Consumer<T> handler) {
            return this.addServerHandler(name, (target, parameters) -> handler.accept(target));
        }

        public Builder<T> addServerHandler(final String name, final BiConsumer<T, CompoundTag> handler) {

            this._server.put(name, handler);
            return this;
        }

        public Builder<T> addClientHandler(final String name, final Consumer<T> handler) {
            return this.addClientHandler(name, (target, parameters) -> handler.accept(target));
        }

        public Builder<T> addClientHandler(final String name, final BiConsumer<T, CompoundTag> handler) {

            this._client.put(name, handler);
            return this;
        }

        public TileCommandDispatcher<T> build(final T target) {
            return new TileCommandDispatcher<>(target, this._server.build(), this._client.build());
        }

        private Builder() {

            this._server = ImmutableMap.builder();
            this._client = ImmutableMap.builder();
        }

        private final ImmutableMap.Builder<String, BiConsumer<T, CompoundTag>> _server;
        private final ImmutableMap.Builder<String, BiConsumer<T, CompoundTag>> _client;
    }

    //region internals

    private TileCommandDispatcher(final T target, final Map<String, BiConsumer<T, CompoundTag>> serverHandlers,
                                  final Map<String, BiConsumer<T, CompoundTag>> clientHandlers) {

        this._target = target;
        this._serverHandlers = serverHandlers;
        this._clientHandlers = clientHandlers;
    }

    private final T _target;
    private final Map<String, BiConsumer<T, CompoundTag>> _serverHandlers;
    private final Map<String, BiConsumer<T, CompoundTag>> _clientHandlers;

    //endregion
}
