package ports.buildcraft.robotics;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ports.buildcraft.lib.Power;
import ports.buildcraft.robotics.board.RobotBoardType;

/**
 * The robot item, written for this port. It carries the robot's board and stored energy in its tag; using it on a
 * free docking station deploys the robot there, linked to that station as its home. A freshly crafted robot has no
 * board and an empty battery.
 */
public class RobotItem extends Item {
    public static final String BOARD = "board";
    public static final String ENERGY = "energy";

    public RobotItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static RobotBoardType board(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : RobotBoardType.byId(tag.getString(BOARD));
    }

    /** Stored energy in micro-MJ. */
    public static long energy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getLong(ENERGY), 0, RobotEntity.CAPACITY);
    }

    /** A copy of {@code stack} with {@code board}, or none, keeping its energy. */
    public static ItemStack withBoard(ItemStack stack, @Nullable RobotBoardType board) {
        ItemStack result = stack.copy();
        result.setCount(1);
        if (board == null) result.removeTagKey(BOARD);
        else result.getOrCreateTag().putString(BOARD, board.id);
        if (result.getTag() != null && result.getTag().isEmpty()) result.setTag(null);
        return result;
    }

    public static ItemStack create(@Nullable RobotBoardType board, long energy) {
        ItemStack stack = withBoard(new ItemStack(BCRobotics.ROBOT_ITEM.get()), board);
        if (energy > 0) stack.getOrCreateTag().putLong(ENERGY, energy);
        return stack;
    }

    public static ItemStack fromRobot(RobotEntity robot) {
        return create(robot.boardType(), robot.battery().stored());
    }

    /** A robot made from {@code stack}, docked at {@code station} and calling it home; the caller adds it to the level. */
    public static RobotEntity createRobot(ServerLevel level, ItemStack stack, StationRef station) {
        RobotEntity robot = new RobotEntity(BCRobotics.ROBOT_TYPE.get(), level);
        robot.setBoard(board(stack));
        robot.battery().set(energy(stack));
        robot.deployAt(station);
        return robot;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energy(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack) / RobotEntity.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xD01010;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        RobotBoardType board = board(stack);
        tooltip.add(board == null ? Component.translatable("item.buildcraft.robot.no_board").withStyle(ChatFormatting.GRAY)
                : board.displayName().copy().withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.buildcraft.robot.energy", Power.formatMj(energy(stack)),
                Power.formatMj(RobotEntity.CAPACITY)).withStyle(ChatFormatting.GRAY));
    }
}
