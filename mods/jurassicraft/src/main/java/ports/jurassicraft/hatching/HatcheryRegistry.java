package ports.jurassicraft.hatching;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
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
    private static final Map<Machine, Supplier<Block>> MACHINES = new EnumMap<>(Machine.class);
    private static final Map<Machine, Supplier<BlockEntityType<HatcheryBlockEntity>>> BLOCK_ENTITIES = new EnumMap<>(Machine.class);
    private static final Map<Machine, Supplier<MenuType<HatcheryMenu>>> MENU_TYPES = new EnumMap<>(Machine.class);
    private static final Map<Species, Supplier<Item>> SYRINGES = new EnumMap<>(Species.class);
    private static final Map<Species, Supplier<Item>> EGGS = new EnumMap<>(Species.class);
    private static final Map<Species, Supplier<Item>> HATCHED = new EnumMap<>(Species.class);
    public static final Supplier<Block> PEAT_MOSS = Platform.register(Registry.BLOCK_REGISTRY, "peat_moss", () -> new Block(BlockBehaviour.Properties.of(Material.DIRT).strength(.5F).sound(SoundType.GRAVEL)));
    public static final Supplier<Item> PEAT_MOSS_ITEM = Platform.register(Registry.ITEM_REGISTRY, "peat_moss", () -> new BlockItem(PEAT_MOSS.get(), new Item.Properties().tab(JurassicContent.TAB)));
    static {
        for (Machine machine : Machine.values()) {
            Supplier<Block> block = Platform.register(Registry.BLOCK_REGISTRY, machine.id, () -> new HatcheryBlock(machine));
            MACHINES.put(machine, block);
            Platform.register(Registry.ITEM_REGISTRY, machine.id, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
            BLOCK_ENTITIES.put(machine, Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, machine.id, () -> BlockEntityType.Builder.of((p,s) -> new HatcheryBlockEntity(p,s,machine), block.get()).build(null)));
            MENU_TYPES.put(machine, Platform.register(Registry.MENU_REGISTRY, machine.id, () -> Platform.menuType((id, inv, data) -> new HatcheryMenu(id, inv, data, machine))));
        }
        for (Species species : Species.values()) {
            SYRINGES.put(species, geneticItem(species.id() + "_syringe"));
            if (species != Species.COELACANTH) EGGS.put(species, geneticItem(species.id() + "_egg"));
            String readyId = species.id() + (species == Species.COELACANTH ? "_gestated" : "_hatched_egg");
            HATCHED.put(species, Platform.register(Registry.ITEM_REGISTRY, readyId, () -> new HatchedEggItem(species)));
        }
    }
    private HatcheryRegistry() {}
    private static Supplier<Item> geneticItem(String id) { return Platform.register(Registry.ITEM_REGISTRY, id, () -> new GeneticsItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1))); }
    public static Block block(Machine machine) { return MACHINES.get(machine).get(); }
    public static BlockEntityType<HatcheryBlockEntity> blockEntity(Machine machine) { return BLOCK_ENTITIES.get(machine).get(); }
    public static MenuType<HatcheryMenu> menu(Machine machine) { return MENU_TYPES.get(machine).get(); }
    public static Item syringe(Species species) { return SYRINGES.get(species).get(); }
    public static Item egg(Species species) { return EGGS.containsKey(species) ? EGGS.get(species).get() : null; }
    public static Item hatchedEgg(Species species) { return HATCHED.containsKey(species) ? HATCHED.get(species).get() : null; }
    public static void init() { }
}
