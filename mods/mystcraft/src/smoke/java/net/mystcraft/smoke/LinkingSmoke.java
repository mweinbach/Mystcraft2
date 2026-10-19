package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.integration.registry.BookstandRegistry;
import net.mystcraft.minecraft.blockentity.BookstandBlockEntity;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.linking.LinkingService;

/** Dedicated-server smoke checks. This source belongs only in the separate test mod. */
public final class LinkingSmoke {
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private static final BlockPos ORIGIN = new BlockPos(32, 200, 32);
    private static final BlockPos ARRIVAL = new BlockPos(80, 200, 80);

    private LinkingSmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel origin) throws Exception {
        check(server.isSameThread(), "smoke checks must execute on the server thread");
        ServerLevel target = server.getLevel(origin.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER);
        check(target != null && target != origin, "a real second dimension must exist");
        List<String> passed = new ArrayList<>();
        Map<ServerLevel, Map<BlockPos, BlockState>> changed = new LinkedHashMap<>();
        List<ServerPlayer> players = new ArrayList<>();
        List<LinkBookEntity> created = new ArrayList<>();
        Field transportField = LinkingNetwork.class.getDeclaredField("transport");
        transportField.setAccessible(true);
        LinkingNetwork.Transport originalTransport = (LinkingNetwork.Transport) transportField.get(null);
        check(originalTransport != null, "production network transport must already be initialized");
        Capture transport = new Capture();
        try {
            preparePad(origin, ORIGIN, changed);
            preparePad(target, ARRIVAL, changed);
            ServerPlayer first = player(server, origin, "MystSmokeOne", 32.5, 200, 32.5);
            players.add(first);
            ServerPlayer second = player(server, origin, "MystSmokeTwo", 34.5, 200, 32.5);
            players.add(second);
            LinkingNetwork.initialize(transport);

            ItemStack unlinked = new ItemStack(MystcraftItems.UNLINKED_LINK_BOOK.get());
            unlinked.setHoverName(Component.literal("Smoke destination"));
            unlinked.getOrCreateTag().putString("SmokeMarker", "preserved");
            first.setItemInHand(HAND, unlinked);
            unlinked.getItem().use(origin, first, HAND);
            ItemStack bound = first.getItemInHand(HAND);
            check(bound.is(MystcraftItems.LINKING_BOOK.get()), "unlinked book must turn into linking book");
            LinkDestination boundDestination = LinkDestination.read(bound).orElseThrow();
            check(boundDestination.dimension().equals(origin.dimension())
                    && boundDestination.x() == first.getX() && boundDestination.y() == first.getY()
                    && boundDestination.z() == first.getZ(), "binding must capture server location");
            check(bound.getTag().getString("SmokeMarker").equals("preserved")
                    && bound.getHoverName().getString().equals("Smoke destination"), "binding must preserve source metadata");
            passed.add("binding: server destination and original item metadata");

            check(!LinkingService.link(first, HAND, bound), "same-dimension link must reject");
            check(first.getLevel() == origin && first.getItemInHand(HAND) == bound,
                    "rejected same-dimension link must preserve player and book");
            ItemStack invalid = new ItemStack(MystcraftItems.LINKING_BOOK.get());
            first.setItemInHand(HAND, invalid);
            check(!LinkingService.link(first, HAND, invalid), "unbound linking item must reject");
            ItemStack missing = book(new LinkDestination(new ResourceLocation("mystcraft_smoke", "missing_dimension"), 80.5, 200, 80.5, 90, 15));
            first.setItemInHand(HAND, missing);
            check(!LinkingService.link(first, HAND, missing), "absent dimension must reject");
            check(first.getItemInHand(HAND) == missing, "missing dimension must not consume book");
            ItemStack blocked = targetBook(target);
            first.setItemInHand(HAND, blocked);
            target.setBlock(ARRIVAL, Blocks.STONE.defaultBlockState(), 3);
            check(!LinkingService.link(first, HAND, blocked), "solid arrival obstruction must reject");
            target.setBlock(ARRIVAL, Blocks.WATER.defaultBlockState(), 3);
            check(!LinkingService.link(first, HAND, blocked), "liquid arrival must reject");
            target.setBlock(ARRIVAL, Blocks.AIR.defaultBlockState(), 3);
            check(first.getLevel() == origin && first.getItemInHand(HAND) == blocked,
                    "unsafe arrival must preserve source state");
            passed.add("safety: same-dimension, malformed, missing-dimension, solid and liquid rejection");

            ItemStack travel = targetBook(target);
            String travelMarker = UUID.randomUUID().toString();
            travel.getOrCreateTag().putString("SmokeTravel", travelMarker);
            first.setItemInHand(HAND, travel);
            check(LinkingService.link(first, HAND, travel), "valid cross-dimension held book must link");
            check(first.getLevel() == target && first.getItemInHand(HAND).isEmpty(),
                    "held link must move player and clear hand");
            check(first.getX() == 80.5 && first.getY() == 200 && first.getZ() == 80.5
                    && first.getYRot() == 90 && first.getXRot() == 15, "link must retain exact destination and orientation");
            List<LinkBookEntity> drops = origin.getEntitiesOfClass(LinkBookEntity.class,
                    new AABB(ORIGIN).inflate(8), entity -> ((Entity) entity).isAlive()
                            && entity.getBook().hasTag()
                            && travelMarker.equals(entity.getBook().getTag().getString("SmokeTravel")));
            created.addAll(drops);
            if (drops.size() != 1) {
                String diagnosis = sourceEntityDiagnosis(origin, travelMarker, created);
                throw new AssertionError("held link must leave exactly one interactive source book; visibleMatches="
                        + drops.size() + "; " + diagnosis);
            }
            LinkBookEntity dropped = drops.get(0);
            check(ItemStack.matches(dropped.getBook(), travel), "left-behind book must retain original destination NBT");
            reset(first, origin, 32.5, 200, 34.5);
            ((ItemEntity) dropped).playerTouch(second);
            check(((Entity) dropped).isAlive() && second.getItemInHand(HAND).isEmpty(), "walking over book must not pick it up");
            ((Entity) dropped).interact(second, HAND);
            UUID entityToken = transport.token(second);
            check(second.getLevel() == origin, "opening entity book must not teleport");
            LinkingNetwork.activate(second, entityToken);
            check(second.getLevel() == target && ((Entity) dropped).isAlive() && ItemStack.matches(dropped.getBook(), travel),
                    "entity session must link while retaining source book");
            reset(second, origin, 34.5, 200, 32.5);
            LinkingNetwork.activate(second, entityToken);
            check(second.getLevel() == origin, "replaying spent entity token must not link");
            passed.add("held/entity links: cross-dimension position, source drop, no auto pickup, retained source and replay rejection");

            BlockPos standPos = ORIGIN.offset(1, 0, 2);
            origin.setBlock(standPos, BookstandRegistry.BOOKSTAND.get().defaultBlockState(), 3);
            check(origin.getBlockEntity(standPos) instanceof BookstandBlockEntity, "bookstand must create its registered block entity");
            BookstandBlockEntity stand = (BookstandBlockEntity) origin.getBlockEntity(standPos);
            stand.setBook(targetBook(target));
            ItemStack standBook = stand.getBook();
            LinkingNetwork.openStand(second, standPos);
            LinkingNetwork.activate(second, transport.token(second));
            check(second.getLevel() == target && stand.getBook() == standBook,
                    "stand session must retain exact source stack");
            reset(second, origin, 34.5, 200, 32.5);
            passed.add("bookstand: real block entity session links without consuming stored book");
            second.setShiftKeyDown(true);
            origin.getBlockState(standPos).getBlock().use(origin.getBlockState(standPos), origin, standPos,
                    second, HAND, new BlockHitResult(Vec3.atCenterOf(standPos), Direction.UP, standPos, false));
            check(stand.getBook().isEmpty() && second.getItemInHand(HAND) == standBook,
                    "sneak-empty-hand stand retrieval must transfer the exact book to interacting hand");
            second.setShiftKeyDown(false);
            second.setItemInHand(HAND, ItemStack.EMPTY);
            passed.add("bookstand pickup: empty interacting hand receives the stored book directly");

            second.setItemInHand(HAND, targetBook(target));
            LinkingNetwork.openBook(second, HAND);
            UUID stale = transport.token(second);
            second.setItemInHand(HAND, second.getItemInHand(HAND).copy());
            LinkingNetwork.activate(second, stale);
            check(second.getLevel() == origin && !second.getItemInHand(HAND).isEmpty(),
                    "equal NBT replacement must invalidate held session identity");
            LinkingNetwork.openBook(second, HAND);
            UUID moved = transport.token(second);
            second.setPos(35.5, 200, 32.5);
            LinkingNetwork.activate(second, moved);
            check(second.getLevel() == origin, "moving away from source must invalidate session");
            second.setPos(34.5, 200, 32.5);
            first.setItemInHand(HAND, targetBook(target));
            LinkingNetwork.openBook(first, HAND);
            UUID firstToken = transport.token(first);
            LinkingNetwork.openBook(second, HAND);
            LinkingNetwork.activate(second, firstToken);
            check(second.getLevel() == origin, "another player's session token must not authorize link");
            passed.add("sessions: identical-stack substitution, source movement and cross-player token rejection");

            second.setItemInHand(HAND, ItemStack.EMPTY);
            second.setShiftKeyDown(true);
            ((Entity) dropped).interact(second, HAND);
            check(!((Entity) dropped).isAlive() && ItemStack.matches(second.getItemInHand(HAND), travel),
                    "sneak-empty-hand pickup must recover exact book once");
            second.setShiftKeyDown(false);
            passed.add("entity pickup: explicit sneak interaction recovers one unchanged book");
            return List.copyOf(passed);
        } finally {
            LinkingNetwork.initialize(originalTransport);
            for (ServerPlayer player : players) {
                LinkingNetwork.clear(player.getUUID());
                player.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
            for (LinkBookEntity entity : created) ((Entity) entity).discard();
            for (var levelEntry : changed.entrySet()) {
                for (var block : levelEntry.getValue().entrySet()) {
                    if (levelEntry.getKey().getBlockEntity(block.getKey()) instanceof BookstandBlockEntity stand) {
                        stand.takeBook();
                    }
                    levelEntry.getKey().setBlock(block.getKey(), block.getValue(), 3);
                }
            }
        }
    }

    private static ItemStack book(LinkDestination destination) {
        ItemStack book = new ItemStack(MystcraftItems.LINKING_BOOK.get());
        destination.write(book);
        return book;
    }

    private static ItemStack targetBook(ServerLevel target) {
        return book(new LinkDestination(target.dimension().location(), 80.5, 200, 80.5, 90, 15));
    }

    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name, double x, double y, double z) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name), null);
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        player.setPos(x, y, z);
        level.addNewPlayer(player);
        return player;
    }

    private static void reset(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, 0, 0);
        player.hasChangedDimension();
        player.setItemInHand(HAND, ItemStack.EMPTY);
    }

    private static void preparePad(ServerLevel level, BlockPos center,
                                   Map<ServerLevel, Map<BlockPos, BlockState>> changed) {
        Map<BlockPos, BlockState> snapshots = changed.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(4, 3, 4))) {
            BlockPos immutable = pos.immutable();
            check(level.getBlockEntity(immutable) == null, "smoke pad must not replace existing block entities");
            snapshots.putIfAbsent(immutable, level.getBlockState(immutable));
            level.setBlock(immutable, pos.getY() == center.getY() - 1
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Diagnostic only: inspect hidden sections without pretending their entities are interactable. */
    @SuppressWarnings("unchecked")
    private static String sourceEntityDiagnosis(ServerLevel level, String marker, List<LinkBookEntity> cleanup) {
        try {
            PersistentEntitySectionManager<Entity> manager = null;
            for (java.lang.reflect.Field field : ServerLevel.class.getDeclaredFields()) {
                if (PersistentEntitySectionManager.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true); manager = (PersistentEntitySectionManager<Entity>) field.get(level); break;
                }
            }
            if (manager == null) return "entityManagerNotFound";
            EntitySectionStorage<Entity> storage = null;
            for (java.lang.reflect.Field field : PersistentEntitySectionManager.class.getDeclaredFields()) {
                if (EntitySectionStorage.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true); storage = (EntitySectionStorage<Entity>) field.get(manager); break;
                }
            }
            List<String> sections = new ArrayList<>();
            if (storage != null) {
                storage.getExistingSectionsInChunk(new ChunkPos(ORIGIN).toLong()).forEach(section -> {
                    section.getEntities().forEach(entity -> {
                        if (entity instanceof LinkBookEntity book && book.getBook().hasTag()
                                && marker.equals(book.getBook().getTag().getString("SmokeTravel"))) {
                            if (!cleanup.contains(book)) cleanup.add(book);
                            sections.add("book=" + entity.getUUID() + ",status=" + section.getStatus()
                                    + ",alive=" + entity.isAlive() + ",pos=" + entity.position());
                        }
                    });
                });
            }
            int vanillaItemQuery = level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(8),
                    entity -> entity.isAlive() && entity.getItem().hasTag()
                            && marker.equals(entity.getItem().getTag().getString("SmokeTravel"))).size();
            return "entityTicking=" + level.isPositionEntityTicking(ORIGIN)
                    + ",forced=" + level.getForcedChunks().contains(new ChunkPos(ORIGIN).toLong())
                    + ",vanillaItemMatches=" + vanillaItemQuery + ",manager=" + manager.gatherStats()
                    + ",rawSections=" + sections;
        } catch (ReflectiveOperationException failure) {
            return "entityDiagnosticFailed=" + failure;
        }
    }

    private static final class Capture implements LinkingNetwork.Transport {
        private final Map<UUID, LinkingNetwork.OpenBook> opened = new HashMap<>();
        @Override public void sendToPlayer(ServerPlayer player, LinkingNetwork.OpenBook packet) { opened.put(player.getUUID(), packet); }
        @Override public void sendRequest(UUID token) { throw new AssertionError("server smoke must not send client requests"); }
        UUID token(ServerPlayer player) {
            LinkingNetwork.OpenBook packet = opened.remove(player.getUUID());
            check(packet != null, "opening a valid book must produce a session packet");
            return packet.token();
        }
    }
}
