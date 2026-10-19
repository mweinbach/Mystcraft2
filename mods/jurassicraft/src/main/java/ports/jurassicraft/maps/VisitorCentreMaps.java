package ports.jurassicraft.maps;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.structures.VisitorPlacement;

/** The expert cartographer's map to this world's visitor centre. */
public final class VisitorCentreMaps {
    private static final ResourceLocation STRUCTURE = new ResourceLocation(PortMod.MOD_ID, "visitor_centre");
    static final String MAP_NAME = "filled_map.jurassicraft.visitorcenter";

    private VisitorCentreMaps() {}

    public static void addTo(JurassicEvents.TradeSink sink) {
        sink.add(VillagerProfession.CARTOGRAPHER, 4, VisitorCentreMaps::offer);
    }

    @Nullable
    private static MerchantOffer offer(Entity merchant, RandomSource random) {
        if (!(merchant.level instanceof ServerLevel level)) return null;
        return offer(level, random, JurassicConfig.GENERATE_VISITOR_CENTRE.get());
    }

    @Nullable
    static MerchantOffer offer(ServerLevel level, RandomSource random, boolean generationEnabled) {
        Optional<BlockPos> destination = VisitorPlacement.candidate(level, generationEnabled).flatMap(pos -> destination(level, pos));
        return destination.map(pos -> createOffer(level, pos, random)).orElse(null);
    }

    /** Generating structure starts (not the full chunk) confirms a candidate was not suppressed by a datapack. */
    static Optional<BlockPos> destination(ServerLevel level, ChunkPos candidate) {
        var structure = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).get(STRUCTURE);
        if (structure == null) return Optional.empty();
        var chunk = level.getChunkSource().getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true);
        if (chunk == null) return Optional.empty();
        var start = chunk.getStartForStructure(structure);
        return start != null && start.isValid() ? Optional.of(start.getBoundingBox().getCenter()) : Optional.empty();
    }

    static MerchantOffer createOffer(ServerLevel level, BlockPos destination, RandomSource random) {
        ItemStack map = MapItem.create(level, destination.getX(), destination.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, destination, "+", MapDecoration.Type.MANSION);
        map.setHoverName(Component.translatable(MAP_NAME));
        return new MerchantOffer(new ItemStack(Items.EMERALD, 16 + random.nextInt(12)),
                new ItemStack(Items.COMPASS), map, 7, 15, 0.2F);
    }
}
