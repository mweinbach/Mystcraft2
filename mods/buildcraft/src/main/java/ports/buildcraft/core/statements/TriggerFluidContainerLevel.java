package ports.buildcraft.core.statements;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementParameterItemStack;
import ports.buildcraft.statement.Trigger;

/**
 * Upstream {@code TriggerFluidContainerLevel}: whether the first neighbouring tank that is empty or holds
 * the searched fluid is filled below a quarter, a half or three quarters.
 */
public class TriggerFluidContainerLevel extends BCStatement implements Trigger.External {
    public enum TriggerType {
        BELOW25(0.25F),
        BELOW50(0.5F),
        BELOW75(0.75F);

        public final float level;

        TriggerType(float level) {
            this.level = level;
        }
    }

    public final TriggerType type;
    private final ResourceLocation sprite;

    TriggerFluidContainerLevel(TriggerType type) {
        super("buildcraft:fluid." + name(type), "buildcraft.fluid." + name(type));
        this.type = type;
        this.sprite = sprite("trigger_liquidcontainer_" + name(type));
    }

    private static String name(TriggerType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite;
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gate.trigger.fluidlevel.below", (int) (type.level * 100));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, StatementContainer container, StatementParameter[] parameters) {
        FluidPort port = BCCoreStatements.fluids(tile, side);
        if (port == null) return false;
        Fluid searched = BCCoreStatements.searchedFluid(parameters);
        List<FluidAmount> tanks = port.contents();
        for (int i = 0; i < tanks.size(); i++) {
            FluidAmount fluid = tanks.get(i);
            if (fluid.isEmpty()) return searched == null || port.fill(searched, 1, true) > 0;
            if (searched == null || fluid.fluid() == searched) return fluid.amount() / (float) port.capacity(i) < type.level;
        }
        return false;
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return StatementParameterItemStack.EMPTY;
    }

    @Override
    public Statement[] getPossible() {
        return BCCoreStatements.TRIGGER_FLUID_ALL;
    }
}
