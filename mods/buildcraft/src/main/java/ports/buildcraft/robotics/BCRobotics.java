package ports.buildcraft.robotics;

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

/** Upstream {@code BCRoboticsBlocks}: the zone planner. Robotics registers no items of its own. */
public final class BCRobotics {
    public static final Supplier<ZonePlannerBlock> ZONE_PLANNER = Reg.block("zone_planner",
            () -> new ZonePlannerBlock(BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(SoundType.METAL)));
    public static final Supplier<Item> ZONE_PLANNER_ITEM = Reg.blockItem("zone_planner", ZONE_PLANNER, BuildCraft.itemProperties());
    public static final Supplier<BlockEntityType<ZonePlannerBlockEntity>> ZONE_PLANNER_TYPE = Reg.blockEntity("zone_planner",
            () -> BlockEntityType.Builder.of(ZonePlannerBlockEntity::new, ZONE_PLANNER.get()).build(null));
    public static final Supplier<MenuType<ZonePlannerMenu>> ZONE_PLANNER_MENU = Platform.menu("zone_planner", ZonePlannerMenu::new);

    private BCRobotics() {}

    public static void init() {}
}
