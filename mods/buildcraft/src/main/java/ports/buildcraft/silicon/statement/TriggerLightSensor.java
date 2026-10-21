package ports.buildcraft.silicon.statement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/**
 * Upstream {@code TriggerLightSensor}: on when the block outside this face is darker than 8, or brighter, the two
 * being opposites. Upstream read {@code World.getLightFromNeighbors}. This uses {@code getMaxLocalRawBrightness},
 * which for a full cube is the light stored in that block; upstream also read the brightest neighbour when the
 * block was not a full cube.
 */
public class TriggerLightSensor extends BCStatement implements Trigger.InternalSided {
    private final boolean bright;
    private final ResourceLocation sprite;

    public TriggerLightSensor(boolean bright) {
        super("buildcraft:light_" + (bright ? "bright" : "dark"));
        this.bright = bright;
        this.sprite = sprite(bright ? "trigger_light_bright" : "trigger_light_dark");
    }

    public boolean bright() {
        return bright;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.light." + (bright ? "bright" : "dark"));
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public boolean isTriggerActive(Direction side, StatementContainer source, StatementParameter[] parameters) {
        BlockEntity tile = source.getTile();
        Level level = tile.getLevel();
        if (level == null) return false;
        BlockPos pos = tile.getBlockPos().relative(side);
        int light = level.getMaxLocalRawBrightness(pos);
        return (light < 8) ^ bright;
    }

    @Override
    public TriggerLightSensor[] getPossible() {
        return BCSiliconStatements.TRIGGER_LIGHT;
    }
}
