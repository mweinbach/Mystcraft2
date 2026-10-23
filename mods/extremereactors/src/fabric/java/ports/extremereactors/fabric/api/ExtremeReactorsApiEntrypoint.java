/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.api;

/**
 * The Fabric counterpart of the inter-mod messages Extreme Reactors reads on Forge.
 * <p>
 * List an implementation under the {@value #KEY} entrypoint of your {@code fabric.mod.json}:
 * <pre>{@code
 * "entrypoints": { "bigreactors:api": [ "com.example.MyReactorsAddon" ] }
 * }</pre>
 * Extreme Reactors calls it once, when the first server or client starts, and runs the messages exactly as it runs
 * Forge IMC messages: grouped by method, in the order of {@code ExtremeReactors.IMC_METHODS} (every registration
 * before any removal), then the modpack API file. An action makes its Extreme Reactors API calls directly, for
 * example {@code ReactantsRegistry.registerFuel(...)} for {@code "reactant-register"}.
 * <p>
 * Calling the API directly from a mod initializer also works, as on Forge: those calls are queued and run in the
 * same order. Messages make the method of each call explicit.
 */
@FunctionalInterface
public interface ExtremeReactorsApiEntrypoint {

    /** The entrypoint key. */
    String KEY = "bigreactors:api";

    /**
     * @param messages accepts the API messages of this mod
     */
    void sendApiMessages(Messages messages);

    @FunctionalInterface
    interface Messages {

        /**
         * @param method one of the Forge IMC methods Extreme Reactors reads, such as {@code "reactant-register"},
         *               {@code "moderator-s-register"} or {@code "coilmaterial-remove"}; unknown methods are logged
         *               and ignored
         * @param action the API calls to run for it
         */
        void send(String method, Runnable action);
    }
}
