package ports.jurassicraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.fabric.FabricMod;
import ports.jurassicraft.worldgen.NaturalSpawns;

/** Fabric's Transfer API and callbacks behind the shared GameTests. */
final class FabricTestPlatform implements TestPlatform {
    private final List<Watch> deathWatches = new CopyOnWriteArrayList<>();

    FabricTestPlatform() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            boolean allow = true;
            for (Watch watch : deathWatches) if (watch.entity == entity) allow &= watch.ask();
            return allow;
        });
    }

    @Override public @Nullable ItemAccess items(BlockEntity blockEntity, @Nullable Direction side) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(),
                blockEntity.getBlockState(), blockEntity, side);
        return storage == null ? null : new ItemAccess() {
            private List<SingleSlotStorage<ItemVariant>> slotList() {
                // A face is an InventoryStorage; unsided access combines the input and output faces in that order.
                if (storage instanceof InventoryStorage inventory) return inventory.getSlots();
                if (storage instanceof CombinedStorage<ItemVariant, ?> combined && combined.parts.stream().allMatch(InventoryStorage.class::isInstance))
                    return combined.parts.stream().flatMap(part -> ((InventoryStorage) part).getSlots().stream()).toList();
                throw new IllegalStateException(blockEntity + " exposes unslotted storage " + storage);
            }

            @Override public int slots() { return slotList().size(); }

            @Override public ItemStack stack(int slot) {
                SingleSlotStorage<ItemVariant> view = slotList().get(slot);
                return view.isResourceBlank() ? ItemStack.EMPTY : view.getResource().toStack((int) view.getAmount());
            }

            @Override public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) return ItemStack.EMPTY;
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = slotList().get(slot).insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    if (!simulate) transaction.commit();
                    return copyWithCount(stack, stack.getCount() - (int) inserted);
                }
            }

            @Override public ItemStack extract(int slot, int amount, boolean simulate) {
                SingleSlotStorage<ItemVariant> view = slotList().get(slot);
                if (amount <= 0 || view.isResourceBlank()) return ItemStack.EMPTY;
                ItemVariant variant = view.getResource();
                try (Transaction transaction = Transaction.openOuter()) {
                    long extracted = view.extract(variant, amount, transaction);
                    if (!simulate) transaction.commit();
                    return extracted == 0 ? ItemStack.EMPTY : variant.toStack((int) extracted);
                }
            }

            // Whole-face transfers go through the storage itself, as Fabric pipes and hoppers do.
            @Override public ItemStack insert(ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) return ItemStack.EMPTY;
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    if (!simulate) transaction.commit();
                    return copyWithCount(stack, stack.getCount() - (int) inserted);
                }
            }

            @Override public ItemStack extract(ItemStack match, int amount, boolean simulate) {
                if (match.isEmpty() || amount <= 0) return ItemStack.EMPTY;
                ItemVariant variant = ItemVariant.of(match);
                try (Transaction transaction = Transaction.openOuter()) {
                    long extracted = storage.extract(variant, amount, transaction);
                    if (!simulate) transaction.commit();
                    return extracted == 0 ? ItemStack.EMPTY : variant.toStack((int) extracted);
                }
            }
        };
    }

    private static ItemStack copyWithCount(ItemStack stack, int count) {
        if (count <= 0) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    @Override public DeathWatch watchDeaths(LivingEntity entity) {
        Watch watch = new Watch(entity);
        deathWatches.add(watch);
        return watch;
    }

    /** Fabric has no per-entity griefing event: the mobGriefing game rule is the only control. */
    @Override public void denyMobGriefing(LivingEntity entity, Runnable action) {
        GameRules.BooleanValue rule = entity.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean previous = rule.get();
        rule.set(false, entity.getServer());
        try {
            action.run();
        } finally {
            rule.set(previous, entity.getServer());
        }
    }

    /**
     * Fabric applies {@code BiomeModifications.addSpawn} once at startup, so this evaluates the same
     * selector ({@link NaturalSpawns#adds}) against the loaded biome with the current config.
     */
    @Override public @Nullable int[] fireOdds(ServerLevel level, BlockPos pos, Block block) {
        FlammableBlockRegistry.Entry entry = FlammableBlockRegistry.getDefaultInstance().get(block);
        return entry == null ? null : new int[]{entry.getBurnChance(), entry.getSpreadChance()};
    }

    @Override public @Nullable LootTable loot(Consumer<JurassicEvents.LootSink> additions) {
        LootTable.Builder builder = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST);
        JurassicEvents.LootSink adapter = FabricMod.lootSink(builder);
        boolean[] added = {false};
        additions.accept((name, pool) -> { added[0] = true; adapter.addPool(name, pool); });
        return added[0] ? builder.build() : null;
    }

    /** FabricMod hands JurassiCraft's trades to {@code TradeOfferHelper}, which writes them into {@code VillagerTrades.TRADES}. */
    @Override public Int2ObjectMap<List<VillagerTrades.ItemListing>> trades(VillagerProfession profession) {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = new Int2ObjectOpenHashMap<>();
        var registered = VillagerTrades.TRADES.get(profession);
        if (registered != null) registered.forEach((level, listings) -> trades.put((int) level, List.of(listings)));
        return trades;
    }

    @Override public Map<String, Registry<?>> loaderDataRegistries(RegistryAccess access) { return Map.of(); }

    @Override public boolean recordsIndexedBySound() { return true; }

    @Override public List<MobSpawnSettings.SpawnerData> naturalSpawns(Holder<Biome> biome, MobCategory category) {
        List<MobSpawnSettings.SpawnerData> spawns = new ArrayList<>(biome.value().getMobSettings().getMobs(category).unwrap());
        NaturalSpawns.BiomeView view = new NaturalSpawns.BiomeView() {
            @Override public boolean is(TagKey<Biome> tag) { return biome.is(tag); }
            @Override public boolean spawns(MobCategory spawnCategory, EntityType<?> type) {
                return biome.value().getMobSettings().getMobs(spawnCategory).unwrap().stream().anyMatch(spawn -> spawn.type == type);
            }
        };
        for (NaturalSpawns.Entry entry : NaturalSpawns.entries())
            if (entry.category() == category && NaturalSpawns.adds(entry, view))
                spawns.add(new MobSpawnSettings.SpawnerData(entry.type(), entry.weight(), entry.minCount(), entry.maxCount()));
        return spawns;
    }

    private final class Watch implements DeathWatch {
        private final LivingEntity entity;
        private int calls;
        private boolean cancel;

        Watch(LivingEntity entity) { this.entity = entity; }

        boolean ask() {
            calls++;
            return !cancel;
        }

        @Override public int calls() { return calls; }
        @Override public void cancel(boolean value) { cancel = value; }
        @Override public void close() { deathWatches.remove(this); }
    }
}
