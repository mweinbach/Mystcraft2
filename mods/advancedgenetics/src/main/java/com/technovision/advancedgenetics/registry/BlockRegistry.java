package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierBlock;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerBlock;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterBlock;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorBlock;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserBlock;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockRegistry {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AdvancedGenetics.MOD_ID);

    public static final RegistryObject<CellAnalyzerBlock> CELL_ANALYZER = BLOCKS.register("cell_analyzer", CellAnalyzerBlock::new);
    public static final RegistryObject<DnaExtractorBlock> DNA_EXTRACTOR = BLOCKS.register("dna_extractor", DnaExtractorBlock::new);
    public static final RegistryObject<DnaDecrypterBlock> DNA_DECRYPTER = BLOCKS.register("dna_decrypter", DnaDecrypterBlock::new);
    public static final RegistryObject<PlasmidInfuserBlock> PLASMID_INFUSER = BLOCKS.register("plasmid_infuser", PlasmidInfuserBlock::new);
    public static final RegistryObject<BloodPurifierBlock> BLOOD_PURIFIER = BLOCKS.register("blood_purifier", BloodPurifierBlock::new);
    public static final RegistryObject<PlasmidInjectorBlock> PLASMID_INJECTOR = BLOCKS.register("plasmid_injector", PlasmidInjectorBlock::new);
}
