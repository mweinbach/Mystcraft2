package ports.jurassicraft.maps;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisitorCentreMapGameTests {
    private VisitorCentreMapGameTests() {}

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void cartographerTradeRequiresAnAvailableStructure(GameTestHelper helper) {
        var trades = new Int2ObjectOpenHashMap<List<VillagerTrades.ItemListing>>();
        VisitorCentreMaps.onVillagerTrades(new VillagerTradesEvent(trades, VillagerProfession.FARMER));
        require(helper, trades.isEmpty(), "only cartographers receive this trade");
        VisitorCentreMaps.onVillagerTrades(new VillagerTradesEvent(trades, VillagerProfession.CARTOGRAPHER));
        require(helper, trades.size() == 1 && trades.get(4).size() == 1, "cartographers receive an expert-level listing");
        require(helper, VisitorCentreMaps.offer(helper.getLevel(), RandomSource.create(), false) == null,
                "disabled structure generation must not sell a map");
        var testChunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        require(helper, VisitorCentreMaps.destination(helper.getLevel(), testChunk).isEmpty(),
                "a chunk without a visitor centre start must not be a destination");
        helper.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void visitorMapMarksTheDestination(GameTestHelper helper) {
        BlockPos destination = helper.absolutePos(new BlockPos(1, 0, 1));
        var offer = VisitorCentreMaps.createOffer(helper.getLevel(), destination, RandomSource.create(123));
        var map = offer.getResult();
        var data = MapItem.getSavedData(map, helper.getLevel());
        var icons = map.getOrCreateTag().getList("Decorations", Tag.TAG_COMPOUND);
        require(helper, offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() >= 16
                && offer.getBaseCostA().getCount() <= 27 && offer.getCostB().is(Items.COMPASS),
                "the map costs 16 to 27 emeralds and a compass");
        require(helper, map.is(Items.FILLED_MAP) && map.getHoverName().equals(Component.translatable(VisitorCentreMaps.MAP_NAME))
                && data != null && data.scale == 2 && data.dimension.equals(helper.getLevel().dimension()),
                "the offer contains a named exploration map for this dimension");
        require(helper, icons.size() == 1 && icons.getCompound(0).getByte("type") == MapDecoration.Type.MANSION.getIcon()
                && icons.getCompound(0).getDouble("x") == destination.getX()
                && icons.getCompound(0).getDouble("z") == destination.getZ(),
                "the mansion marker points to the destination");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
