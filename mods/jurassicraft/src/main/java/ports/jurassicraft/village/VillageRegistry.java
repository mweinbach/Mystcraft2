package ports.jurassicraft.village;

import com.google.common.collect.ImmutableSet;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.machine.DnaMachineRegistry;
import ports.jurassicraft.platform.Platform;

/** Registers village professions, their workstations, and the structure processor. */
public final class VillageRegistry {
    public static final Supplier<Block> ITEM_HOLDER = Platform.register(Registry.BLOCK_REGISTRY, "ancient_item_holding_block", AncientItemHoldingBlock::new);
    public static final Supplier<Item> ITEM_HOLDER_ITEM = Platform.register(Registry.ITEM_REGISTRY, "ancient_item_holding_block", () -> new BlockItem(ITEM_HOLDER.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<BlockEntityType<AncientItemHoldingBlockEntity>> ITEM_HOLDER_ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "ancient_item_holding_block", () -> BlockEntityType.Builder.of(AncientItemHoldingBlockEntity::new, ITEM_HOLDER.get()).build(null));
    public static final Supplier<PoiType> GENETICIST_POI = Platform.register(Registry.POINT_OF_INTEREST_TYPE_REGISTRY, "geneticist", () -> point(DnaMachineRegistry.EXTRACTOR.get()));
    public static final Supplier<PoiType> PALEONTOLOGIST_POI = Platform.register(Registry.POINT_OF_INTEREST_TYPE_REGISTRY, "paleontologist", () -> point(CleaningStationRegistry.BLOCK.get()));
    public static final Supplier<VillagerProfession> GENETICIST = Platform.register(Registry.VILLAGER_PROFESSION_REGISTRY, "geneticist", () -> profession("geneticist"));
    public static final Supplier<VillagerProfession> PALEONTOLOGIST = Platform.register(Registry.VILLAGER_PROFESSION_REGISTRY, "paleontologist", () -> profession("paleontologist"));
    public static final Supplier<StructureProcessorType<VillageProcessor>> PROCESSOR = Platform.register(Registry.STRUCTURE_PROCESSOR_REGISTRY, "village", () -> () -> VillageProcessor.CODEC);
    private VillageRegistry() {}
    private static PoiType point(Block block) { return new PoiType(ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates()), 1, 1); }
    private static VillagerProfession profession(String id) {
        ResourceKey<PoiType> key = ResourceKey.create(Registry.POINT_OF_INTEREST_TYPE_REGISTRY, new ResourceLocation(PortMod.MOD_ID, id));
        return new VillagerProfession(id, holder -> holder.is(key), holder -> holder.is(key), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_LIBRARIAN);
    }
    public static void init() { }
}
