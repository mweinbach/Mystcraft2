package ports.buildcraft.transport.statements;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.PipeDefinition;
import ports.buildcraft.transport.behaviour.LimiterBehaviour;

/**
 * Upstream {@code ActionPowerLimit} ({@code ActionIronPowerLimit} and {@code ActionDiamondPowerLimit}): sets an iron
 * or diamond kinesis pipe's limit to its rate shifted right by {@link #limitShift}; the pipe's behaviour does the
 * setting. Upstream's RF variants are not ported because kinesis pipes speak FE/E instead of RF pipes.
 */
public class ActionPowerLimit extends BCStatement implements Action.Internal {
    /** Upstream {@code BCTransportSprites.POWER_LIMIT}, by shift. */
    private static final String[] SPRITES = {"m256", "m128", "m64", "m16", "m8", "m2", "m0"};

    private final PipeDefinition pipe;
    /** Behaves as {@link LimiterBehaviour}'s shift. */
    public final int limitShift;
    private final ActionPowerLimit[] possible;
    private final ResourceLocation sprite;

    ActionPowerLimit(String suffix, PipeDefinition pipe, int limitShift, ActionPowerLimit[] possible) {
        super("buildcraft:pipe.power_limit." + suffix + "_s" + limitShift);
        this.pipe = pipe;
        this.limitShift = limitShift;
        this.possible = possible;
        this.sprite = sprite("trigger_limiter_" + SPRITES[limitShift]);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.action.pipe.power_limit", LimiterBehaviour.limitMj(pipe.powerTransferPerTick, limitShift));
    }

    @Override
    public void actionActivate(StatementContainer source, StatementParameter[] parameters) {
        // The behaviour handles this.
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public Statement[] getPossible() {
        return possible;
    }
}
