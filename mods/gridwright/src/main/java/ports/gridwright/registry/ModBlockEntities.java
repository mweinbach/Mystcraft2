package ports.gridwright.registry;

import ports.gridwright.grid.ProducerBlockEntity;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.ResonatorBlockEntity;
import ports.gridwright.platform.Platform;
import ports.gridwright.worker.EnderQuarryBlockEntity;
import ports.gridwright.worker.MechanicalCrafterBlockEntity;
import ports.gridwright.worker.MechanicalMinerBlockEntity;
import ports.gridwright.worker.MechanicalUserBlockEntity;
import ports.gridwright.worker.QuantumQuarryBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public final class ModBlockEntities {

    public static final Supplier<BlockEntityType<ProducerBlockEntity>> PRODUCER = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "producer", () -> BlockEntityType.Builder.of(ProducerBlockEntity::new,
                    ModBlocks.SOLAR_PANEL.get(), ModBlocks.LUNAR_PANEL.get(), ModBlocks.WATER_MILL.get(), ModBlocks.WIND_MILL.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ResonatorBlockEntity>> RESONATOR = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "resonator", () -> BlockEntityType.Builder.of(ResonatorBlockEntity::new,
                    ModBlocks.RESONATOR.get()).build(null));

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "generator", () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new,
                    ModBlocks.GENERATORS.values().stream().map(Supplier::get).toArray(Block[]::new)).build(null));

    // Phase 2c: quarries and mechanical blocks.
    public static final Supplier<BlockEntityType<QuantumQuarryBlockEntity>> QUANTUM_QUARRY = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "quantum_quarry", () -> BlockEntityType.Builder.of(QuantumQuarryBlockEntity::new,
                    ModBlocks.QUANTUM_QUARRY.get()).build(null));
    public static final Supplier<BlockEntityType<EnderQuarryBlockEntity>> ENDER_QUARRY = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "ender_quarry", () -> BlockEntityType.Builder.of(EnderQuarryBlockEntity::new,
                    ModBlocks.ENDER_QUARRY.get()).build(null));
    public static final Supplier<BlockEntityType<MechanicalMinerBlockEntity>> MECHANICAL_MINER = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "mechanical_miner", () -> BlockEntityType.Builder.of(MechanicalMinerBlockEntity::new,
                    ModBlocks.MECHANICAL_MINER.get()).build(null));
    public static final Supplier<BlockEntityType<MechanicalUserBlockEntity>> MECHANICAL_USER = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "mechanical_user", () -> BlockEntityType.Builder.of(MechanicalUserBlockEntity::new,
                    ModBlocks.MECHANICAL_USER.get()).build(null));
    // Phase 3
    public static final Supplier<BlockEntityType<MechanicalCrafterBlockEntity>> MECHANICAL_CRAFTER = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "mechanical_crafter", () -> BlockEntityType.Builder.of(MechanicalCrafterBlockEntity::new,
                    ModBlocks.MECHANICAL_CRAFTER.get()).build(null));

    private ModBlockEntities() { }

    public static void init() { }
}
