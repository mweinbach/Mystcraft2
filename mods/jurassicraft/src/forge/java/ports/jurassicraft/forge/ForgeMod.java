package ports.jurassicraft.forge;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.platform.Platform;

/** Forge entrypoint: installs the platform and forwards Forge events to the shared handlers. */
@Mod(PortMod.MOD_ID)
public final class ForgeMod {
    public ForgeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        Platform.install(new ForgePlatform(modBus));
        new ForgeConfigBackend().register(modBus);
        PortMod.init();

        DeferredRegister<Codec<? extends BiomeModifier>> biomeModifiers =
                DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, PortMod.MOD_ID);
        biomeModifiers.register("biome_additions", () -> JurassicBiomeModifier.CODEC);
        biomeModifiers.register(modBus);

        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(PortMod::setup));
        modBus.addListener((EntityAttributeCreationEvent event) -> JurassicEvents.attributes(event::put));
        modBus.addListener(ForgeMod::spawnPlacements);

        forgeBus.addListener((RegisterCommandsEvent event) -> JurassicEvents.commands(event.getDispatcher()));
        forgeBus.addListener((ServerAboutToStartEvent event) -> JurassicEvents.serverAboutToStart(event.getServer()));
        forgeBus.addListener((EntityJoinLevelEvent event) -> JurassicEvents.entityJoined(event.getEntity()));
        forgeBus.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel && event.getChunk() instanceof LevelChunk chunk) JurassicEvents.chunkLoaded(chunk);
        });
        forgeBus.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) JurassicEvents.serverTick(event.getServer());
        });
        forgeBus.addListener(EventPriority.LOWEST, false, ForgeMod::blockBroken);
        forgeBus.addListener(ForgeMod::lootTable);
        forgeBus.addListener(ForgeMod::trades);
        forgeBus.addGenericListener(BlockEntity.class, ForgeMod::itemHandlers);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClient.init(modBus, forgeBus));
    }

    private static void spawnPlacements(SpawnPlacementRegisterEvent event) {
        JurassicEvents.spawnPlacements(new JurassicEvents.SpawnPlacementSink() {
            @Override public <T extends Mob> void register(EntityType<T> type, SpawnPlacements.Type placement, Heightmap.Types heightmap,
                    SpawnPlacements.SpawnPredicate<T> predicate) {
                event.register(type, placement, heightmap, predicate, SpawnPlacementRegisterEvent.Operation.REPLACE);
            }
        });
    }

    private static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) JurassicEvents.blockBroken(level, event.getPlayer(), event.getPos(), event.getState());
    }

    private static void lootTable(LootTableLoadEvent event) { JurassicEvents.lootTable(event.getName(), lootSink(event.getTable())); }
    private static void trades(VillagerTradesEvent event) { JurassicEvents.trades(tradeSink(event.getType(), event.getTrades())); }

    /** Adds each named pool once; public so in-world tests exercise the same adapter. */
    public static JurassicEvents.LootSink lootSink(LootTable table) {
        return (name, pool) -> {
            if (table.getPool(name) == null) table.addPool(pool.name(name).build());
        };
    }
    public static JurassicEvents.TradeSink tradeSink(VillagerProfession type, Int2ObjectMap<List<VillagerTrades.ItemListing>> trades) {
        return (profession, level, listing) -> {
            if (profession == type) trades.computeIfAbsent(level, key -> new ArrayList<>()).add(listing);
        };
    }

    /** Pipes reach shared machines through their vanilla sided-container contract. */
    private static void itemHandlers(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof WorldlyContainer container)) return;
        ResourceLocation type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(event.getObject().getType());
        if (type == null || !PortMod.MOD_ID.equals(type.getNamespace())) return;
        SidedItemHandlers handlers = new SidedItemHandlers(container);
        event.addCapability(new ResourceLocation(PortMod.MOD_ID, "items"), handlers);
        event.addListener(handlers::invalidate);
    }

    private static final class SidedItemHandlers implements ICapabilityProvider {
        private final WorldlyContainer container;
        @SuppressWarnings("unchecked")
        private final LazyOptional<IItemHandler>[] sides = new LazyOptional[Direction.values().length];
        private LazyOptional<IItemHandler> unsided;

        SidedItemHandlers(WorldlyContainer container) { this.container = container; }

        @Override public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (capability != ForgeCapabilities.ITEM_HANDLER) return LazyOptional.empty();
            if (side == null) {
                if (unsided == null) unsided = LazyOptional.of(this::unsidedHandler);
                return unsided.cast();
            }
            int index = side.get3DDataValue();
            if (sides[index] == null) sides[index] = LazyOptional.of(() -> new SidedInvWrapper(container, side));
            return sides[index].cast();
        }

        /** Unsided access sees the input and output faces; identical faces are exposed once. */
        private IItemHandler unsidedHandler() {
            if (Arrays.equals(container.getSlotsForFace(Direction.UP), container.getSlotsForFace(Direction.DOWN)))
                return new SidedInvWrapper(container, Direction.UP);
            return new CombinedInvWrapper(new SidedInvWrapper(container, Direction.UP), new SidedInvWrapper(container, Direction.DOWN));
        }

        void invalidate() {
            for (LazyOptional<IItemHandler> side : sides) if (side != null) side.invalidate();
            if (unsided != null) unsided.invalidate();
        }
    }
}
