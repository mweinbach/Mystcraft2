package ports.jurassicraft.content;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.skull.SkullItem;

/** Base material, fossil and bone registrations shared by the Fabric systems. */
public final class JurassicContent {
    public static final CreativeModeTab TAB = FabricItemGroupBuilder.build(
            new ResourceLocation(PortMod.MOD_ID, "jurassicraft"), () -> new ItemStack(JurassicContent.AMBER.get()));

    public static final Supplier<Item> AMBER = item("amber");
    public static final Supplier<Item> AMBER_APHID = item("amber_aphid");
    public static final Supplier<Item> GYPSUM_POWDER = item("gypsum_powder");
    public static final Supplier<Item> SEA_LAMPREY = item("sea_lamprey");
    public static final Supplier<Item> PLANT_FOSSIL = item("plant_fossil");
    public static final Supplier<Item> TWIG_FOSSIL = item("twig_fossil");
    public static final Supplier<Item> PLASTER_AND_BANDAGE = register(Registry.ITEM, "plaster_and_bandage",
            () -> new PlasterItem(new Item.Properties().tab(TAB)));

    public static final Supplier<Block> AMBER_ORE = protectedStone("amber_ore", 3.0F, 5.0F);
    public static final Supplier<Block> ICE_SHARD = protectedStone("ice_shard", 3.0F, 5.0F);
    public static final Supplier<Block> GYPSUM_STONE = stone("gypsum_stone", 1.5F, 1.5F);
    public static final Supplier<Block> GYPSUM_COBBLESTONE = stone("gypsum_cobblestone", 1.5F, 1.5F);
    public static final Supplier<Block> GYPSUM_BRICKS = stone("gypsum_bricks", 2.0F, 2.0F);
    public static final Supplier<Block> REINFORCED_STONE = stone("reinforced_stone", 2.0F, 2.0F);
    public static final Supplier<Block> REINFORCED_BRICKS = stone("reinforced_bricks", 3.0F, 3.0F);
    public static final Supplier<Block> PLANT_FOSSIL_BLOCK = protectedStone("plant_fossil_block", 2.0F, 8.0F);

    public static final Map<Species, Supplier<Block>> FOSSILS = new EnumMap<>(Species.class);
    public static final Map<Species, Supplier<Block>> ENCASED_FOSSILS = new EnumMap<>(Species.class);
    public static final Map<Species, Map<String, Supplier<Item>>> BONES = new EnumMap<>(Species.class);

    static {
        for (Species species : Species.values()) {
            String fossilName = species.id() + "_fossil_block";
            Supplier<Block> fossil = register(Registry.BLOCK, fossilName, () -> new FossilBlock(species));
            register(Registry.ITEM, fossilName, () -> new BlockItem(fossil.get(), new Item.Properties().tab(TAB)));
            FOSSILS.put(species, fossil);
            ENCASED_FOSSILS.put(species, protectedStone(species.id() + "_encased_fossil", 2.0F, 8.0F));

            Map<String, Supplier<Item>> bones = new LinkedHashMap<>();
            for (String bone : species.bones()) {
                String name = species.id() + "_fossil_" + bone;
                bones.put(bone, species == Species.TYRANNOSAURUS && bone.equals("skull")
                        ? register(Registry.ITEM, name, () -> new SkullItem(new Item.Properties().tab(TAB))) : item(name));
            }
            BONES.put(species, Map.copyOf(bones));
        }
    }

    private JurassicContent() {}
    public static void init() { /* Force class initialization before dependent systems. */ }

    private static <T> Supplier<T> register(Registry<? super T> registry, String name, Supplier<T> create) {
        T value = Registry.register(registry, new ResourceLocation(PortMod.MOD_ID, name), create.get());
        return () -> value;
    }

    private static Supplier<Item> item(String name) {
        return register(Registry.ITEM, name, () -> new Item(new Item.Properties().tab(TAB)));
    }

    private static Supplier<Block> stone(String name, float hardness, float resistance) {
        Supplier<Block> block = register(Registry.BLOCK, name, () -> new Block(
                BlockBehaviour.Properties.of(Material.STONE).strength(hardness, resistance)
                        .sound(SoundType.STONE).requiresCorrectToolForDrops()));
        register(Registry.ITEM, name, () -> new BlockItem(block.get(), new Item.Properties().tab(TAB)));
        return block;
    }

    private static Supplier<Block> protectedStone(String name, float hardness, float resistance) {
        Supplier<Block> block = register(Registry.BLOCK, name, () -> new ExplosionProtectedBlock(
                BlockBehaviour.Properties.of(Material.STONE).strength(hardness, resistance)
                        .sound(SoundType.STONE).requiresCorrectToolForDrops()));
        register(Registry.ITEM, name, () -> new BlockItem(block.get(), new Item.Properties().tab(TAB)));
        return block;
    }
}
