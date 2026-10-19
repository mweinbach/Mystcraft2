package ports.jurassicraft.hatching;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
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
    private static final Map<Machine, Block> MACHINES = new EnumMap<>(Machine.class);
    private static final Map<Machine, BlockEntityType<HatcheryBlockEntity>> BLOCK_ENTITIES = new EnumMap<>(Machine.class);
    private static final Map<Machine, MenuType<HatcheryMenu>> MENU_TYPES = new EnumMap<>(Machine.class);
    private static final Map<Species, Item> SYRINGES = new EnumMap<>(Species.class);
    private static final Map<Species, Item> EGGS = new EnumMap<>(Species.class);
    private static final Map<Species, Item> HATCHED = new EnumMap<>(Species.class);
    public static final Supplier<Block> PEAT_MOSS = hold(Registry.register(Registry.BLOCK, id("peat_moss"), new Block(BlockBehaviour.Properties.of(Material.DIRT).strength(.5F).sound(SoundType.GRAVEL))));
    public static final Supplier<Item> PEAT_MOSS_ITEM = hold(Registry.register(Registry.ITEM, id("peat_moss"), new BlockItem(PEAT_MOSS.get(), new Item.Properties().tab(JurassicContent.TAB))));
    static {
        for (Machine machine : Machine.values()) {
            Block block = Registry.register(Registry.BLOCK, id(machine.id), new HatcheryBlock(machine));
            MACHINES.put(machine, block);
            Registry.register(Registry.ITEM, id(machine.id), new BlockItem(block, new Item.Properties().tab(JurassicContent.TAB)));
            BLOCK_ENTITIES.put(machine, Registry.register(Registry.BLOCK_ENTITY_TYPE, id(machine.id), BlockEntityType.Builder.of((p,s) -> new HatcheryBlockEntity(p,s,machine), block).build(null)));
            MENU_TYPES.put(machine, ScreenHandlerRegistry.registerExtended(id(machine.id), (menuId, inv, data) -> new HatcheryMenu(menuId, inv, data, machine)));
        }
        for (Species species : Species.values()) {
            SYRINGES.put(species, geneticItem(species.id() + "_syringe"));
            if (species != Species.COELACANTH) EGGS.put(species, geneticItem(species.id() + "_egg"));
            String readyId = species.id() + (species == Species.COELACANTH ? "_gestated" : "_hatched_egg");
            HATCHED.put(species, Registry.register(Registry.ITEM, id(readyId), new HatchedEggItem(species)));
        }
    }
    private HatcheryRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static <T> Supplier<T> hold(T value) { return () -> value; }
    private static Item geneticItem(String id) { return Registry.register(Registry.ITEM, id(id), new GeneticsItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1))); }
    public static Block block(Machine machine) { return MACHINES.get(machine); }
    public static BlockEntityType<HatcheryBlockEntity> blockEntity(Machine machine) { return BLOCK_ENTITIES.get(machine); }
    public static MenuType<HatcheryMenu> menu(Machine machine) { return MENU_TYPES.get(machine); }
    public static Item syringe(Species species) { return SYRINGES.get(species); }
    public static Item egg(Species species) { return EGGS.get(species); }
    public static Item hatchedEgg(Species species) { return HATCHED.get(species); }
    public static void init() {}
}
