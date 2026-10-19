package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.exploration.ArchivistHouseElement;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;
import net.mystcraft.minecraft.exploration.VillageArchivistPools;
import net.mystcraft.minecraft.writing.WritingDeskBlockEntity;

public final class VillageArchivistSmoke {
    private VillageArchivistSmoke() {}
    public static State begin(MinecraftServer server, ServerLevel level) { return new State(server, level); }
    /** Synchronous plain-loader compatibility; full smoke uses begin/tick for pack spawn queues. */
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        State state = begin(server, level);
        try { return state.verify(); } finally { state.close(); }
    }
    public static final class State implements AutoCloseable {
        private final ServerLevel level;
        private final ArrayList<String> passed = new ArrayList<>();
        private final BlockPos origin = new BlockPos(3448, 200, 3448);
        private final BoundingBox clip = new BoundingBox(3447, 198, 3447, 3458, 213, 3458);
        private final AABB area = new AABB(3447, 198, 3447, 3459, 214, 3459);
        private final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        private final Map<BlockPos, CompoundTag> data = new LinkedHashMap<>();
        private final java.util.Set<UUID> previousEntities;
        private final StructurePoolElement element;
        private int ticks;
        private boolean closed;
        private State(MinecraftServer server, ServerLevel level) {
            this.level = level;
            previousEntities = level.getEntities((Entity) null, area).stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        var pools = server.registryAccess().registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
        for (String type : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            var pool = pools.get(new ResourceLocation("minecraft", "village/" + type + "/houses"));
            require(pool != null && pool.getShuffledTemplates(RandomSource.create(9)).stream()
                    .filter(element -> element instanceof ArchivistHouseElement).count() == 20,
                    "Native village pool did not retain source weight20 archivist house: " + type);
        }
        var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        var encoded = StructurePoolElement.CODEC.encodeStart(ops, ArchivistHouseElement.create()).getOrThrow(false, message -> {});
        element = StructurePoolElement.CODEC.parse(ops, encoded).getOrThrow(false, message -> {});
        require(element instanceof ArchivistHouseElement, "Persisted jigsaw element lost custom marker behavior after codec reload");
        require(server.getStructureManager().get(ArchivistHouseElement.TEMPLATE).isPresent(), "Classic archivist template is missing");
        passed.add("village archivist: five native house pools add exact weight20 without registry replacement; custom jigsaw piece survives registry-aware save/reload codec");
            try {
            for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(10, 12, 10))) {
                BlockPos pos = cursor.immutable(); states.put(pos, level.getBlockState(pos));
                BlockEntity be = level.getBlockEntity(pos); if (be != null) data.put(pos, be.saveWithFullMetadata());
                level.removeBlockEntity(pos);
                level.setBlock(pos, pos.getY() == 199 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
            require(element.place(server.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(),
                    origin, origin, Rotation.NONE, clip, RandomSource.create(7129), false), "Actual native house template placement failed");
            long shelves = states.keySet().stream().filter(pos -> level.getBlockState(pos).is(Blocks.BOOKSHELF)).count();
            require(shelves == 8, "Classic village house bookshelf count differs: " + shelves);
            for (BlockPos pos : List.of(origin.offset(1, 2, 2), origin.offset(1, 2, 3)))
                require(level.getBlockEntity(pos) instanceof MystLecternBlockEntity lectern && !lectern.getBook().isEmpty(),
                        "Village treasure lectern missing its actual page at " + pos + ": state=" + level.getBlockState(pos)
                                + ", blockEntity=" + level.getBlockEntity(pos));
            boolean desk = VillageArchivistPools.settings().villageDeskGen();
            require(level.getBlockState(origin.offset(4, 1, 2)).is(WritingDeskRegistry.WRITING_DESK.get()) == desk, "villageDeskGen did not govern source desk");
            if (desk) require(level.getBlockEntity(origin.offset(4, 1, 2)) instanceof WritingDeskBlockEntity be
                    && be.inventory.isEmpty() && be.ink() == 0, "Village desk acquired invented starting inventory/ink");
            } catch (Throwable failure) { close(); throw failure; }
        }
        /** Wait for asynchronous pack conversion; never tick a world recursively. */
        public boolean tick() {
            if (++ticks < 40) return false;
            var villagers = level.getEntitiesOfClass(Villager.class, area, villager -> !previousEntities.contains(villager.getUUID()));
            if (VillageArchivistPools.settings().archivistEnabled() && villagers.isEmpty() && ticks < 80) return false;
            try { verify(); return true; } finally { close(); }
        }
        public List<String> passed() { return List.copyOf(passed); }
        private List<String> verify() {
            var villagers = level.getEntitiesOfClass(Villager.class, area, villager -> !previousEntities.contains(villager.getUUID()));
            require(villagers.size() == (VillageArchivistPools.settings().archivistEnabled() ? 1 : 0), "Archivist spawn setting/count differs: enabled=" + VillageArchivistPools.settings().archivistEnabled()
                    + ", actualVillagers=" + villagers.size() + ", freshEntities=" + entityDiagnostics(level, area, previousEntities));
            if (!villagers.isEmpty()) require(villagers.get(0).getVillagerData().getProfession() == ExplorationRegistry.ARCHIVIST.get(), "House spawned wrong native profession: " + entityDiagnostics(level, area, previousEntities));
            if (!villagers.isEmpty()) {
                Villager archivist = villagers.get(0);
                net.mystcraft.minecraft.exploration.ArchivistShop.ensureOffers(archivist);
                require(archivist.getVillagerXp() >= 1 && !archivist.getTags().contains(net.mystcraft.minecraft.exploration.ArchivistCareer.INITIALIZE),
                        "Archivist permanent-career initialization was not consumed: xp=" + archivist.getVillagerXp() + ", tags=" + archivist.getTags());
                var offers = archivist.getOffers();
                long pages = offers.stream().filter(offer -> net.mystcraft.minecraft.writing.PageData.symbol(offer.getResult()).isPresent()).count();
                long boosters = offers.stream().filter(offer -> offer.getResult().is(net.mystcraft.integration.MystcraftItems.BOOSTER.get())).count();
                require(offers.size() == 4 && pages == 3 && boosters == 1,
                        "Spawned archivist lost native merchant page/booster trades: offers=" + offers.size()
                                + ", pages=" + pages + ", boosters=" + boosters + ", entities=" + entityDiagnostics(level, area, previousEntities));
            }
            passed.add("village archivist: actual template places source house, eight bookshelves, two loot lecterns, configured empty two-block desk and native archivist profession and actual page/booster trades after forty server ticks");
            var disabled = new VillageArchivistPools.Settings(false, false);
            int before = level.getEntitiesOfClass(Villager.class, area).size();
            BlockPos markerPos = origin.offset(4, 8, 4);
            for (String action : List.of("desk_head", "desk_foot", "archivist")) {
                var nbt = new CompoundTag(); nbt.putString("metadata", action);
                ((ArchivistHouseElement) element).handleDataMarker(level,
                        new StructureTemplate.StructureBlockInfo(markerPos, Blocks.STRUCTURE_BLOCK.defaultBlockState(), nbt),
                        origin, Rotation.NONE, RandomSource.create(44), clip, disabled);
            }
            require(level.isEmptyBlock(markerPos) && level.getEntitiesOfClass(Villager.class, area).size() == before,
                    "Disabled village desk/archivist flags still placed contents");
            passed.add("village archivist: disabled desk and native archivist flags suppress their markers while retaining the source house/lectern content");
            return List.copyOf(passed);
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            for (var entity : level.getEntities((Entity) null, area)) if (!previousEntities.contains(entity.getUUID())) entity.discard();
            for (var entry : states.entrySet()) { level.removeBlockEntity(entry.getKey()); level.setBlock(entry.getKey(), entry.getValue(), 18); }
            for (var entry : data.entrySet()) {
                BlockEntity be = level.getBlockEntity(entry.getKey()); if (be != null) { be.load(entry.getValue()); be.setChanged(); }
            }
        }
    }
    private static String entityDiagnostics(ServerLevel level, AABB area, java.util.Set<UUID> previous) {
        return level.getEntities((Entity) null, area.inflate(8)).stream()
                .filter(entity -> !previous.contains(entity.getUUID()))
                .map(entity -> "{type=" + Registry.ENTITY_TYPE.getKey(entity.getType())
                        + ", class=" + entity.getClass().getName() + ", uuid=" + entity.getUUID()
                        + ", pos=" + entity.position() + ", removed=" + entity.isRemoved()
                        + ", profession=" + (entity instanceof Villager villager
                            ? Registry.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()) : "not-native-Villager") + "}")
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
