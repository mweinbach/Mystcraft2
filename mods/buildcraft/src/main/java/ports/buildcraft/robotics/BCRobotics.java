package ports.buildcraft.robotics;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.Reg;
import ports.buildcraft.robotics.board.RobotBoardType;
import ports.buildcraft.robotics.statements.BCRoboticsStatements;
import ports.buildcraft.transport.pluggable.PluggableDefinition;
import ports.buildcraft.transport.pluggable.SimplePluggableItem;

/**
 * Upstream {@code BCRoboticsBlocks}: the zone planner. The robots, redstone boards and docking station are this
 * port's clean-room additions (see {@code docs/robots-clean-room.md}); upstream 8.0.x ships none of their code.
 */
public final class BCRobotics {
    public static final Supplier<ZonePlannerBlock> ZONE_PLANNER = Reg.block("zone_planner",
            () -> new ZonePlannerBlock(BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(SoundType.METAL)));
    public static final Supplier<Item> ZONE_PLANNER_ITEM = Reg.blockItem("zone_planner", ZONE_PLANNER, BuildCraft.itemProperties());
    public static final Supplier<BlockEntityType<ZonePlannerBlockEntity>> ZONE_PLANNER_TYPE = Reg.blockEntity("zone_planner",
            () -> BlockEntityType.Builder.of(ZonePlannerBlockEntity::new, ZONE_PLANNER.get()).build(null));
    public static final Supplier<MenuType<ZonePlannerMenu>> ZONE_PLANNER_MENU = Platform.menu("zone_planner", ZonePlannerMenu::new);

    public static final PluggableDefinition DOCKING_STATION = PluggableDefinition.register(BuildCraft.id("docking_station"),
            DockingStationPluggable::new);
    public static final Supplier<Item> DOCKING_STATION_ITEM = Reg.item("docking_station",
            () -> new SimplePluggableItem(BuildCraft.pipeProperties(), DOCKING_STATION, null));
    public static final Supplier<Item> ROBOT_ITEM = Reg.item("robot", () -> new RobotItem(BuildCraft.itemProperties().stacksTo(16)));
    public static final Supplier<Item> REDSTONE_BOARD = Reg.item("redstone_board", () -> new RedstoneBoardItem(BuildCraft.itemProperties(), null));
    public static final Map<RobotBoardType, Supplier<Item>> BOARDS = new EnumMap<>(RobotBoardType.class);

    static {
        for (RobotBoardType type : RobotBoardType.values()) {
            BOARDS.put(type, Reg.item("board_" + type.id, () -> new RedstoneBoardItem(BuildCraft.itemProperties().stacksTo(16), type)));
        }
    }

    public static final Supplier<EntityType<RobotEntity>> ROBOT_TYPE = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "robot",
            () -> EntityType.Builder.<RobotEntity>of(RobotEntity::new, MobCategory.MISC).sized(RobotEntity.SIZE, RobotEntity.SIZE)
                    .clientTrackingRange(8).updateInterval(1).fireImmune().build(BuildCraft.id("robot").toString()));
    public static final Supplier<RecipeSerializer<RobotBoardRecipe>> ROBOT_BOARD_SERIALIZER =
            Platform.register(Registry.RECIPE_SERIALIZER_REGISTRY, "robot_board", RobotBoardRecipe.Serializer::new);

    private BCRobotics() {}

    public static void init() {
        BCRoboticsStatements.init();
    }
}
