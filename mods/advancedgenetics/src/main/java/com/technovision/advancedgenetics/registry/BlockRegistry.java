package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierBlock;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerBlock;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterBlock;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorBlock;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserBlock;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorBlock;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class BlockRegistry {

    public static final Supplier<CellAnalyzerBlock> CELL_ANALYZER = register("cell_analyzer", CellAnalyzerBlock::new);
    public static final Supplier<DnaExtractorBlock> DNA_EXTRACTOR = register("dna_extractor", DnaExtractorBlock::new);
    public static final Supplier<DnaDecrypterBlock> DNA_DECRYPTER = register("dna_decrypter", DnaDecrypterBlock::new);
    public static final Supplier<PlasmidInfuserBlock> PLASMID_INFUSER = register("plasmid_infuser", PlasmidInfuserBlock::new);
    public static final Supplier<BloodPurifierBlock> BLOOD_PURIFIER = register("blood_purifier", BloodPurifierBlock::new);
    public static final Supplier<PlasmidInjectorBlock> PLASMID_INJECTOR = register("plasmid_injector", PlasmidInjectorBlock::new);

    public static void init() { }

    private static <T extends Block> Supplier<T> register(String name, Supplier<T> block) {
        return Platform.register(Registry.BLOCK_REGISTRY, name, block);
    }
}
