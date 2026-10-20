package ports.buildcraft.builders;

import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.Reg;

/** Upstream BuildCraft Builders blocks covered by this port: the quarry, its frame and the filler. */
public final class BCBuilders {
    public static final Supplier<QuarryBlock> QUARRY = Reg.block("quarry",
            () -> new QuarryBlock(BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(SoundType.ANVIL)));
    public static final Supplier<FrameBlock> FRAME = Reg.block("frame",
            () -> new FrameBlock(BlockBehaviour.Properties.of(Material.GLASS).strength(0.5F).sound(SoundType.METAL).noOcclusion().noLootTable()));
    public static final Supplier<FillerBlock> FILLER = Reg.block("filler",
            () -> new FillerBlock(BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(SoundType.METAL)));

    public static final Supplier<Item> QUARRY_ITEM = Reg.blockItem("quarry", QUARRY, BuildCraft.itemProperties());
    public static final Supplier<Item> FRAME_ITEM = Reg.blockItem("frame", FRAME, BuildCraft.itemProperties());
    public static final Supplier<Item> FILLER_ITEM = Reg.blockItem("filler", FILLER, BuildCraft.itemProperties());

    public static final Supplier<BlockEntityType<QuarryBlockEntity>> QUARRY_TYPE = Reg.blockEntity("quarry",
            () -> BlockEntityType.Builder.of(QuarryBlockEntity::new, QUARRY.get()).build(null));
    public static final Supplier<BlockEntityType<FillerBlockEntity>> FILLER_TYPE = Reg.blockEntity("filler",
            () -> BlockEntityType.Builder.of(FillerBlockEntity::new, FILLER.get()).build(null));

    public static final Supplier<MenuType<FillerMenu>> FILLER_MENU = Platform.menu("filler", FillerMenu::new);

    private BCBuilders() {}

    public static void init() {}
}
