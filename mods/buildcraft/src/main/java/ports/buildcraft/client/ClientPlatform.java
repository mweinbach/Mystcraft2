package ports.buildcraft.client;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

/** Client services whose API differs between loaders; installed by each loader's client entrypoint. */
public final class ClientPlatform {
    public interface Backend {
        TextureAtlasSprite fluidSprite(Fluid fluid);

        /** ARGB tint applied to {@link #fluidSprite}. */
        int fluidColour(Fluid fluid);

        /** Sends a message to the server receiver registered for {@code channel} in {@code BuildCraft.serverReceivers}. */
        void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> payload);
    }

    private static Backend backend;

    private ClientPlatform() {}

    public static void install(Backend implementation) {
        backend = Objects.requireNonNull(implementation);
    }

    public static Backend get() {
        if (backend == null) throw new IllegalStateException("BuildCraft client platform is not initialized");
        return backend;
    }
}
