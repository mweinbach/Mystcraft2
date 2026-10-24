package ports.gridwright.registry;

import ports.gridwright.grid.ProducerBlockEntity;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.ResonatorBlockEntity;
import ports.gridwright.platform.Platform;
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

    private ModBlockEntities() { }

    public static void init() { }
}
