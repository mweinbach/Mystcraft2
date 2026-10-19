package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.genetics.Entities;
import com.technovision.advancedgenetics.common.item.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class ItemRegistry {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AdvancedGenetics.MOD_ID);

    // Organic Matter and Cells
    public static final Map<EntityType<?>, RegistryObject<OrganicMatterItem>> MATTER = new LinkedHashMap<>();
    public static final Map<EntityType<?>, RegistryObject<CellItem>> CELLS = new LinkedHashMap<>();

    // Block Items
    public static final RegistryObject<BlockItem> CELL_ANALYZER = blockItem("cell_analyzer", BlockRegistry.CELL_ANALYZER);
    public static final RegistryObject<BlockItem> DNA_EXTRACTOR = blockItem("dna_extractor", BlockRegistry.DNA_EXTRACTOR);
    public static final RegistryObject<BlockItem> DNA_DECRYPTER = blockItem("dna_decrypter", BlockRegistry.DNA_DECRYPTER);
    public static final RegistryObject<BlockItem> PLASMID_INFUSER = blockItem("plasmid_infuser", BlockRegistry.PLASMID_INFUSER);
    public static final RegistryObject<BlockItem> BLOOD_PURIFIER = blockItem("blood_purifier", BlockRegistry.BLOOD_PURIFIER);
    public static final RegistryObject<BlockItem> PLASMID_INJECTOR = blockItem("plasmid_injector", BlockRegistry.PLASMID_INJECTOR);

    // Tools
    public static final RegistryObject<ScalpelItem> METAL_SCALPEL = ITEMS.register("metal_scalpel", () -> new ScalpelItem(25));
    public static final RegistryObject<ScalpelItem> DIAMOND_SCALPEL = ITEMS.register("diamond_scalpel", () -> new ScalpelItem(150));
    public static final RegistryObject<ScalpelItem> NETHERITE_SCALPEL = ITEMS.register("netherite_scalpel", () -> new ScalpelItem(300));
    public static final RegistryObject<SyringeItem> SYRINGE = ITEMS.register("syringe", SyringeItem::new);
    public static final RegistryObject<Item> OVERCLOCKER = ITEMS.register("overclocker", () -> new Item(properties()));
    public static final RegistryObject<Item> CROWBAR = ITEMS.register("crowbar", () -> new Item(properties().stacksTo(1).durability(100)));

    // Other Items
    public static final RegistryObject<DnaItem> DNA_HELIX = ITEMS.register("dna_helix", DnaItem::new);
    public static final RegistryObject<PlasmidItem> PLASMID = ITEMS.register("plasmid", PlasmidItem::new);
    public static final RegistryObject<AntiPlasmidItem> ANTIPLASMID = ITEMS.register("antiplasmid", AntiPlasmidItem::new);
    public static final RegistryObject<DragonHealthCrystalItem> DRAGON_HEALTH_CRYSTAL = ITEMS.register("dragon_health_crystal", DragonHealthCrystalItem::new);

    static {
        // Organic Matter
        for (Entities entity : Entities.values()) {
            MATTER.put(entity.getType(), ITEMS.register(entity.getName() + "_matter", () -> new OrganicMatterItem(entity.getType())));
        }

        // Cells
        for (Entities entity : Entities.values()) {
            CELLS.put(entity.getType(), ITEMS.register(entity.getName() + "_cell", () -> new CellItem(entity.getType(), entity.getColor())));
        }
    }

    public static Item.Properties properties() {
        return new Item.Properties().tab(AdvancedGenetics.TAB);
    }

    private static RegistryObject<BlockItem> blockItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), properties()));
    }
}
