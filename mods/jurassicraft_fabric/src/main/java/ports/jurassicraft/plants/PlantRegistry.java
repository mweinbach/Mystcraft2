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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Prehistoric vegetation and wood, including crops and aquatic plants. */
public final class PlantRegistry {
    private static final Register<Block> BLOCKS = new Register<>(Registry.BLOCK);
    private static final Register<Feature<?>> FEATURES = new Register<>(Registry.FEATURE);
    public static final Entry<Feature<?>> LAND_PLANT_FEATURE = FEATURES.register("land_plant", LandPlantFeature::new);
    public static final Entry<Feature<?>> PEAT_FEATURE = FEATURES.register("peat", PeatFeature::new);
    public static final Entry<Feature<?>> GRACILARIA_FEATURE = FEATURES.register("gracilaria", GracilariaFeature::new);
    public static final Entry<Feature<?>> PETRIFIED_TREE_FEATURE = FEATURES.register("petrified_tree", PetrifiedTreeFeature::new);
    public static final Entry<Feature<?>> COAL_FOSSIL_FEATURE = FEATURES.register("coal_plant_fossils", CoalPlantFossilsFeature::new);
    private static final Register<Item> ITEMS = new Register<>(Registry.ITEM);
    public static final Entry<Item> PHOENIX_SEEDS = ITEMS.register("phoenix_seeds",
            () -> new PhoenixSeedItem(plant(PlantSpecies.PHOENIX).get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Entry<Item> PHOENIX_FRUIT = ITEMS.register("phoenix_fruit",
            () -> new PhoenixFruitItem(new Item.Properties().tab(JurassicContent.TAB)
                    .food(new FoodProperties.Builder().nutrition(4).saturationMod(0.4F).build())));
    public static final Entry<Block> GRACILARIA_SEAWEED = BLOCKS.register("gracilaria_seaweed",
            () -> new AncientAquaticPlantBlock(BlockBehaviour.Properties.copy(Blocks.SEAGRASS).randomTicks()));
    public static final Entry<Item> GRACILARIA = ITEMS.register("gracilaria",
            () -> new BlockItem(GRACILARIA_SEAWEED.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Entry<Item> AJUGINUCULA_SEEDS = seed("ajuginucula_smithii_seeds", PlantSpecies.AJUGINUCULA_SMITHII, null);
    public static final Entry<Item> AJUGINUCULA_OIL = ITEMS.register("ajuginucula_smithii_oil",
            () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    public static final Entry<Item> OILED_POTATO_STRIPS = food("oiled_potato_strips", 1, 0.0F);
    public static final Entry<Item> FUN_FRIES = food("fun_fries", 4, 2.0F);
    public static final Entry<Item> CHILEAN_SEA_BASS = food("chilean_sea_bass", 10, 1.0F);
    public static final Entry<Item> LILAC_BERRIES = ITEMS.register("west_indian_lilac_berries", () -> new Item(new Item.Properties().tab(JurassicContent.TAB).food(
            new FoodProperties.Builder().nutrition(1).saturationMod(0.1F)
                    .effect(new MobEffectInstance(MobEffects.POISON, 1400, 1), 1.0F).build())));
    public static final Entry<Item> AJUGINUCULA_LEAVES = food("ajuginucula_smithii_leaves", 1, 0.5F);
    public static final Entry<Item> WILD_ONION = seed("wild_onion", PlantSpecies.WILD_ONION, new FoodProperties.Builder().nutrition(3).saturationMod(0.3F).build());
    public static final Entry<Item> WILD_POTATO_SEEDS = seed("wild_potato_seeds", PlantSpecies.WILD_POTATO, null);
    public static final Entry<Item> WILD_POTATO = food("wild_potato", 1, 0.1F);
    public static final Entry<Item> WILD_POTATO_COOKED = food("wild_potato_cooked", 6, 0.6F);
    public static final Entry<Item> RHAMNUS_SEEDS = seed("rhamnus_salicifolius_seeds", PlantSpecies.RHAMNUS_SALIFOCIFUS, null);
    public static final Entry<Item> RHAMNUS_BERRIES = ITEMS.register("rhamnus_salicifolius_berries", () -> new Item(new Item.Properties().tab(JurassicContent.TAB).food(
            new FoodProperties.Builder().nutrition(5).saturationMod(0.6F)
                    .effect(new MobEffectInstance(MobEffects.POISON, 1400, 1), 1.0F).build())));
    public static final Entry<Block> PALEO_BALE_CYCADEOIDEA = bale("paleo_bale_cycadeoidea");
    public static final Entry<Block> PALEO_BALE_CYCAD = bale("paleo_bale_cycad");
    public static final Entry<Block> PALEO_BALE_FERN = bale("paleo_bale_fern");
    public static final Entry<Block> PALEO_BALE_LEAVES = bale("paleo_bale_leaves");
    public static final Entry<Block> PALEO_BALE_OTHER = bale("paleo_bale_other");
    public static final Entry<Block> PEAT = block("peat", PeatBlock::new);
    public static final Entry<Block> MOSS = block("moss", MossBlock::new);
    private static final Map<PlantSpecies, Entry<Block>> PLANTS = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, Entry<Block>> LOGS = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, Entry<Block>> PETRIFIED_LOGS = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, Entry<Block>> LEAVES = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, Entry<Block>> PLANKS = new EnumMap<>(PlantSpecies.class);

    static {
        for (PlantSpecies species : PlantSpecies.values()) {
            if (species.isTree()) {
                String id = species.id();
                Entry<Block> log = block(id + "_log", () -> new WoodBlocks.Log(species, BlockBehaviour.Properties.copy(Blocks.OAK_LOG)));
                Entry<Block> petrified = block(id + "_log_petrified", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of(Material.STONE).strength(4.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
                Entry<Block> leaves = block(id + "_leaves", () -> new WoodBlocks.Leaves(species, BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)));
                Entry<Block> planks = block(id + "_planks", () -> new WoodBlocks.Planks(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
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

    /** Simple deferred holder: registration itself still uses Minecraft's native Registry. */
    public static final class Entry<T> implements Supplier<T> {
        private final String id;
        private final Supplier<? extends T> factory;
        private T value;
        private Entry(String id, Supplier<? extends T> factory) { this.id = id; this.factory = factory; }
        public boolean isPresent() { return value != null; }
        @Override public T get() {
            if (value == null) throw new IllegalStateException("PlantRegistry not initialized: " + id);
            return value;
        }
    }
    private static final class Register<T> {
        private final Registry<T> registry;
        private final List<Entry<T>> entries = new ArrayList<>();
        Register(Registry<T> registry) { this.registry = registry; }
        Entry<T> register(String id, Supplier<? extends T> factory) {
            Entry<T> entry = new Entry<>(id, factory);
            entries.add(entry);
            return entry;
        }
        void init() {
            for (Entry<T> entry : entries) {
                entry.value = Registry.register(registry, new ResourceLocation(PortMod.MOD_ID, entry.id), entry.factory.get());
            }
        }
    }
    private PlantRegistry() {}
    /** Call once from the Fabric common entrypoint after JurassicContent is initialized. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        PlantGameRules.init();
        BLOCKS.init();
        ITEMS.init();
        FEATURES.init();
        FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
        for (PlantSpecies species : PlantSpecies.values()) if (species.isTree()) {
            fire.add(log(species).get(), 5, 5);
            fire.add(leaves(species).get(), 60, 30);
            for (String suffix : new String[]{"planks", "stairs", "slab", "fence", "fence_gate"}) {
                fire.add(Registry.BLOCK.get(new ResourceLocation(PortMod.MOD_ID, species.id() + "_" + suffix)), 20, 5);
            }
        }
        PlantWorldgen.init();
    }
    /** The original peat spread distance defaults to 20; a common config may override it. */
    public static void setPeatSpreadDistance(java.util.function.IntSupplier value) { PeatBlock.setSpreadDistance(value); }
    private static boolean initialized;
    /** Gets the plant or sapling registered for a species. */
    public static Entry<Block> plant(PlantSpecies species) { return PLANTS.get(species); }
    public static Entry<Block> log(PlantSpecies species) { return LOGS.get(species); }
    public static Entry<Block> petrifiedLog(PlantSpecies species) { return PETRIFIED_LOGS.get(species); }
    public static Entry<Block> leaves(PlantSpecies species) { return LEAVES.get(species); }
    public static Entry<Block> planks(PlantSpecies species) { return PLANKS.get(species); }

    private static Entry<Item> seed(String id, PlantSpecies plant, FoodProperties food) {
        return ITEMS.register(id, () -> {
            Item.Properties props = new Item.Properties().tab(JurassicContent.TAB);
            if (food != null) props.food(food);
            return new CropSeedItem(plant(plant).get(), props, id);
        });
    }
    private static Entry<Item> food(String id, int nutrition, float saturation) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().tab(JurassicContent.TAB)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation).build())));
    }
    private static void crop(PlantSpecies species, String name, int maxAge, Supplier<Item> seed) {
        PLANTS.put(species, BLOCKS.register(name, () -> new AncientCropBlock(maxAge, seed)));
    }
    private static Entry<Block> bale(String id) {
        return block(id, () -> new HayBlock(BlockBehaviour.Properties.copy(Blocks.HAY_BLOCK)));
    }
    private static Entry<Block> block(String id, Supplier<? extends Block> factory) {
        Entry<Block> block = BLOCKS.register(id, factory);
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
