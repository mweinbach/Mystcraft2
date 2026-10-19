package ports.jurassicraft.content;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.skull.SkullItem;
import ports.jurassicraft.PortMod;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** The first registered content from the 1.12.2 JurassiCraft resource chain. */
public final class JurassicContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);

    public static final CreativeModeTab TAB = new CreativeModeTab(PortMod.MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(AMBER.get());
        }
    };

    public static final RegistryObject<Item> AMBER = item("amber");
    public static final RegistryObject<Item> AMBER_APHID = item("amber_aphid");
    public static final RegistryObject<Item> GYPSUM_POWDER = item("gypsum_powder");
    public static final RegistryObject<Item> SEA_LAMPREY = item("sea_lamprey");
    public static final RegistryObject<Item> PLANT_FOSSIL = item("plant_fossil");
    public static final RegistryObject<Item> TWIG_FOSSIL = item("twig_fossil");
    public static final RegistryObject<Item> PLASTER_AND_BANDAGE = ITEMS.register("plaster_and_bandage",
            () -> new PlasterItem(new Item.Properties().tab(TAB)));

    public static final RegistryObject<Block> AMBER_ORE = protectedStone("amber_ore", 3.0F, 5.0F);
    public static final RegistryObject<Block> ICE_SHARD = protectedStone("ice_shard", 3.0F, 5.0F);
    public static final RegistryObject<Block> GYPSUM_STONE = stone("gypsum_stone", 1.5F, 1.5F);
    public static final RegistryObject<Block> GYPSUM_COBBLESTONE = stone("gypsum_cobblestone", 1.5F, 1.5F);
    public static final RegistryObject<Block> GYPSUM_BRICKS = stone("gypsum_bricks", 2.0F, 2.0F);
    public static final RegistryObject<Block> REINFORCED_STONE = stone("reinforced_stone", 2.0F, 2.0F);
    public static final RegistryObject<Block> REINFORCED_BRICKS = stone("reinforced_bricks", 3.0F, 3.0F);
    public static final RegistryObject<Block> PLANT_FOSSIL_BLOCK = protectedStone("plant_fossil_block", 2.0F, 8.0F);

    public static final Map<Species, RegistryObject<Block>> FOSSILS = new EnumMap<>(Species.class);
    public static final Map<Species, RegistryObject<Block>> ENCASED_FOSSILS = new EnumMap<>(Species.class);
    public static final Map<Species, Map<String, RegistryObject<Item>>> BONES = new EnumMap<>(Species.class);

    static {
        for (Species species : Species.values()) {
            String fossilName = species.id() + "_fossil_block";
            RegistryObject<Block> fossil = BLOCKS.register(fossilName, () -> new FossilBlock(species));
            ITEMS.register(fossilName, () -> new BlockItem(fossil.get(), new Item.Properties().tab(TAB)));
            FOSSILS.put(species, fossil);
            ENCASED_FOSSILS.put(species, protectedStone(species.id() + "_encased_fossil", 2.0F, 8.0F));

            Map<String, RegistryObject<Item>> bones = new LinkedHashMap<>();
            for (String bone : species.bones()) {
                String name = species.id() + "_fossil_" + bone;
                bones.put(bone, species == Species.TYRANNOSAURUS && bone.equals("skull")
                        ? ITEMS.register(name, () -> new SkullItem(new Item.Properties().tab(TAB))) : item(name));
            }
            BONES.put(species, Map.copyOf(bones));
        }
    }

    private JurassicContent() {}

    private static RegistryObject<Item> item(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().tab(TAB)));
    }

    private static RegistryObject<Block> stone(String name, float hardness, float resistance) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new Block(
                BlockBehaviour.Properties.of(Material.STONE).strength(hardness, resistance)
                        .sound(SoundType.STONE).requiresCorrectToolForDrops()));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(TAB)));
        return block;
    }

    private static RegistryObject<Block> protectedStone(String name, float hardness, float resistance) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new ExplosionProtectedBlock(
                BlockBehaviour.Properties.of(Material.STONE).strength(hardness, resistance)
                        .sound(SoundType.STONE).requiresCorrectToolForDrops()));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(TAB)));
        return block;
    }
}
