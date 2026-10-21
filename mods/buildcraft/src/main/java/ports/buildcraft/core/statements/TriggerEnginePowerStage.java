package ports.buildcraft.core.statements;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.energy.EngineBlockEntity;
import ports.buildcraft.energy.PowerStage;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/** Upstream {@code TriggerEnginePowerStage}: whether the neighbouring engine has reached a heat stage. */
public class TriggerEnginePowerStage extends BCStatement implements Trigger.External {
    public final PowerStage stage;
    private final ResourceLocation sprite;

    TriggerEnginePowerStage(PowerStage stage) {
        super("buildcraft:engine.stage." + stage.getSerializedName());
        this.stage = stage;
        this.sprite = sprite("trigger_engineheat_" + stage.getSerializedName());
    }

    public static boolean isTriggeringTile(BlockEntity tile) {
        return tile instanceof EngineBlockEntity;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.engine." + stage.getSerializedName());
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public boolean isTriggerActive(BlockEntity target, Direction side, StatementContainer source, StatementParameter[] parameters) {
        return target instanceof EngineBlockEntity engine && engine.getPowerStage() == stage;
    }

    /** Upstream left the engine stages out of each other's rings. */
    @Override
    public Statement[] getPossible() {
        return BCCoreStatements.TRIGGER_POWER_STAGES;
    }
}
