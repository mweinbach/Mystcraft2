package ports.jurassicraft.platform.client;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.platform.Network;

/** Client-only loader services. Loader adapters drive the registration phases at the right time. */
public final class ClientPlatform {
    public interface Backend {
        void renderLayer(Block block, RenderType type);
        /** Creates and registers a mapping; must be called from the {@code keys} phase. */
        KeyMapping keyMapping(Key key);
    }

    /**
     * A default binding. {@code control} bindings need Forge key modifiers; loaders without them bind
     * {@code fallback} instead, because vanilla gives each physical key to a single mapping.
     * {@code activeWhen} narrows where the mapping may conflict with others, or is null for in-game.
     */
    public record Key(String name, int key, boolean control, int fallback, BooleanSupplier activeWhen, String category) {
        public static final String CATEGORY = "key.categories.jurassicraft";
        public static Key of(String name, int key) { return new Key(name, key, false, key, null, CATEGORY); }
        public Key when(BooleanSupplier context) { return new Key(name, key, control, fallback, context, category); }
        public Key control(int fallbackKey) { return new Key(name, key, true, fallbackKey, activeWhen, category); }
    }

    public interface Renderers {
        <E extends Entity> void entity(EntityType<? extends E> type, EntityRendererProvider<E> provider);
        <T extends BlockEntity> void blockEntity(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider);
    }

    public interface Colors {
        void block(BlockColor color, Block... blocks);
        void item(ItemColor color, ItemLike... items);
    }

    private static Backend backend;

    private ClientPlatform() {}

    public static void install(Backend implementation) {
        if (backend != null) throw new IllegalStateException("JurassiCraft client platform is already installed");
        backend = Objects.requireNonNull(implementation);
    }

    private static Backend backend() {
        if (backend == null) throw new IllegalStateException("JurassiCraft client platform is not initialized");
        return backend;
    }

    public static void renderLayer(RenderType type, Block... blocks) {
        for (Block block : blocks) backend().renderLayer(block, type);
    }
    public static KeyMapping keyMapping(Key key) { return backend().keyMapping(key); }

    public static void sendToServer(ResourceLocation id, Consumer<FriendlyByteBuf> writer) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(Network.toServer(id, writer));
    }
}
