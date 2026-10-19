package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierScreenHandler;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerScreenHandler;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterScreenHandler;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorScreenHandler;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserScreenHandler;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorScreenHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ScreenRegistry {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AdvancedGenetics.MOD_ID);

    public static final RegistryObject<MenuType<CellAnalyzerScreenHandler>> CELL_ANALYZER_SCREEN_HANDLER = register("cell_analyzer_menu", CellAnalyzerScreenHandler::new);
    public static final RegistryObject<MenuType<DnaExtractorScreenHandler>> DNA_EXTRACTOR_SCREEN_HANDLER = register("dna_extractor_menu", DnaExtractorScreenHandler::new);
    public static final RegistryObject<MenuType<DnaDecrypterScreenHandler>> DNA_DECRYPTER_SCREEN_HANDLER = register("dna_decrypter_menu", DnaDecrypterScreenHandler::new);
    public static final RegistryObject<MenuType<PlasmidInfuserScreenHandler>> PLASMID_INFUSER_SCREEN_HANDLER = register("plasmid_infuser_menu", PlasmidInfuserScreenHandler::new);
    public static final RegistryObject<MenuType<BloodPurifierScreenHandler>> BLOOD_PURIFIER_SCREEN_HANDLER = register("blood_purifier_menu", BloodPurifierScreenHandler::new);
    public static final RegistryObject<MenuType<PlasmidInjectorScreenHandler>> PLASMID_INJECTOR_SCREEN_HANDLER = register("plasmid_injector_menu", PlasmidInjectorScreenHandler::new);

    private static <T extends AbstractContainerMenu> RegistryObject<MenuType<T>> register(String name, IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IForgeMenuType.create(factory));
    }
}
