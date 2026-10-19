package net.mystcraft.integration.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.mystcraft.minecraft.blockentity.BookstandBlockEntity;
import net.mystcraft.minecraft.blockentity.BookHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.linking.LinkingService;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.writing.PageData;
import net.minecraft.nbt.CompoundTag;

/** Server-owned, single-use linking sessions. Packets never accept a client destination. */
public final class LinkingNetwork {
    /** Implementations must dispatch inbound requests on the appropriate game thread. */
    public interface Transport {
        void sendToPlayer(ServerPlayer player, OpenBook packet);
        void sendRequest(UUID token);
    }
    private static Transport transport;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private record Session(UUID token, InteractionHand hand, int slot, ItemStack book, ItemStack identity,
                           ResourceLocation dimension, Vec3 position, long expires, BlockPos stand,
                           LinkBookEntity entity, UUID entityId, Vec3 entityPosition,
                           net.mystcraft.minecraft.writing.WritingDeskBlockEntity desk,
                           net.minecraft.world.inventory.AbstractContainerMenu menu) {}
    private LinkingNetwork() {}

    public static void initialize(Transport implementation) {
        transport = java.util.Objects.requireNonNull(implementation);
    }

    /** Called on the server thread when a player disconnects. */
    public static void clear(UUID playerId) { SESSIONS.remove(playerId); }

    private static Transport transport() {
        if (transport == null) throw new IllegalStateException("Linking transport has not been initialized");
        return transport;
    }

