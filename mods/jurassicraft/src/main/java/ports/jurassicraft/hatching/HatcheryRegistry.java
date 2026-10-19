package ports.jurassicraft.hatching;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsItem;

/** Embryonic, calcification and incubation machines and their genetic products. */
public final class HatcheryRegistry {
    public enum Machine { EMBRYONIC("embryonic_machine", 7, 3, 200), CALCIFIER("embryo_calcification_machine", 3, 2, 200), INCUBATOR("incubator", 6, 5, 8000);
        public final String id; public final int slots, inputs, ticks;
        Machine(String id, int slots, int inputs, int ticks) { this.id=id; this.slots=slots; this.inputs=inputs; this.ticks=ticks; }
    }
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, PortMod.MOD_ID);
    private static final Map<Machine, RegistryObject<Block>> MACHINES = new EnumMap<>(Machine.class);
    private static final Map<Machine, RegistryObject<BlockEntityType<HatcheryBlockEntity>>> BLOCK_ENTITIES = new EnumMap<>(Machine.class);
    private static final Map<Machine, RegistryObject<MenuType<HatcheryMenu>>> MENU_TYPES = new EnumMap<>(Machine.class);
    private static final Map<Species, RegistryObject<Item>> SYRINGES = new EnumMap<>(Species.class);
    private static final Map<Species, RegistryObject<Item>> EGGS = new EnumMap<>(Species.class);
    private static final Map<Species, RegistryObject<Item>> HATCHED = new EnumMap<>(Species.class);
    public static final RegistryObject<Block> PEAT_MOSS = BLOCKS.register("peat_moss", () -> new Block(BlockBehaviour.Properties.of(Material.DIRT).strength(.5F).sound(SoundType.GRAVEL)));
    public static final RegistryObject<Item> PEAT_MOSS_ITEM = ITEMS.register("peat_moss", () -> new BlockItem(PEAT_MOSS.get(), new Item.Properties().tab(JurassicContent.TAB)));
    static {
        for (Machine machine : Machine.values()) {
            RegistryObject<Block> block = BLOCKS.register(machine.id, () -> new HatcheryBlock(machine));
            MACHINES.put(machine, block);
            ITEMS.register(machine.id, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
            BLOCK_ENTITIES.put(machine, ENTITIES.register(machine.id, () -> BlockEntityType.Builder.of((p,s) -> new HatcheryBlockEntity(p,s,machine), block.get()).build(null)));
            MENU_TYPES.put(machine, MENUS.register(machine.id, () -> IForgeMenuType.create((id, inv, data) -> new HatcheryMenu(id, inv, data, machine))));
        }
        for (Species species : Species.values()) {
            SYRINGES.put(species, geneticItem(species.id() + "_syringe"));
            if (species != Species.COELACANTH) EGGS.put(species, geneticItem(species.id() + "_egg"));
            String readyId = species.id() + (species == Species.COELACANTH ? "_gestated" : "_hatched_egg");
            HATCHED.put(species, ITEMS.register(readyId, () -> new HatchedEggItem(species)));
        }
    }
    private HatcheryRegistry() {}
    private static RegistryObject<Item> geneticItem(String id) { return ITEMS.register(id, () -> new GeneticsItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1))); }
    public static Block block(Machine machine) { return MACHINES.get(machine).get(); }
    public static BlockEntityType<HatcheryBlockEntity> blockEntity(Machine machine) { return BLOCK_ENTITIES.get(machine).get(); }
    public static MenuType<HatcheryMenu> menu(Machine machine) { return MENU_TYPES.get(machine).get(); }
    public static Item syringe(Species species) { return SYRINGES.get(species).get(); }
    public static Item egg(Species species) { return EGGS.containsKey(species) ? EGGS.get(species).get() : null; }
    public static Item hatchedEgg(Species species) { return HATCHED.containsKey(species) ? HATCHED.get(species).get() : null; }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); }
}
