package ports.jurassicraft.village;

import com.google.common.collect.ImmutableSet;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Registers village professions, their workstations, and the structure processor. */
public final class VillageRegistry {
    public static final Supplier<Block> ITEM_HOLDER = hold(Registry.register(Registry.BLOCK,id("ancient_item_holding_block"),new AncientItemHoldingBlock()));
    public static final Supplier<Item> ITEM_HOLDER_ITEM = hold(Registry.register(Registry.ITEM,id("ancient_item_holding_block"),new BlockItem(ITEM_HOLDER.get(),new Item.Properties().tab(JurassicContent.TAB))));
    public static final Supplier<BlockEntityType<AncientItemHoldingBlockEntity>> ITEM_HOLDER_ENTITY = hold(Registry.register(Registry.BLOCK_ENTITY_TYPE,id("ancient_item_holding_block"),BlockEntityType.Builder.of(AncientItemHoldingBlockEntity::new,ITEM_HOLDER.get()).build(null)));
    public static final Supplier<PoiType> GENETICIST_POI = hold(PointOfInterestHelper.register(id("geneticist"),1,1,DnaMachineRegistry.EXTRACTOR.get()));
    public static final Supplier<PoiType> PALEONTOLOGIST_POI = hold(PointOfInterestHelper.register(id("paleontologist"),1,1,CleaningStationRegistry.BLOCK.get()));
    public static final Supplier<VillagerProfession> GENETICIST = hold(Registry.register(Registry.VILLAGER_PROFESSION,id("geneticist"),profession("geneticist")));
    public static final Supplier<VillagerProfession> PALEONTOLOGIST = hold(Registry.register(Registry.VILLAGER_PROFESSION,id("paleontologist"),profession("paleontologist")));
    public static final Supplier<StructureProcessorType<VillageProcessor>> PROCESSOR = hold(Registry.register(Registry.STRUCTURE_PROCESSOR,id("village"),() -> VillageProcessor.CODEC));
    private VillageRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID,path); }
    private static <T> Supplier<T> hold(T value) { return () -> value; }
    private static VillagerProfession profession(String id) {
        ResourceKey<PoiType> key = ResourceKey.create(Registry.POINT_OF_INTEREST_TYPE_REGISTRY,id(id));
        return new VillagerProfession(id,holder -> holder.is(key),holder -> holder.is(key),ImmutableSet.of(),ImmutableSet.of(),SoundEvents.VILLAGER_WORK_LIBRARIAN);
    }
    public static void init() { VillageTrades.init(); VisitorCentreMaps.init(); VillagePools.init(); PaleontologistDigGoal.init(); }
}
