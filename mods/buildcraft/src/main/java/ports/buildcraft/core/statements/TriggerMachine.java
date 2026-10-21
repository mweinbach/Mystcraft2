package ports.buildcraft.core.statements;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.lib.HasWork;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/** Upstream {@code TriggerMachine}: whether the neighbouring machine has work to do. */
public class TriggerMachine extends BCStatement implements Trigger.External {
    public final boolean active;
    private final ResourceLocation sprite;

    TriggerMachine(boolean active) {
        super("buildcraft:work." + state(active), "buildcraft.work." + state(active));
        this.active = active;
        this.sprite = sprite(active ? "trigger_machine_active" : "trigger_machine_inactive");
    }

    private static String state(boolean active) {
        return active ? "scheduled" : "done";
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.machine." + state(active));
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, StatementContainer container, StatementParameter[] parameters) {
        return tile instanceof HasWork machine && machine.hasWork() == active;
    }

    @Override
    public Statement[] getPossible() {
        return BCCoreStatements.TRIGGER_MACHINE;
    }
}
