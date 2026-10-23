package ports.jurassicraft.gametest;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import ports.jurassicraft.forge.ForgeMod;
import ports.jurassicraft.forge.JurassicBiomeModifier;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.village.VillageTrades;

/** Forge capabilities and events behind the shared GameTests. */
final class ForgeTestPlatform implements TestPlatform {
    @Override public @Nullable ItemAccess items(BlockEntity blockEntity, @Nullable Direction side) {
        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        return handler == null ? null : new ItemAccess() {
            @Override public int slots() { return handler.getSlots(); }
            @Override public ItemStack stack(int slot) { return handler.getStackInSlot(slot); }
            @Override public ItemStack insert(int slot, ItemStack stack, boolean simulate) { return handler.insertItem(slot, stack, simulate); }
            @Override public ItemStack extract(int slot, int amount, boolean simulate) { return handler.extractItem(slot, amount, simulate); }
        };
    }

    @Override public DeathWatch watchDeaths(LivingEntity entity) {
        DeathListener listener = new DeathListener(entity);
        MinecraftForge.EVENT_BUS.register(listener);
        return listener;
    }

    @Override public void denyMobGriefing(LivingEntity entity, Runnable action) {
        GriefingDenial denial = new GriefingDenial(entity);
        MinecraftForge.EVENT_BUS.register(denial);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(denial);
        }
    }

    /** Forge's fire reads the vanilla table through {@code IForgeBlock}: ignite odds are the burn chance, burn odds the spread chance. */
    @Override public @Nullable int[] fireOdds(ServerLevel level, BlockPos pos, Block block) {
        BlockState state = block.defaultBlockState();
        int burn = state.getFireSpreadSpeed(level, pos, Direction.UP), spread = state.getFlammability(level, pos, Direction.UP);
        return burn == 0 && spread == 0 ? null : new int[]{burn, spread};
    }

    /** Applied twice, as a reload re-firing the event would: Forge's sink must add each named pool once. */
    @Override public @Nullable LootTable loot(Consumer<JurassicEvents.LootSink> additions) {
        LootTable table = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST).build();
        JurassicEvents.LootSink adapter = ForgeMod.lootSink(table);
        boolean[] added = {false};
        JurassicEvents.LootSink counted = (name, pool) -> { added[0] = true; adapter.addPool(name, pool); };
        additions.accept(counted);
        additions.accept(counted);
        return added[0] ? table : null;
    }

    @Override public Int2ObjectMap<List<VillagerTrades.ItemListing>> trades(VillagerProfession profession) {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = new Int2ObjectOpenHashMap<>();
        VillageTrades.addTo(ForgeMod.tradeSink(profession, trades));
        return trades;
    }

    @Override public boolean recordsIndexedBySound() { return false; }

    @Override public Map<String, Registry<?>> loaderDataRegistries(RegistryAccess access) {
        return Map.of("forge/biome_modifier", access.registryOrThrow(ForgeRegistries.Keys.BIOME_MODIFIERS));
    }

    @Override public List<MobSpawnSettings.SpawnerData> naturalSpawns(Holder<Biome> biome, MobCategory category) {
        var server = ServerLifecycleHooks.getCurrentServer();
        var modifier = new JurassicBiomeModifier(server.registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY));
        var info = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(biome.value().modifiableBiomeInfo().getOriginalBiomeInfo());
        modifier.modify(biome, BiomeModifier.Phase.ADD, info);
        return List.copyOf(info.getMobSpawnSettings().getSpawner(category));
    }

    // The event bus generates a public invoker for each listener, so listeners are public classes.
    public static final class DeathListener implements DeathWatch {
        private final LivingEntity entity;
        private int calls;
        private boolean cancel;

        DeathListener(LivingEntity entity) { this.entity = entity; }

        @SubscribeEvent public void onDeath(LivingDeathEvent event) {
            if (event.getEntity() != entity) return;
            calls++;
            if (cancel) event.setCanceled(true);
        }

        @Override public int calls() { return calls; }
        @Override public void cancel(boolean value) { cancel = value; }
        @Override public void close() { MinecraftForge.EVENT_BUS.unregister(this); }
    }

    public static final class GriefingDenial {
        private final LivingEntity entity;

        GriefingDenial(LivingEntity entity) { this.entity = entity; }

        @SubscribeEvent public void onGriefing(EntityMobGriefingEvent event) {
            if (event.getEntity() == entity) event.setResult(Event.Result.DENY);
        }
    }
}
