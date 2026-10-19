package ports.buildcraft.factory;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Reg;

/** Upstream BuildCraft Factory blocks covered by this port: tank, pump and mining well. */
public final class BCFactory {
    public static final Supplier<TankBlock> TANK = Reg.block("tank",
            () -> new TankBlock(BlockBehaviour.Properties.of(Material.GLASS).strength(0.3F).sound(SoundType.GLASS).noOcclusion()));
    public static final Supplier<MinerBlock> PUMP = Reg.block("pump",
            () -> MinerBlock.pump(BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(SoundType.METAL)));
    public static final Supplier<MinerBlock> MINING_WELL = Reg.block("mining_well",
            () -> MinerBlock.miningWell(BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(SoundType.METAL)));
    public static final Supplier<TubeBlock> TUBE = Reg.block("tube",
            () -> new TubeBlock(BlockBehaviour.Properties.of(Material.METAL).strength(0.5F).noOcclusion().noLootTable()));

    public static final Supplier<Item> TANK_ITEM = Reg.blockItem("tank", TANK, BuildCraft.itemProperties());
    public static final Supplier<Item> PUMP_ITEM = Reg.blockItem("pump", PUMP, BuildCraft.itemProperties());
    public static final Supplier<Item> MINING_WELL_ITEM = Reg.blockItem("mining_well", MINING_WELL, BuildCraft.itemProperties());

    public static final Supplier<BlockEntityType<TankBlockEntity>> TANK_TYPE = Reg.blockEntity("tank",
            () -> BlockEntityType.Builder.of(TankBlockEntity::new, TANK.get()).build(null));
    public static final Supplier<BlockEntityType<PumpBlockEntity>> PUMP_TYPE = Reg.blockEntity("pump",
            () -> BlockEntityType.Builder.of(PumpBlockEntity::new, PUMP.get()).build(null));
    public static final Supplier<BlockEntityType<MiningWellBlockEntity>> MINING_WELL_TYPE = Reg.blockEntity("mining_well",
            () -> BlockEntityType.Builder.of(MiningWellBlockEntity::new, MINING_WELL.get()).build(null));

    private BCFactory() {}

    public static void init() {}
}
