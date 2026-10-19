package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.genetics.Entities;
import com.technovision.advancedgenetics.common.item.*;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ItemRegistry {

    // Organic Matter and Cells
    public static final Map<EntityType<?>, Supplier<OrganicMatterItem>> MATTER = new LinkedHashMap<>();
    public static final Map<EntityType<?>, Supplier<CellItem>> CELLS = new LinkedHashMap<>();

    // Block Items
    public static final Supplier<BlockItem> CELL_ANALYZER = blockItem("cell_analyzer", BlockRegistry.CELL_ANALYZER);
    public static final Supplier<BlockItem> DNA_EXTRACTOR = blockItem("dna_extractor", BlockRegistry.DNA_EXTRACTOR);
    public static final Supplier<BlockItem> DNA_DECRYPTER = blockItem("dna_decrypter", BlockRegistry.DNA_DECRYPTER);
    public static final Supplier<BlockItem> PLASMID_INFUSER = blockItem("plasmid_infuser", BlockRegistry.PLASMID_INFUSER);
    public static final Supplier<BlockItem> BLOOD_PURIFIER = blockItem("blood_purifier", BlockRegistry.BLOOD_PURIFIER);
    public static final Supplier<BlockItem> PLASMID_INJECTOR = blockItem("plasmid_injector", BlockRegistry.PLASMID_INJECTOR);

    // Tools
    public static final Supplier<ScalpelItem> METAL_SCALPEL = register("metal_scalpel", () -> new ScalpelItem(25));
    public static final Supplier<ScalpelItem> DIAMOND_SCALPEL = register("diamond_scalpel", () -> new ScalpelItem(150));
    public static final Supplier<ScalpelItem> NETHERITE_SCALPEL = register("netherite_scalpel", () -> new ScalpelItem(300));
    public static final Supplier<SyringeItem> SYRINGE = register("syringe", SyringeItem::new);
    public static final Supplier<Item> OVERCLOCKER = register("overclocker", () -> new Item(properties()));
    public static final Supplier<Item> CROWBAR = register("crowbar", () -> new Item(properties().stacksTo(1).durability(100)));

    // Other Items
    public static final Supplier<DnaItem> DNA_HELIX = register("dna_helix", DnaItem::new);
    public static final Supplier<PlasmidItem> PLASMID = register("plasmid", PlasmidItem::new);
    public static final Supplier<AntiPlasmidItem> ANTIPLASMID = register("antiplasmid", AntiPlasmidItem::new);
    public static final Supplier<DragonHealthCrystalItem> DRAGON_HEALTH_CRYSTAL = register("dragon_health_crystal", DragonHealthCrystalItem::new);

    static {
        // Organic Matter
        for (Entities entity : Entities.values()) {
            MATTER.put(entity.getType(), register(entity.getName() + "_matter", () -> new OrganicMatterItem(entity.getType())));
        }

        // Cells
        for (Entities entity : Entities.values()) {
            CELLS.put(entity.getType(), register(entity.getName() + "_cell", () -> new CellItem(entity.getType(), entity.getColor())));
        }
    }

    public static void init() { }

    public static Item.Properties properties() {
        return new Item.Properties().tab(AdvancedGenetics.TAB);
    }

    private static <T extends Item> Supplier<T> register(String name, Supplier<T> item) {
        return Platform.register(Registry.ITEM_REGISTRY, name, item);
    }

    private static Supplier<BlockItem> blockItem(String name, Supplier<? extends Block> block) {
        return register(name, () -> new BlockItem(block.get(), properties()));
    }
}
