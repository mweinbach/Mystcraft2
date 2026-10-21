package ports.buildcraft.silicon.statement;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;

/** Upstream {@code TriggerTimer}: active for the one tick when the world time divides the duration. */
public class TriggerTimer extends BCStatement implements Trigger.Internal {
    public enum Duration {
        SHORT(5),
        MEDIUM(10),
        LONG(15);

        public final int duration;

        Duration(int duration) {
            this.duration = duration;
        }
    }

    private final Duration duration;
    private final ResourceLocation sprite;

    public TriggerTimer(Duration duration) {
        super("buildcraft:timer_" + duration.name().toLowerCase(Locale.ROOT));
        this.duration = duration;
        this.sprite = sprite("trigger_timer_" + duration.name().toLowerCase(Locale.ROOT));
    }

    public Duration duration() {
        return duration;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.timer", duration.duration);
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public boolean isTriggerActive(StatementContainer source, StatementParameter[] parameters) {
        Level level = source.getTile().getLevel();
        return level != null && level.getGameTime() % (20L * duration.duration) == 0;
    }

    @Override
    public TriggerTimer[] getPossible() {
        return BCSiliconStatements.TRIGGER_TIMER;
    }
}
