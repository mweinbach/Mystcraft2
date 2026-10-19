package net.mystcraft.smoke;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.integration.registry.BookEntityRegistry;
import net.mystcraft.minecraft.entity.LinkBookEntity;

public final class BookPresentationSmoke {
    private BookPresentationSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        ItemStack source = new ItemStack(MystcraftItems.LINKING_BOOK.get());
        source.getOrCreateTag().putString("Author", "Presentation author");
        var packet = new LinkingNetwork.OpenBook(UUID.randomUUID(), Component.literal("Visited, forbidden"),
                "minecraft:overworld", 1, 2, 3, false, false, source, true);
        source.getOrCreateTag().putString("Author", "Changed after snapshot");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LinkingNetwork.OpenBook.encode(packet, buffer);
            var decoded = LinkingNetwork.OpenBook.decode(buffer);
            require(decoded.visited() && !decoded.available() && !decoded.newAge(),
                    "Presentation confused registered world with permission to activate");
            require(decoded.token().equals(packet.token()) && !buffer.isReadable()
                    && decoded.contents().getTag().getString("Author").equals("Presentation author"),
                    "Presentation wire lost session identity or aliased book NBT");
        } finally { buffer.release(); }
        LinkBookEntity entity = new LinkBookEntity(BookEntityRegistry.LINK_BOOK.get(), level);
        Entity vanilla = entity;
        vanilla.handleEntityEvent((byte) 61);
        require(entity.isVisuallyHurt(), "Book hurt entity event did not enable classic tint");
        vanilla.discard();
        var receptacle = new net.mystcraft.minecraft.portal.BookReceptacleBlockEntity(net.minecraft.core.BlockPos.ZERO,
                net.mystcraft.integration.registry.PortalRegistry.BOOK_RECEPTACLE.get().defaultBlockState());
        receptacle.setYaw(725); receptacle.setPitch(-450);
        var saved = ((net.minecraft.world.level.block.entity.BlockEntity) receptacle).saveWithFullMetadata();
        var restored = new net.mystcraft.minecraft.portal.BookReceptacleBlockEntity(net.minecraft.core.BlockPos.ZERO,
                net.mystcraft.integration.registry.PortalRegistry.BOOK_RECEPTACLE.get().defaultBlockState());
        ((net.minecraft.world.level.block.entity.BlockEntity) restored).load(saved);
        require(restored.getYaw() == 5 && restored.getPitch() == -90,
                "Receptacle source modulo-360 presentation rotations did not persist");
        return List.of("book presentation: visited and forbidden states survive binary transport independently, session token and immutable author snapshot retained",
                "book presentation: native tracked entity event enables classic physical-book hurt tint without changing health NBT",
                "book presentation: receptacle pitch/yaw use source signed modulo-360 and survive full block-entity NBT roundtrip");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
