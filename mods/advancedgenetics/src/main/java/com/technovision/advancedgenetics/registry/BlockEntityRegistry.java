package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierBlockEntity;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerBlockEntity;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterBlockEntity;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorBlockEntity;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserBlockEntity;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockEntityRegistry {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AdvancedGenetics.MOD_ID);

    // Energy and item automation are exposed by the block entities as Forge capabilities.
    public static final RegistryObject<BlockEntityType<CellAnalyzerBlockEntity>> CELL_ANALYZER_BLOCK_ENTITY = register("cell_analyzer_block_entity", CellAnalyzerBlockEntity::new, BlockRegistry.CELL_ANALYZER);
    public static final RegistryObject<BlockEntityType<DnaExtractorBlockEntity>> DNA_EXTRACTOR_BLOCK_ENTITY = register("dna_extractor_block_entity", DnaExtractorBlockEntity::new, BlockRegistry.DNA_EXTRACTOR);
    public static final RegistryObject<BlockEntityType<DnaDecrypterBlockEntity>> DNA_DECRYPTER_BLOCK_ENTITY = register("dna_decrypter_block_entity", DnaDecrypterBlockEntity::new, BlockRegistry.DNA_DECRYPTER);
    public static final RegistryObject<BlockEntityType<PlasmidInfuserBlockEntity>> PLASMID_INFUSER_BLOCK_ENTITY = register("plasmid_infuser_block_entity", PlasmidInfuserBlockEntity::new, BlockRegistry.PLASMID_INFUSER);
    public static final RegistryObject<BlockEntityType<BloodPurifierBlockEntity>> BLOOD_PURIFIER_BLOCK_ENTITY = register("blood_purifier_block_entity", BloodPurifierBlockEntity::new, BlockRegistry.BLOOD_PURIFIER);
    public static final RegistryObject<BlockEntityType<PlasmidInjectorBlockEntity>> PLASMID_INJECTOR_BLOCK_ENTITY = register("plasmid_injector_block_entity", PlasmidInjectorBlockEntity::new, BlockRegistry.PLASMID_INJECTOR);

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, RegistryObject<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
