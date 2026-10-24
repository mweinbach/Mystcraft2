package ports.buildcraft.robotics.board;

import java.util.Locale;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.robotics.RobotEntity;

/**
 * The programmed redstone boards, written for this port. Each gives a robot one job; the colour picks the board
 * item's texture from upstream's board art and the id picks the robot's skin from upstream's robot art. Names and
 * descriptions are upstream's {@code boardRobot*} language entries.
 */
public enum RobotBoardType {
    PICKER(Colour.YELLOW, PickerBoard::new),
    CARRIER(Colour.YELLOW, CarrierBoard::new),
    LUMBERJACK(Colour.GREEN, BreakerBoard::lumberjack),
    MINER(Colour.BLUE, BreakerBoard::miner);

    /** The board item textures upstream ships. */
    public enum Colour { RED, GREEN, BLUE, YELLOW }

    public final String id = name().toLowerCase(Locale.ROOT);
    public final Colour colour;
    private final Function<RobotEntity, RobotBoard> factory;

    RobotBoardType(Colour colour, Function<RobotEntity, RobotBoard> factory) {
        this.colour = colour;
        this.factory = factory;
    }

    public RobotBoard create(RobotEntity robot) {
        return factory.apply(robot);
    }

    /** The robot's skin on the block atlas, as the robot item model names it. */
    public ResourceLocation sprite() {
        return BuildCraft.id("entity/robot/" + id);
    }

    public Component displayName() {
        return Component.translatable("buildcraft.board." + id);
    }

    public Component description() {
        return Component.translatable("buildcraft.board." + id + ".desc");
    }

    @Nullable
    public static RobotBoardType byId(String id) {
        for (RobotBoardType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }

    /** The skin of a robot without a board. */
    public static final ResourceLocation BLANK_SPRITE = BuildCraft.id("entity/robot/base");
}
