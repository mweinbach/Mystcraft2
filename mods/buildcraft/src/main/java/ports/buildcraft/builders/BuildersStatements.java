package ports.buildcraft.builders;

import java.util.Collection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.builders.filler.FillerPatterns;
import ports.buildcraft.builders.filler.Pattern;
import ports.buildcraft.builders.filler.PatternParameterAxis;
import ports.buildcraft.builders.filler.PatternParameterCenter;
import ports.buildcraft.builders.filler.PatternParameterFacing;
import ports.buildcraft.builders.filler.PatternParameterHollow;
import ports.buildcraft.builders.filler.PatternParameterRotation;
import ports.buildcraft.builders.filler.PatternParameterXZDir;
import ports.buildcraft.builders.filler.PatternParameterYDir;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionProvider;
import ports.buildcraft.statement.StatementManager;

/**
 * Upstream {@code BCBuildersStatements} and {@code BCBuildersActionProvider}. The filler planner addon is not ported,
 * so only a filler offers its patterns as gate actions. Pattern parameters are registered the way upstream registered
 * them, so a gate can save them.
 */
public final class BuildersStatements {
    private BuildersStatements() {}

    public static void init() {
        StatementManager.registerParameter(PatternParameterAxis::readFromNbt);
        StatementManager.registerParameter(PatternParameterCenter::readFromNbt);
        StatementManager.registerParameter(PatternParameterFacing::readFromNbt);
        StatementManager.registerParameter(PatternParameterHollow::readFromNbt);
        StatementManager.registerParameter(PatternParameterRotation::readFromNbt);
        StatementManager.registerParameter(PatternParameterXZDir::readFromNbt);
        StatementManager.registerParameter(PatternParameterYDir::readFromNbt);
        StatementManager.registerActionProvider(BuildersActionProvider.INSTANCE);
        FillerPatterns.PATTERNS.size();
    }

    enum BuildersActionProvider implements ActionProvider {
        INSTANCE;

        @Override
        public void addExternalActions(Collection<Action.External> actions, Direction side, BlockEntity tile) {
            if (tile instanceof FillerBlockEntity) {
                for (Pattern pattern : FillerPatterns.PATTERNS) actions.add(pattern);
            }
        }
    }
}
