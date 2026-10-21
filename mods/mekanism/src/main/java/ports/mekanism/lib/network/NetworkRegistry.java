/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.network;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import ports.mekanism.lib.network.simple.SimpleChannel;

public final class NetworkRegistry {

    private NetworkRegistry() {
    }

    public static final class ChannelBuilder {

        private final ResourceLocation name;
        private Supplier<String> protocolVersion = () -> "1";

        private ChannelBuilder(ResourceLocation name) {
            this.name = name;
        }

        public static ChannelBuilder named(ResourceLocation name) {
            return new ChannelBuilder(name);
        }

        /**
         * Accepted for source compatibility; both sides run the same build, so versions are not negotiated.
         */
        public ChannelBuilder clientAcceptedVersions(Predicate<String> predicate) {
            return this;
        }

        public ChannelBuilder serverAcceptedVersions(Predicate<String> predicate) {
            return this;
        }

        public ChannelBuilder networkProtocolVersion(Supplier<String> version) {
            this.protocolVersion = version;
            return this;
        }

        public SimpleChannel simpleChannel() {
            return new SimpleChannel(name);
        }
    }
}
