package ports.jurassicraft.plants;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Prehistoric vegetation and wood, including crops and aquatic plants. */
public final class PlantRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, PortMod.MOD_ID);
    public static final RegistryObject<Feature<?>> LAND_PLANT_FEATURE = FEATURES.register("land_plant", LandPlantFeature::new);
    public static final RegistryObject<Feature<?>> PEAT_FEATURE = FEATURES.register("peat", PeatFeature::new);
    public static final RegistryObject<Feature<?>> GRACILARIA_FEATURE = FEATURES.register("gracilaria", GracilariaFeature::new);
    public static final RegistryObject<Feature<?>> PETRIFIED_TREE_FEATURE = FEATURES.register("petrified_tree", PetrifiedTreeFeature::new);
    public static final RegistryObject<Feature<?>> COAL_FOSSIL_FEATURE = FEATURES.register("coal_plant_fossils", CoalPlantFossilsFeature::new);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    public static final RegistryObject<Item> PHOENIX_SEEDS = ITEMS.register("phoenix_seeds",
            () -> new PhoenixSeedItem(plant(PlantSpecies.PHOENIX).get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> PHOENIX_FRUIT = ITEMS.register("phoenix_fruit",
            () -> new PhoenixFruitItem(new Item.Properties().tab(JurassicContent.TAB)
                    .food(new FoodProperties.Builder().nutrition(4).saturationMod(0.4F).build())));
    public static final RegistryObject<Block> GRACILARIA_SEAWEED = BLOCKS.register("gracilaria_seaweed",
            () -> new AncientAquaticPlantBlock(BlockBehaviour.Properties.copy(Blocks.SEAGRASS).randomTicks()));
    public static final RegistryObject<Item> GRACILARIA = ITEMS.register("gracilaria",
            () -> new BlockItem(GRACILARIA_SEAWEED.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> AJUGINUCULA_SEEDS = seed("ajuginucula_smithii_seeds", PlantSpecies.AJUGINUCULA_SMITHII, null);
    public static final RegistryObject<Item> AJUGINUCULA_OIL = ITEMS.register("ajuginucula_smithii_oil",
            () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> OILED_POTATO_STRIPS = food("oiled_potato_strips", 1, 0.0F);
    public static final RegistryObject<Item> FUN_FRIES = food("fun_fries", 4, 2.0F);
    public static final RegistryObject<Item> CHILEAN_SEA_BASS = food("chilean_sea_bass", 10, 1.0F);
    public static final RegistryObject<Item> LILAC_BERRIES = ITEMS.register("west_indian_lilac_berries", () -> new Item(new Item.Properties().tab(JurassicContent.TAB).food(
            new FoodProperties.Builder().nutrition(1).saturationMod(0.1F)
                    .effect(() -> new MobEffectInstance(MobEffects.POISON, 1400, 1), 1.0F).build())));
    public static final RegistryObject<Item> AJUGINUCULA_LEAVES = food("ajuginucula_smithii_leaves", 1, 0.5F);
    public static final RegistryObject<Item> WILD_ONION = seed("wild_onion", PlantSpecies.WILD_ONION, new FoodProperties.Builder().nutrition(3).saturationMod(0.3F).build());
    public static final RegistryObject<Item> WILD_POTATO_SEEDS = seed("wild_potato_seeds", PlantSpecies.WILD_POTATO, null);
    public static final RegistryObject<Item> WILD_POTATO = food("wild_potato", 1, 0.1F);
    public static final RegistryObject<Item> WILD_POTATO_COOKED = food("wild_potato_cooked", 6, 0.6F);
    public static final RegistryObject<Item> RHAMNUS_SEEDS = seed("rhamnus_salicifolius_seeds", PlantSpecies.RHAMNUS_SALIFOCIFUS, null);
    public static final RegistryObject<Item> RHAMNUS_BERRIES = ITEMS.register("rhamnus_salicifolius_berries", () -> new Item(new Item.Properties().tab(JurassicContent.TAB).food(
            new FoodProperties.Builder().nutrition(5).saturationMod(0.6F)
                    .effect(() -> new MobEffectInstance(MobEffects.POISON, 1400, 1), 1.0F).build())));
    public static final RegistryObject<Block> PALEO_BALE_CYCADEOIDEA = bale("paleo_bale_cycadeoidea");
    public static final RegistryObject<Block> PALEO_BALE_CYCAD = bale("paleo_bale_cycad");
    public static final RegistryObject<Block> PALEO_BALE_FERN = bale("paleo_bale_fern");
    public static final RegistryObject<Block> PALEO_BALE_LEAVES = bale("paleo_bale_leaves");
    public static final RegistryObject<Block> PALEO_BALE_OTHER = bale("paleo_bale_other");
    public static final RegistryObject<Block> PEAT = block("peat", PeatBlock::new);
    public static final RegistryObject<Block> MOSS = block("moss", MossBlock::new);
    private static final Map<PlantSpecies, RegistryObject<Block>> PLANTS = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, RegistryObject<Block>> LOGS = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, RegistryObject<Block>> PETRIFIED_LOGS = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, RegistryObject<Block>> LEAVES = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, RegistryObject<Block>> PLANKS = new EnumMap<>(PlantSpecies.class);

    static {
        for (PlantSpecies species : PlantSpecies.values()) {
            if (species.isTree()) {
                String id = species.id();
                RegistryObject<Block> log = block(id + "_log", () -> new WoodBlocks.Log(species, BlockBehaviour.Properties.copy(Blocks.OAK_LOG)));
                RegistryObject<Block> petrified = block(id + "_log_petrified", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of(Material.STONE).strength(4.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
                RegistryObject<Block> leaves = block(id + "_leaves", () -> new WoodBlocks.Leaves(species, BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)));
                RegistryObject<Block> planks = block(id + "_planks", () -> new WoodBlocks.Planks(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
                LOGS.put(species, log);
                PETRIFIED_LOGS.put(species, petrified);
                LEAVES.put(species, leaves);
                PLANKS.put(species, planks);
                PLANTS.put(species, block(id + "_sapling", () -> new AncientSaplingBlock(species)));
                block(id + "_stairs", () -> new WoodBlocks.Stair(() -> planks.get().defaultBlockState(), BlockBehaviour.Properties.copy(Blocks.OAK_STAIRS)));
                block(id + "_slab", () -> new WoodBlocks.Slab(BlockBehaviour.Properties.copy(Blocks.OAK_SLAB)));
                block(id + "_fence", () -> new WoodBlocks.Fence(BlockBehaviour.Properties.copy(Blocks.OAK_FENCE)));
                block(id + "_fence_gate", () -> new WoodBlocks.Gate(BlockBehaviour.Properties.copy(Blocks.OAK_FENCE_GATE)));
                block(id + "_door", () -> new DoorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_DOOR)));
            }
        }
        crop(PlantSpecies.AJUGINUCULA_SMITHII, "ajuginucula_smithii", 7, () -> AJUGINUCULA_SEEDS.get());
        crop(PlantSpecies.WILD_ONION, "wild_onion_plant", 6, () -> WILD_ONION.get());
        crop(PlantSpecies.WILD_POTATO, "wild_potato_plant", 5, () -> WILD_POTATO_SEEDS.get());
        crop(PlantSpecies.RHAMNUS_SALIFOCIFUS, "rhamnus_salicifolius", 7, () -> RHAMNUS_SEEDS.get());
        single(PlantSpecies.SMALL_ROYAL_FERN, PlantSpecies.SMALL_CHAIN_FERN, PlantSpecies.SMALL_CYCAD,
                PlantSpecies.BENNETTITALEAN_CYCADEOIDEA, PlantSpecies.CRY_PANSY, PlantSpecies.DICTYOPHYLLUM,
                PlantSpecies.LADINIA_SIMPLEX, PlantSpecies.ORONTIUM_MACKII, PlantSpecies.RAPHAELIA,
                PlantSpecies.CINNAMON_FERN, PlantSpecies.BRISTLE_FERN, PlantSpecies.WOOLLY_STALKED_BEGONIA,
                PlantSpecies.LARGESTIPULE_LEATHER_ROOT);
        aquatic(PlantSpecies.ENALLHELIA, PlantSpecies.AULOPORA, PlantSpecies.CLADOCHONUS,
                PlantSpecies.LITHOSTROTION, PlantSpecies.STYLOPHYLLOPSIS, PlantSpecies.HIPPURITES_RADIOSUS);
        PLANTS.put(PlantSpecies.WEST_INDIAN_LILAC, block("west_indian_lilac", () -> new WestIndianLilacBlock(plantProperties())));
        tall(PlantSpecies.SCALY_TREE_FERN, PlantSpecies.CYCAD_ZAMITES, PlantSpecies.DICKSONIA,
                PlantSpecies.DICROIDIUM_ZUBERI, PlantSpecies.SERENNA_VERIFORMANS,
                PlantSpecies.UMALTOLEPIS, PlantSpecies.LIRIODENDRITES, PlantSpecies.ENCEPHALARTOS,
                PlantSpecies.TEMPSKYA, PlantSpecies.RHACOPHYTON, PlantSpecies.GRAMINIDITES_BAMBUSOIDES,
                PlantSpecies.HELICONIA);
    }

    private PlantRegistry() {}
    public static void register(IEventBus bus) { PlantGameRules.init(); BLOCKS.register(bus); ITEMS.register(bus); FEATURES.register(bus); }
    /** Gets the plant or sapling registered for a species. */
    public static RegistryObject<Block> plant(PlantSpecies species) { return PLANTS.get(species); }
    public static RegistryObject<Block> log(PlantSpecies species) { return LOGS.get(species); }
    public static RegistryObject<Block> petrifiedLog(PlantSpecies species) { return PETRIFIED_LOGS.get(species); }
    public static RegistryObject<Block> leaves(PlantSpecies species) { return LEAVES.get(species); }
    public static RegistryObject<Block> planks(PlantSpecies species) { return PLANKS.get(species); }

    private static RegistryObject<Item> seed(String id, PlantSpecies plant, FoodProperties food) {
        return ITEMS.register(id, () -> {
            Item.Properties props = new Item.Properties().tab(JurassicContent.TAB);
            if (food != null) props.food(food);
            return new CropSeedItem(plant(plant).get(), props, id);
        });
    }
    private static RegistryObject<Item> food(String id, int nutrition, float saturation) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().tab(JurassicContent.TAB)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation).build())));
    }
    private static void crop(PlantSpecies species, String name, int maxAge, Supplier<Item> seed) {
        PLANTS.put(species, BLOCKS.register(name, () -> new AncientCropBlock(maxAge, seed)));
    }
    private static RegistryObject<Block> bale(String id) {
        return block(id, () -> new HayBlock(BlockBehaviour.Properties.copy(Blocks.HAY_BLOCK)));
    }
    private static RegistryObject<Block> block(String id, Supplier<? extends Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(id, factory);
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
    private static BlockBehaviour.Properties plantProperties() {
        return BlockBehaviour.Properties.of(Material.PLANT).noCollission().instabreak().sound(SoundType.GRASS).randomTicks();
    }
    private static void single(PlantSpecies... species) {
        for (PlantSpecies plant : species) PLANTS.put(plant, block(plant.id(), () -> new AncientPlantBlock(plantProperties())));
    }
    private static void aquatic(PlantSpecies... species) {
        for (PlantSpecies plant : species) PLANTS.put(plant, block(plant.id(),
                () -> new AncientCoralBlock(BlockBehaviour.Properties.copy(Blocks.SEAGRASS).randomTicks())));
    }
    private static void tall(PlantSpecies... species) {
        for (PlantSpecies plant : species) PLANTS.put(plant, block(plant.id(), () -> new AncientTallPlantBlock(plantProperties())));
    }
}
