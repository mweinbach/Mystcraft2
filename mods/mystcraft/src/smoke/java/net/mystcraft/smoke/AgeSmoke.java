package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.age.AgeLevelData;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.age.AgeSavedData;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.writing.PageData;

/** Test-mod-only dynamic Age checks and an explicit fixture for verification after process restart. */
public final class AgeSmoke {
    private static final BlockPos MARKER = new BlockPos(160, 80, 160);
    private static final String FIXTURE_FILE = "mystcraft_smoke_age_fixture";
    private AgeSmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel origin) throws Exception {
        check(server.isSameThread(), "Age smoke must run on the server thread");
        AgeSavedData saved = AgeManager.data(server);
        int initialAges = saved.entries().size();
        int initialWorlds = server.levelKeys().size();
        List<String> passed = new ArrayList<>();
        Field transportField = LinkingNetwork.class.getDeclaredField("transport");
        transportField.setAccessible(true);
        LinkingNetwork.Transport previous = (LinkingNetwork.Transport) transportField.get(null);
        check(previous != null, "production transport must be installed before Age smoke");
        Capture transport = new Capture();
        ServerPlayer player = null;
        UUID createdAge = null;
        try {
            player = player(server, origin);
            LinkingNetwork.initialize(transport);
            List<CompoundTag> pages = pages("weatheron");
            ItemStack book = AgeBookData.create("Smoke Age fixture", pages);
            check(!book.isEmpty() && AgeBookData.isUnallocated(book), "panel-first descriptive book must start unallocated");
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            LinkingNetwork.openBook(player, InteractionHand.MAIN_HAND);
            UUID token = transport.takeToken(player);
            check(saved.entries().size() == initialAges && server.levelKeys().size() == initialWorlds
                    && AgeBookData.ageId(book).isEmpty() && player.getLevel() == origin,
                    "opening the descriptive-book UI must not reserve or generate an Age");
            passed.add("Ages: opening an unwritten descriptive book reserves no identity or dimension");

            LinkingNetwork.activate(player, token);
            createdAge = AgeBookData.ageId(book).orElseThrow(() -> new AssertionError("panel activation must allocate Age identity"));
            AgeSavedData.Entry first = saved.get(createdAge);
            check(first != null && saved.entries().size() == initialAges + 1,
                    "one panel activation must allocate exactly one canonical Age");
            LinkDestination destination = LinkDestination.read(book).orElseThrow();
            ServerLevel age = server.getLevel(destination.dimension());
            check(age != null && player.getLevel() == age && age != origin
                    && !age.dimension().equals(Level.OVERWORLD) && !age.dimension().equals(Level.NETHER)
                    && !age.dimension().equals(Level.END), "panel activation must travel to a real custom Age world");
            check(age.getSeed() == first.seed() && age.dimension().location().equals(first.dimensionId()),
                    "runtime world must use canonical persistent Age key and seed");
            check(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "descriptive book must remain at departure");
            check(first.pages().equals(pages), "Age allocation must retain ordered page payloads and unknown nested data");
            passed.add("Ages: activation allocates one genuine custom world, uses its seed and leaves the book behind");

            player.teleportTo(origin, 48.5, 200, 48.5, 0, 0);
            player.hasChangedDimension();
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            check(AgeManager.prepare(player, book) && saved.entries().size() == initialAges + 1
                    && AgeBookData.ageId(book).orElseThrow().equals(createdAge),
                    "reopening an allocated book must reuse the same Age");
            ItemStack secondBook = AgeBookData.create("Smoke second Age", pages("weathernorm"));
            player.setItemInHand(InteractionHand.MAIN_HAND, secondBook);
            check(AgeManager.prepare(player, secondBook), "second descriptive book must prepare successfully");
            AgeSavedData.Entry second = saved.get(AgeBookData.ageId(secondBook).orElseThrow());
            ServerLevel otherAge = server.getLevel(LinkDestination.read(secondBook).orElseThrow().dimension());
            check(second != null && otherAge != null && otherAge != age && player.getLevel() == origin
                    && !first.id().equals(second.id()) && !first.dimensionId().equals(second.dimensionId())
                    && first.seed() != second.seed() && saved.entries().size() == initialAges + 2,
                    "separate books must receive unique world identities and seeds without prepare teleporting");
            passed.add("Ages: allocated books reuse identity; separate books have distinct world keys and seeds");

            // Native holder counts include generation dependencies, not just full chunks.
            // getChunkNow is nonloading: inspect the vanilla radius-11 START footprint directly.
            otherAge.getChunkSource().tick(() -> false, false);
            BlockPos ageSpawn = otherAge.getSharedSpawnPos();
            int fullSpawnChunks = 0;
            for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
                if (otherAge.getChunkSource().getChunkNow((ageSpawn.getX() >> 4) + x,
                        (ageSpawn.getZ() >> 4) + z) != null) fullSpawnChunks++;
            }
            int dependencyHolders = otherAge.getChunkSource().getLoadedChunksCount();
            check(otherAge.players().isEmpty() && fullSpawnChunks < 529 && dependencyHolders < 2209,
                    "unoccupied prepared Age must not retain vanilla's spawn region: full="
                            + fullSpawnChunks + "/529, all holders=" + dependencyHolders + "/2209");
            passed.add("Ages: independent spawn avoids vanilla spawn region (full=" + fullSpawnChunks
                    + ", dependency holders=" + dependencyHolders + ")");

            long overworldDay = server.overworld().getDayTime();
            boolean overworldRain = server.overworld().getLevelData().isRaining();
            ServerLevelData firstData = (ServerLevelData) age.getLevelData();
            ServerLevelData secondData = (ServerLevelData) otherAge.getLevelData();
            firstData.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            firstData.setGameTime(4000); firstData.setDayTime(12345);
            firstData.setRaining(true); firstData.setThundering(true);
            secondData.setGameTime(8000); secondData.setDayTime(23456);
            secondData.setRaining(false); secondData.setRainTime(333);
            check(firstData.getGameTime() == 4000 && secondData.getGameTime() == 8000
                    && firstData.getDayTime() == 12345 && secondData.getDayTime() == 23456
                    && firstData.isRaining() && !firstData.isThundering() && !secondData.isRaining()
                    && secondData.getRainTime() == 333 && server.overworld().getDayTime() == overworldDay
                    && server.overworld().getLevelData().isRaining() == overworldRain,
                    "Age time/weather changes must remain independent of other Ages and overworld");
            CompoundTag offState = new CompoundTag(); offState.putString("MystcraftWeather", "off");
            AgeLevelData off = new AgeLevelData(server.getWorldData(), offState, () -> {});
            off.enforceWeather(server);
            ServerLevelData offData = off;
            offData.setRaining(true); offData.setThundering(true);
            check(!offData.isRaining() && !offData.isThundering(), "weather-off symbol must reject rain and thunder transitions");
            passed.add("Ages: independent time/weather state and enforced always-rain/weather-off symbols");

            age.getChunk(MARKER.getX() >> 4, MARKER.getZ() >> 4);
            check(age.getBlockState(MARKER).isAir(), "void terrain must generate empty remote chunks");
            check(age.setBlock(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3)
                    && age.getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "generated Age chunks must accept real persistent edits");
            AgeSavedData restored = AgeSavedData.load(((SavedData) saved).save(new CompoundTag()));
            AgeSavedData.Entry restoredFirst = restored.get(first.id());
            check(restoredFirst != null && restoredFirst.seed() == first.seed()
                    && restoredFirst.dimensionId().equals(first.dimensionId()) && restoredFirst.pages().equals(first.pages())
                    && restoredFirst.state().equals(first.state()), "SavedData must roundtrip canonical pages, seed, identity and mutable Age state");
            restoredFirst.state().putBoolean("SmokeDetachedCopy", true);
            check(!first.state().contains("SmokeDetachedCopy"), "SavedData reload must not alias live Age state");
            passed.add("Ages: real void-chunk generation/edit and canonical SavedData state/pages roundtrip");

            List<CompoundTag> unsupportedPages = pages("weathernorm");
            CompoundTag unsupported = new CompoundTag(); PageData.setLegacySymbol(unsupported, "mystcraft:smoke_unimplemented_symbol");
            List<CompoundTag> invalidPages = new ArrayList<>(unsupportedPages); invalidPages.add(unsupported);
            ItemStack rejected = AgeBookData.create("Unsupported smoke", invalidPages);
            check(!AgeManager.prepare(player, rejected) && AgeBookData.ageId(rejected).isEmpty()
                    && saved.entries().size() == initialAges + 2, "unsupported grammar must fail before allocating an identity");
            ResourceKey<Level> absent = ResourceKey.create(Registry.DIMENSION_REGISTRY,
                    new ResourceLocation("mystcraft_smoke", "never_allocated"));
            check(AgeManager.ensureLoaded(server, absent) == null && saved.entries().size() == initialAges + 2,
                    "resolving an unknown dimension must never allocate an Age");
            passed.add("Ages: unsupported symbols and unknown dimension lookups cannot allocate worlds");

            Fixture fixture = fixture(server);
            fixture.book = book.save(new CompoundTag());
            fixture.dimension = first.dimensionId().toString();
            fixture.age = first.id(); fixture.seed = first.seed(); fixture.loadedFromDisk = false;
            ((SavedData) fixture).setDirty();
            age.getChunkSource().save(true);
            server.overworld().getDataStorage().save();
            passed.add("Ages: restart fixture saved; verifyFixture must run in a subsequent server process");
            return List.copyOf(passed);
        } finally {
            LinkingNetwork.initialize(previous);
            if (player != null) {
                LinkingNetwork.clear(player.getUUID());
                player.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            }
            UUID allocated = createdAge;
            if (allocated != null) {
                for (LinkBookEntity entity : origin.getEntitiesOfClass(LinkBookEntity.class,
                        new AABB(44, 195, 44, 53, 205, 53),
                        entity -> AgeBookData.ageId(entity.getBook()).filter(allocated::equals).isPresent())) {
                    ((Entity) entity).discard();
                }
            }
        }
    }

    /** Does not create a fixture: requires one written by run(), followed by a real process restart. */
    public static List<String> verifyFixture(MinecraftServer server, ServerLevel origin) {
        check(server.isSameThread(), "Age restart verification must run on the server thread");
        Fixture fixture = fixture(server);
        check(fixture.loadedFromDisk && !fixture.book.isEmpty() && fixture.age != null,
                "restart verification requires a saved fixture reloaded in a different server process");
        ItemStack book = ItemStack.of(fixture.book);
        AgeSavedData.Entry entry = AgeManager.data(server).get(fixture.age);
        check(entry != null && entry.seed() == fixture.seed && entry.dimensionId().toString().equals(fixture.dimension)
                && entry.pages().equals(AgeBookData.pages(book)), "restart must retain original canonical Age/book identity, seed and pages");
        ServerLevel level = server.getLevel(LinkDestination.read(book).orElseThrow().dimension());
        check(level != null && level.getSeed() == fixture.seed,
                "startup lifecycle must restore Age before explicit book activation");
        level.getChunk(MARKER.getX() >> 4, MARKER.getZ() >> 4);
        check(level.getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "edited Age chunk must survive actual server restart");
        check(level.getDayTime() == 12345 && level.getGameTime() >= 4000
                && level.getLevelData().isRaining() && !level.getLevelData().isThundering(),
                "independent fixed-day and always-rain Age state must survive restart");
        return List.of("Ages restart: restored canonical identity/seed/pages before login, retained chunk marker and independent time/weather");
    }

    private static List<CompoundTag> pages(String weather) {
        ItemStack panel = new ItemStack(MystcraftItems.LINKING_PANEL.get());
        CompoundTag original = new CompoundTag(); original.putString("Marker", "nested payload survives");
        panel.getOrCreateTag().put("SmokeUnknown", original);
        CompoundTag terrain = new CompoundTag(); PageData.setLegacySymbol(terrain, "mystcraft:terrainvoid");
        CompoundTag climate = new CompoundTag(); PageData.setLegacySymbol(climate, "mystcraft:" + weather);
        var pages = new ArrayList<CompoundTag>(List.of(PageData.payload(panel), terrain, climate));
        for (String id : List.of("bioconnative", "lightingnormal", "sundark", "moondark", "starsdark",
                "featurelargedummy", "featuremediumdummy", "featuresmalldummy")) {
            CompoundTag page = new CompoundTag(); PageData.setLegacySymbol(page, "mystcraft:" + id); pages.add(page);
        }
        return pages;
    }

    private static ServerPlayer player(MinecraftServer server, ServerLevel level) {
        ServerPlayer player = new ServerPlayer(server, level,
                new GameProfile(UUID.randomUUID(), "MystSmokeAge"), null);
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener callback) {}
        };
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        player.setPos(48.5, 200, 48.5);
        level.addNewPlayer(player);
        return player;
    }

    private static Fixture fixture(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Fixture::load, Fixture::new, FIXTURE_FILE);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Capture implements LinkingNetwork.Transport {
        private UUID player;
        private UUID token;
        @Override public void sendToPlayer(ServerPlayer player, LinkingNetwork.OpenBook packet) {
            this.player = player.getUUID(); token = packet.token();
        }
        @Override public void sendRequest(UUID token) { throw new AssertionError("server smoke must not dispatch a client request"); }
        UUID takeToken(ServerPlayer player) {
            check(player.getUUID().equals(this.player) && token != null, "opening valid descriptive book must create a UI session");
            UUID result = token; token = null; return result;
        }
    }

    private static final class Fixture extends SavedData {
        private CompoundTag book = new CompoundTag();
        private String dimension = "";
        private UUID age;
        private long seed;
        private boolean loadedFromDisk;
        static Fixture load(CompoundTag tag) {
            Fixture fixture = new Fixture(); fixture.book = tag.getCompound("Book").copy();
            fixture.dimension = tag.getString("Dimension"); fixture.seed = tag.getLong("Seed");
            fixture.age = tag.hasUUID("Age") ? tag.getUUID("Age") : null;
            fixture.loadedFromDisk = true; return fixture;
        }
        @Override public CompoundTag save(CompoundTag tag) {
            tag.put("Book", book.copy()); tag.putString("Dimension", dimension); tag.putLong("Seed", seed);
            if (age != null) tag.putUUID("Age", age); return tag;
        }
    }
}
