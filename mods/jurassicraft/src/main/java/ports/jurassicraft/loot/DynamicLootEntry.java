package ports.jurassicraft.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Species and variants that were selected by metadata functions in 1.12.2. */
public final class DynamicLootEntry extends LootPoolSingletonContainer {
    private static final DeferredRegister<LootPoolEntryType> TYPES =
            DeferredRegister.create(Registry.LOOT_POOL_ENTRY_TYPE.key(), PortMod.MOD_ID);
    private static final RegistryObject<LootPoolEntryType> TYPE =
            TYPES.register("dynamic_bonus", () -> new LootPoolEntryType(new Serializer()));
    private static final List<Species> DINOSAURS = List.of(Species.values());
    private static final List<PlantSpecies> PLANTS = Arrays.stream(PlantSpecies.values())
            .filter(PlantSpecies::isPrehistoric).toList();
    private static final List<Item> WOOLS = List.of(Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL,
            Items.LIGHT_BLUE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL,
            Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL, Items.BROWN_WOOL,
            Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL);

    enum Kind { AMBER, WOOL, BONE, ANIMAL_TISSUE, PLANT_TISSUE, DINOSAUR_DNA, ACTION_FIGURE }
    private final Kind kind;

    private DynamicLootEntry(Kind kind, int weight, int quality, LootItemCondition[] conditions,
            LootItemFunction[] functions) {
        super(weight, quality, conditions, functions);
        this.kind = kind;
    }

    public static void register(IEventBus bus) { TYPES.register(bus); }
    @Override public LootPoolEntryType getType() { return TYPE.get(); }

    static Builder<?> entry(Kind kind) {
        return simpleBuilder((weight, quality, conditions, functions) ->
                new DynamicLootEntry(kind, weight, quality, conditions, functions));
    }

    @Override protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        RandomSource random = context.getRandom();
        ItemStack stack;
        switch (kind) {
            case AMBER -> stack = new ItemStack(random.nextBoolean() ? JurassicContent.AMBER.get() : JurassicContent.AMBER_APHID.get());
            case WOOL -> stack = new ItemStack(WOOLS.get(random.nextInt(WOOLS.size())));
            case BONE -> {
                Species species = DINOSAURS.get(random.nextInt(DINOSAURS.size()));
                String bone = species.bones().get(random.nextInt(species.bones().size()));
                stack = new ItemStack(JurassicContent.BONES.get(species).get(bone).get());
            }
            case ANIMAL_TISSUE -> {
                Species species = DINOSAURS.get(random.nextInt(DINOSAURS.size()));
                stack = new ItemStack(GeneticsRegistry.softTissue(species).get());
            }
            case PLANT_TISSUE -> {
                PlantSpecies species = PLANTS.get(random.nextInt(PLANTS.size()));
                stack = new ItemStack(GeneticsRegistry.plantTissue(species).get());
            }
            case DINOSAUR_DNA -> {
                Species species = DINOSAURS.get(random.nextInt(DINOSAURS.size()));
                stack = new ItemStack(GeneticsRegistry.dinosaurDna(species).get());
                Genetics.write(stack.getOrCreateTag(), DNAData.dinosaur(species,
                        (random.nextInt(10) + 1) * 5, Genetics.randomGenetics(random)));
            }
            case ACTION_FIGURE -> stack = BonusLoot.actionFigure(DINOSAURS.get(random.nextInt(DINOSAURS.size())));
            default -> throw new IllegalStateException("Unhandled loot kind " + kind);
        }
        if (!stack.isEmpty()) output.accept(stack);
    }

    private static final class Serializer extends LootPoolSingletonContainer.Serializer<DynamicLootEntry> {
        @Override public void serializeCustom(JsonObject json, DynamicLootEntry entry, JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            json.addProperty("kind", entry.kind.name().toLowerCase(Locale.ROOT));
        }

        @Override protected DynamicLootEntry deserialize(JsonObject json, JsonDeserializationContext context, int weight,
                int quality, LootItemCondition[] conditions, LootItemFunction[] functions) {
            String name = GsonHelper.getAsString(json, "kind");
            try {
                return new DynamicLootEntry(Kind.valueOf(name.toUpperCase(Locale.ROOT)), weight, quality, conditions, functions);
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException("Unknown dynamic loot kind: " + name, exception);
            }
        }
    }
}
