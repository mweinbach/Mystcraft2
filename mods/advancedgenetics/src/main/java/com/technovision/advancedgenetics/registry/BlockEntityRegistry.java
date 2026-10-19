package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierBlockEntity;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerBlockEntity;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterBlockEntity;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorBlockEntity;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserBlockEntity;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorBlockEntity;
import com.technovision.advancedgenetics.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
import java.util.function.Supplier;

public class BlockEntityRegistry {

    // Each loader exposes the machines' energy and items through its own automation API.
    public static final Supplier<BlockEntityType<CellAnalyzerBlockEntity>> CELL_ANALYZER_BLOCK_ENTITY = register("cell_analyzer_block_entity", CellAnalyzerBlockEntity::new, BlockRegistry.CELL_ANALYZER);
    public static final Supplier<BlockEntityType<DnaExtractorBlockEntity>> DNA_EXTRACTOR_BLOCK_ENTITY = register("dna_extractor_block_entity", DnaExtractorBlockEntity::new, BlockRegistry.DNA_EXTRACTOR);
    public static final Supplier<BlockEntityType<DnaDecrypterBlockEntity>> DNA_DECRYPTER_BLOCK_ENTITY = register("dna_decrypter_block_entity", DnaDecrypterBlockEntity::new, BlockRegistry.DNA_DECRYPTER);
    public static final Supplier<BlockEntityType<PlasmidInfuserBlockEntity>> PLASMID_INFUSER_BLOCK_ENTITY = register("plasmid_infuser_block_entity", PlasmidInfuserBlockEntity::new, BlockRegistry.PLASMID_INFUSER);
    public static final Supplier<BlockEntityType<BloodPurifierBlockEntity>> BLOOD_PURIFIER_BLOCK_ENTITY = register("blood_purifier_block_entity", BloodPurifierBlockEntity::new, BlockRegistry.BLOOD_PURIFIER);
    public static final Supplier<BlockEntityType<PlasmidInjectorBlockEntity>> PLASMID_INJECTOR_BLOCK_ENTITY = register("plasmid_injector_block_entity", PlasmidInjectorBlockEntity::new, BlockRegistry.PLASMID_INJECTOR);

    public static final List<Supplier<? extends BlockEntityType<?>>> MACHINES = List.of(CELL_ANALYZER_BLOCK_ENTITY,
            DNA_EXTRACTOR_BLOCK_ENTITY, DNA_DECRYPTER_BLOCK_ENTITY, PLASMID_INFUSER_BLOCK_ENTITY, BLOOD_PURIFIER_BLOCK_ENTITY,
            PLASMID_INJECTOR_BLOCK_ENTITY);

    public static void init() { }

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> Supplier<BlockEntityType<T>> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }
}
