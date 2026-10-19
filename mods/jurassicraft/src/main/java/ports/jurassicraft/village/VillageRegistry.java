package ports.jurassicraft.village;

import com.google.common.collect.ImmutableSet;
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
import ports.jurassicraft.content.JurassicContent;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Registers village professions, their workstations, and the structure processor. */
public final class VillageRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    public static final RegistryObject<Block> ITEM_HOLDER = BLOCKS.register("ancient_item_holding_block", AncientItemHoldingBlock::new);
    public static final RegistryObject<Item> ITEM_HOLDER_ITEM = ITEMS.register("ancient_item_holding_block", () -> new BlockItem(ITEM_HOLDER.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<BlockEntityType<AncientItemHoldingBlockEntity>> ITEM_HOLDER_ENTITY = ENTITIES.register("ancient_item_holding_block", () -> BlockEntityType.Builder.of(AncientItemHoldingBlockEntity::new, ITEM_HOLDER.get()).build(null));
    private static final DeferredRegister<PoiType> POIS = DeferredRegister.create(ForgeRegistries.POI_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, PortMod.MOD_ID);
    private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registry.STRUCTURE_PROCESSOR_REGISTRY, PortMod.MOD_ID);
    public static final RegistryObject<PoiType> GENETICIST_POI = POIS.register("geneticist", () -> point(DnaMachineRegistry.EXTRACTOR.get()));
    public static final RegistryObject<PoiType> PALEONTOLOGIST_POI = POIS.register("paleontologist", () -> point(CleaningStationRegistry.BLOCK.get()));
    public static final RegistryObject<VillagerProfession> GENETICIST = PROFESSIONS.register("geneticist", () -> profession("geneticist"));
    public static final RegistryObject<VillagerProfession> PALEONTOLOGIST = PROFESSIONS.register("paleontologist", () -> profession("paleontologist"));
    public static final RegistryObject<StructureProcessorType<VillageProcessor>> PROCESSOR = PROCESSORS.register("village", () -> () -> VillageProcessor.CODEC);
    private VillageRegistry() {}
    private static PoiType point(Block block) { return new PoiType(ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates()), 1, 1); }
    private static VillagerProfession profession(String id) {
        ResourceKey<PoiType> key = ResourceKey.create(Registry.POINT_OF_INTEREST_TYPE_REGISTRY, new ResourceLocation(PortMod.MOD_ID, id));
        return new VillagerProfession(id, holder -> holder.is(key), holder -> holder.is(key), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_LIBRARIAN);
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); POIS.register(bus); PROFESSIONS.register(bus); PROCESSORS.register(bus); }
}
