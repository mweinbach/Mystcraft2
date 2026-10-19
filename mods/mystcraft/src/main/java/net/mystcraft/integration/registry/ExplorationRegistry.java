package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.exploration.ClassicCrystalFeature;
import net.mystcraft.minecraft.exploration.ClassicLibraryFeature;
import net.mystcraft.minecraft.exploration.MystLecternBlock;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;
import net.mystcraft.minecraft.exploration.SymbolPageLootFunction;

public final class ExplorationRegistry {
    public static Supplier<net.minecraft.world.entity.ai.village.poi.PoiType> ARCHIVIST_POI;
    public static Supplier<net.minecraft.world.entity.npc.VillagerProfession> ARCHIVIST;
    public static Supplier<Block> LECTERN;
    public static Supplier<Item> LECTERN_ITEM;
    public static Supplier<BlockEntityType<MystLecternBlockEntity>> LECTERN_ENTITY;
    public static Supplier<Feature<NoneFeatureConfiguration>> SMALL_LIBRARY, CRYSTAL_FORMATION;
    public static Supplier<LootItemFunctionType> SYMBOL_PAGE_FUNCTION, TREASURE_FUNCTION;
    public static Supplier<net.minecraft.world.inventory.MenuType<net.mystcraft.minecraft.exploration.ArchivistMenu>> ARCHIVIST_MENU;
    private ExplorationRegistry() {}
    public static void registerBlocks(RegistryBridge bridge) { LECTERN = bridge.registerBlock("lectern", MystLecternBlock::new); }
    public static void register(RegistryBridge bridge) {
        LECTERN_ITEM = bridge.registerItem("lectern", () -> bridge.createLecternItem(LECTERN.get(), new Item.Properties().tab(MystcraftItems.TAB)));
        LECTERN_ENTITY = bridge.registerBlockEntityType("lectern", () -> BlockEntityType.Builder.of(MystLecternBlockEntity::new, LECTERN.get()).build(null));
        SMALL_LIBRARY = bridge.registerFeature("small_library", ClassicLibraryFeature::new);
        CRYSTAL_FORMATION = bridge.registerFeature("crystal_formation", ClassicCrystalFeature::new);
        ARCHIVIST_POI = bridge.registerPoi("archivist", () -> new net.minecraft.world.entity.ai.village.poi.PoiType(
                java.util.Set.copyOf(LECTERN.get().getStateDefinition().getPossibleStates()), 1, 1));
        ARCHIVIST = bridge.registerProfession("archivist", () -> new net.minecraft.world.entity.npc.VillagerProfession(
                "archivist", holder -> holder.value() == ARCHIVIST_POI.get(), holder -> holder.value() == ARCHIVIST_POI.get(),
                com.google.common.collect.ImmutableSet.of(), com.google.common.collect.ImmutableSet.of(), net.minecraft.sounds.SoundEvents.VILLAGER_WORK_LIBRARIAN));
        ARCHIVIST_MENU = bridge.registerMenu("archivist", net.mystcraft.minecraft.exploration.ArchivistMenu::new);
        TREASURE_FUNCTION = bridge.registerLootFunction("treasure", () -> new LootItemFunctionType(new net.mystcraft.minecraft.exploration.ClassicTreasureLootFunction.Serializer()));
        SYMBOL_PAGE_FUNCTION = bridge.registerLootFunction("symbol_page", () -> new LootItemFunctionType(new SymbolPageLootFunction.Serializer()));
    }
}
