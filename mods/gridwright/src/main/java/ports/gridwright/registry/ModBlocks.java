package ports.gridwright.registry;

import ports.gridwright.block.MachineBlock;
import ports.gridwright.block.MechanicalBlock;
import ports.gridwright.block.ProducerBlock;
import ports.gridwright.block.WorkerBlock;
import ports.gridwright.grid.ProducerKind;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class ModBlocks {

    private static final Map<String, Supplier<? extends Block>> ALL = new LinkedHashMap<>();
    /** Every block by registry path, in registration order; each gets a block item. */
    public static final Map<String, Supplier<? extends Block>> BLOCKS = Collections.unmodifiableMap(ALL);

    public static final Supplier<ProducerBlock> SOLAR_PANEL = producer("solar_panel", ProducerKind.SOLAR_PANEL, Material.METAL, SoundType.METAL);
    public static final Supplier<ProducerBlock> LUNAR_PANEL = producer("lunar_panel", ProducerKind.LUNAR_PANEL, Material.METAL, SoundType.METAL);
    public static final Supplier<ProducerBlock> WATER_MILL = producer("water_mill", ProducerKind.WATER_MILL, Material.WOOD, SoundType.WOOD);
    public static final Supplier<ProducerBlock> WIND_MILL = producer("wind_mill", ProducerKind.WIND_MILL, Material.WOOD, SoundType.WOOD);

    public static final Supplier<MachineBlock> RESONATOR = register("resonator", () -> new MachineBlock(null,
            BlockBehaviour.Properties.of(Material.METAL).strength(3.5F).sound(SoundType.METAL)));

    public static final Map<GeneratorKind, Supplier<MachineBlock>> GENERATORS = generators();

    // Phase 2c: quarries and mechanical blocks.
    public static final Supplier<WorkerBlock> QUANTUM_QUARRY = register("quantum_quarry", () -> new WorkerBlock(WorkerBlock.Kind.QUANTUM_QUARRY,
            BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final Supplier<Block> QUARRY_ACTUATOR = register("quarry_actuator", () -> new Block(
            BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final Supplier<WorkerBlock> ENDER_QUARRY = register("ender_quarry", () -> new WorkerBlock(WorkerBlock.Kind.ENDER_QUARRY,
            BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final Supplier<MechanicalBlock> MECHANICAL_MINER = register("mechanical_miner", () -> new MechanicalBlock(
            WorkerBlock.Kind.MECHANICAL_MINER, BlockBehaviour.Properties.of(Material.STONE).strength(3.5F).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));
    public static final Supplier<MechanicalBlock> MECHANICAL_USER = register("mechanical_user", () -> new MechanicalBlock(
            WorkerBlock.Kind.MECHANICAL_USER, BlockBehaviour.Properties.of(Material.STONE).strength(3.5F).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    private ModBlocks() { }

    public static void init() { }

    public static String generatorName(GeneratorKind kind) {
        return kind.name().toLowerCase(Locale.ROOT) + "_generator";
    }

    private static Map<GeneratorKind, Supplier<MachineBlock>> generators() {
        Map<GeneratorKind, Supplier<MachineBlock>> generators = new EnumMap<>(GeneratorKind.class);
        for (GeneratorKind kind : GeneratorKind.values()) {
            Material material = kind == GeneratorKind.SURVIVALIST ? Material.STONE : Material.METAL;
            SoundType sound = kind == GeneratorKind.SURVIVALIST ? SoundType.STONE : SoundType.METAL;
            generators.put(kind, register(generatorName(kind), () -> new MachineBlock(kind, BlockBehaviour.Properties.of(material)
                    .strength(3.5F).sound(sound).lightLevel(state -> state.getValue(MachineBlock.LIT) ? 13 : 0))));
        }
        return Collections.unmodifiableMap(generators);
    }

    private static Supplier<ProducerBlock> producer(String name, ProducerKind kind, Material material, SoundType sound) {
        return register(name, () -> new ProducerBlock(kind, BlockBehaviour.Properties.of(material).strength(2.0F).sound(sound).noOcclusion()));
    }

    private static <T extends Block> Supplier<T> register(String name, Supplier<T> block) {
        Supplier<T> entry = Platform.register(Registry.BLOCK_REGISTRY, name, block);
        ALL.put(name, entry);
        return entry;
    }
}