    public static void openBook(ServerPlayer player, InteractionHand hand) {
        ItemStack book = player.getItemInHand(hand);
        var destination = LinkDestination.read(book);
        if (destination.isEmpty() && !pendingAge(book)) return;
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(token, hand, player.getInventory().selected,
                book.copy(), book, player.level.dimension().location(), player.position(),
                player.server.getTickCount() + 1200L, null, null, null, null, null, null));
        sendPresentation(player, book, token);
    }

    public static void openLectern(ServerPlayer player, BlockPos pos) { openStand(player, pos); }

    public static void openStand(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64
                || !(player.level.getBlockEntity(pos) instanceof BookHolder stand)) return;
        ItemStack book = stand.getBook();
        var destination = LinkDestination.read(book);
        if (destination.isEmpty() && !pendingAge(book)) return;
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(token, null, -1, book.copy(), book,
                player.level.dimension().location(), player.position(),
                player.server.getTickCount() + 1200L, pos.immutable(), null, null, null, null, null));
        sendPresentation(player, book, token);
    }

    /** Called only by the validated writing-desk menu action. The desk retains its book. */
    public static void openDeskTarget(ServerPlayer player, BlockPos pos) {
        if (!player.level.hasChunkAt(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64
                || !(player.level.getBlockEntity(pos) instanceof net.mystcraft.minecraft.writing.WritingDeskBlockEntity desk)
                || !player.containerMenu.stillValid(player)) return;
        ItemStack book = desk.inventory.getItem(0);
        if (LinkDestination.read(book).isEmpty() && !pendingAge(book)) return;
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(token, null, -1, book.copy(), book,
                player.level.dimension().location(), player.position(), player.server.getTickCount() + 1200L,
                pos.immutable(), null, null, null, desk, player.containerMenu));
        sendPresentation(player, book, token);
    }

    public static void openEntity(ServerPlayer player, LinkBookEntity entity) {
        if (!entity.isAlive() || entity.level != player.level || player.distanceToSqr(entity) > 36
                || player.getLevel().getEntity(entity.getUUID()) != entity) return;
        ItemStack book = entity.getBook();
        var destination = LinkDestination.read(book);
        if (destination.isEmpty() && !pendingAge(book)) return;
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(token, null, -1, book.copy(), book,
                player.level.dimension().location(), player.position(),
                player.server.getTickCount() + 1200L, null, entity, entity.getUUID(), entity.position(), null, null));
        sendPresentation(player, book, token);
    }

    private static boolean pendingAge(ItemStack book) {
        return AgeBookData.isUnallocated(book) || AgeBookData.ageId(book).isPresent();
    }
    private static void sendPresentation(ServerPlayer player, ItemStack book, UUID token) {
        var destination = LinkDestination.read(book);
        if (destination.isEmpty()) {
            transport().sendToPlayer(player, new OpenBook(token, book.getHoverName(), "", 0, 0, 0, true, true, presentationCopy(book)));
            return;
        }
        LinkDestination target = destination.get();
        // Legacy isDimensionVisited means registered, not whether this reader has travelled there.
        boolean visited = player.server.levelKeys().contains(target.dimension())
                || net.mystcraft.minecraft.age.AgeManager.data(player.server).entries().stream()
                        .anyMatch(age -> age.dimensionId().equals(target.dimensionId()));
        boolean loaded = player.server.getLevel(target.dimension()) != null;
        transport().sendToPlayer(player, new OpenBook(token, book.getHoverName(), target.dimensionId().toString(),
                target.x(), target.y(), target.z(), net.mystcraft.minecraft.linking.LinkModifiers.from(book).allowsDimension(target.dimension().equals(player.level.dimension()))
                && (loaded || pendingAge(book)), false, presentationCopy(book), visited));
    }

    /** Presentation has no authority: activation still accepts only the server-owned session token. */
    private static ItemStack presentationCopy(ItemStack book) {
        ItemStack view = new ItemStack(book.getItem());
        if (!book.hasTag()) return view;
        CompoundTag source = book.getTag(), tag = new CompoundTag();
        for (String key : new String[]{"display", "Authors", "Author", "author", "authors", "Cover", "cover",
                "Flags", "linkpanel", AgeBookData.AGE, LinkDestination.TAG}) {
            if (source.contains(key)) tag.put(key, source.get(key).copy());
        }
        var pages = PageData.readPages(source);
        if (!pages.isEmpty()) PageData.writePages(tag, pages.subList(0, Math.min(AgeBookData.MAX_PAGES, pages.size())));
        view.setTag(tag);
        return view;
    }

    /** Loader adapters must call this on the server game thread. */
    public static void activate(ServerPlayer player, UUID token) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null || !session.token().equals(token)) return;
        ItemStack held;
        if (session.entity() != null) {
            LinkBookEntity entity = session.entity();
            if (!entity.isAlive() || player.getLevel().getEntity(session.entityId()) != entity
                    || player.distanceToSqr(entity) > 36
                    || entity.position().distanceToSqr(session.entityPosition()) > 0.25) return;
            held = entity.getBook();
        } else if (session.desk() != null) {
            if (!player.level.hasChunkAt(session.stand())
                    || player.distanceToSqr(Vec3.atCenterOf(session.stand())) > 64
                    || player.level.getBlockEntity(session.stand()) != session.desk()
                    || player.containerMenu != session.menu() || !session.menu().stillValid(player)) return;
            held = session.desk().inventory.getItem(0);
        } else if (session.stand() != null) {
            if (!player.level.hasChunkAt(session.stand())
                    || player.distanceToSqr(Vec3.atCenterOf(session.stand())) > 64
                    || !(player.level.getBlockEntity(session.stand()) instanceof BookHolder stand)) return;
            held = stand.getBook();
        } else {
            held = player.getItemInHand(session.hand());
        }
        if (!player.isAlive() || player.isSpectator()
                || player.server.getTickCount() > session.expires()
                || !player.level.dimension().location().equals(session.dimension())
                || player.position().distanceToSqr(session.position()) > 0.25
                || (session.hand() == InteractionHand.MAIN_HAND && player.getInventory().selected != session.slot())
                || held != session.identity() || !ItemStack.matches(held, session.book())) {
            player.displayClientMessage(Component.translatable("message.mystcraft.stale_book"), true);
            return;
        }
        if (session.stand() == null && session.entity() == null) LinkingService.link(player, session.hand(), held);
        else if (session.entity() != null) LinkingService.linkFromEntity(player, held);
        else LinkingService.linkFromStand(player, held);
        if (session.stand() != null) {
            var sourceLevel = player.server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.Registry.DIMENSION_REGISTRY, session.dimension()));
            if (sourceLevel != null && sourceLevel.getBlockEntity(session.stand()) != null)
                sourceLevel.getBlockEntity(session.stand()).setChanged();
        }
    }

    public static void requestLink(UUID token) { transport().sendRequest(token); }
    public record OpenBook(UUID token, Component title, String dimension, double x, double y, double z,
                           boolean available, boolean newAge, ItemStack contents, boolean visited) {
        public OpenBook { contents = contents.copy(); }
        public OpenBook(UUID token, Component title, String dimension, double x, double y, double z,
                        boolean available, boolean newAge, ItemStack contents) {
            this(token, title, dimension, x, y, z, available, newAge, contents, !newAge && available);
        }
        public OpenBook(UUID token, Component title, String dimension, double x, double y, double z, boolean available, boolean newAge) {
            this(token, title, dimension, x, y, z, available, newAge, ItemStack.EMPTY);
        }
        public OpenBook(UUID token, Component title, String dimension, double x, double y, double z, boolean available) {
            this(token, title, dimension, x, y, z, available, false);
        }
        public static void encode(OpenBook packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.token()); buffer.writeComponent(packet.title());
            buffer.writeUtf(packet.dimension(), 256);
            buffer.writeDouble(packet.x()); buffer.writeDouble(packet.y()); buffer.writeDouble(packet.z());
            buffer.writeBoolean(packet.available()); buffer.writeBoolean(packet.newAge());
            buffer.writeItem(packet.contents());
            buffer.writeBoolean(packet.visited());
        }
        public static OpenBook decode(FriendlyByteBuf buffer) {
            return new OpenBook(buffer.readUUID(), buffer.readComponent(), buffer.readUtf(256),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean(), buffer.readItem(), buffer.readBoolean());
        }
    }
}
