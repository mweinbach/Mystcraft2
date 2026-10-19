package ports.jurassicraft.fossil;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Datapack loot entry for the original fossil and mineral Fortune rolls. */
public final class FossilLootEntry extends LootPoolSingletonContainer {
    private static final DeferredRegister<LootPoolEntryType> TYPES =
            DeferredRegister.create(Registry.LOOT_POOL_ENTRY_TYPE.key(), PortMod.MOD_ID);
    private static final RegistryObject<LootPoolEntryType> TYPE =
            TYPES.register("fossil_drops", () -> new LootPoolEntryType(new Serializer()));

    private enum Kind { PLANT, AMBER, GYPSUM, ICE }
    private final Kind kind;

    private FossilLootEntry(Kind kind, int weight, int quality, LootItemCondition[] conditions, LootItemFunction[] functions) {
        super(weight, quality, conditions, functions);
        this.kind = kind;
    }

    public static void register(IEventBus bus) { TYPES.register(bus); }
    @Override public LootPoolEntryType getType() { return TYPE.get(); }

    /** The cleaning station uses the same upstream roll with luck fixed at two. */
    public static Item choosePlant(RandomSource random, int luck) {
        double roll = random.nextDouble() - 0.3D * Math.max(0, luck) * random.nextDouble();
        return roll < 0.20D ? JurassicContent.PLANT_FOSSIL.get()
                : roll < 0.35D ? JurassicContent.TWIG_FOSSIL.get()
                : roll < 0.65D ? Items.COAL : roll < 0.80D ? Items.FLINT : Items.COBBLESTONE;
    }

    @Override protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        int fortune = tool == null ? 0 : Math.max(0, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool));
        RandomSource random = context.getRandom();
        switch (kind) {
            case PLANT -> output.accept(new ItemStack(choosePlant(random, fortune)));
            case AMBER -> {
                int count = Math.max(1, random.nextInt(fortune + 1));
                for (int i = 0; i < count; i++)
                    output.accept(new ItemStack(random.nextBoolean() ? JurassicContent.AMBER_APHID.get() : JurassicContent.AMBER.get()));
            }
            case GYPSUM -> {
                int multiplier = fortune == 0 ? 1 : Math.max(0, random.nextInt(fortune + 2) - 2) + 1;
                output.accept(new ItemStack(JurassicContent.GYPSUM_POWDER.get(), (random.nextInt(5) + 4) * multiplier));
            }
            case ICE -> {
                if (random.nextDouble() < 0.2D + fortune / 3.0D * 0.8D)
                    output.accept(new ItemStack(JurassicContent.SEA_LAMPREY.get()));
                else if (random.nextDouble() < 0.2D) output.accept(new ItemStack(Items.ICE));
            }
        }
    }

    private static final class Serializer extends LootPoolSingletonContainer.Serializer<FossilLootEntry> {
        @Override public void serializeCustom(JsonObject json, FossilLootEntry entry, JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            json.addProperty("kind", entry.kind.name().toLowerCase(Locale.ROOT));
        }

        @Override protected FossilLootEntry deserialize(JsonObject json, JsonDeserializationContext context, int weight,
                int quality, LootItemCondition[] conditions, LootItemFunction[] functions) {
            String name = GsonHelper.getAsString(json, "kind");
            try {
                return new FossilLootEntry(Kind.valueOf(name.toUpperCase(Locale.ROOT)), weight, quality, conditions, functions);
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException("Unknown fossil drop kind: " + name, ex);
            }
        }
    }
}
