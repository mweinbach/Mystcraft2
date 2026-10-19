package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierScreenHandler;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerScreenHandler;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterScreenHandler;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorScreenHandler;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserScreenHandler;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorScreenHandler;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public class ScreenRegistry {

    public static final Supplier<MenuType<CellAnalyzerScreenHandler>> CELL_ANALYZER_SCREEN_HANDLER = Platform.menu("cell_analyzer_menu", CellAnalyzerScreenHandler::new);
    public static final Supplier<MenuType<DnaExtractorScreenHandler>> DNA_EXTRACTOR_SCREEN_HANDLER = Platform.menu("dna_extractor_menu", DnaExtractorScreenHandler::new);
    public static final Supplier<MenuType<DnaDecrypterScreenHandler>> DNA_DECRYPTER_SCREEN_HANDLER = Platform.menu("dna_decrypter_menu", DnaDecrypterScreenHandler::new);
    public static final Supplier<MenuType<PlasmidInfuserScreenHandler>> PLASMID_INFUSER_SCREEN_HANDLER = Platform.menu("plasmid_infuser_menu", PlasmidInfuserScreenHandler::new);
    public static final Supplier<MenuType<BloodPurifierScreenHandler>> BLOOD_PURIFIER_SCREEN_HANDLER = Platform.menu("blood_purifier_menu", BloodPurifierScreenHandler::new);
    public static final Supplier<MenuType<PlasmidInjectorScreenHandler>> PLASMID_INJECTOR_SCREEN_HANDLER = Platform.menu("plasmid_injector_menu", PlasmidInjectorScreenHandler::new);

    public static void init() { }
}
