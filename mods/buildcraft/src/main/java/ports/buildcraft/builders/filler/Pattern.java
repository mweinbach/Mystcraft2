package ports.buildcraft.builders.filler;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.FillerBlockEntity;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.BCStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/**
 * Upstream {@code Pattern}: a filler shape, a gate action that sets it on a neighbouring filler, and how it fills a
 * box. Saved as {@code buildcraft:<tag>}.
 */
public abstract class Pattern extends BCStatement implements Action.External {
    public final String tag;

    protected Pattern(String tag) {
        super(BuildCraft.MOD_ID + ":" + tag);
        this.tag = tag;
    }

    public String uniqueTag() {
        return getUniqueTag();
    }

    @Override
    public Component getDescription() {
        return Component.translatable(translationKey());
    }

    @Override
    public ResourceLocation getSprite() {
        return sprite();
    }

    /**
     * Upstream sets the pattern on a statement container that is itself a filler, and otherwise on the neighbouring
     * block. The filler planner addon is not ported, so the neighbour is the filler a gate acts on.
     */
    @Override
    public void actionActivate(BlockEntity target, Direction side, StatementContainer source, StatementParameter[] parameters) {
        BlockEntity acted = source.getTile() instanceof FillerBlockEntity ? source.getTile() : target;
        if (acted instanceof FillerBlockEntity filler) filler.setPattern(this, parameters);
    }

    @Override
    public Statement[] getPossible() {
        return FillerPatterns.PATTERNS.toArray(Statement[]::new);
    }

    public String translationKey() {
        return "fillerpattern." + tag;
    }

    public ResourceLocation sprite() {
        return BuildCraft.id("textures/filler/patterns/" + tag + ".png");
    }

    public int minParameters() {
        return 0;
    }

    public int maxParameters() {
        return 0;
    }

    @Nullable
    public FillerParameter createParameter(int index) {
        return null;
    }

    /** Upstream {@code IStatement.createParameter(old, index)}: keeps a parameter of the right type. */
    @Nullable
    public final FillerParameter createParameter(@Nullable FillerParameter old, int index) {
        FillerParameter created = createParameter(index);
        if (old == null || created == null) return created;
        return old.getClass() == created.getClass() ? old : created;
    }

    /** @return false if no template should be made for these parameters */
    protected abstract boolean fillTemplate(FilledTemplate template, FillerParameter[] params);

    /** Upstream {@code IFillerPatternShape.createTemplate}: an empty template of the box's size, filled. */
    @Nullable
    public FilledTemplate createTemplate(BlockPos size, FillerParameter[] params) {
        FilledTemplate template = new FilledTemplate(size);
        return fillTemplate(template, params) ? template : null;
    }

    /** Upstream {@code BCStatement.getParam}: the parameter at {@code index} if it has the default's type. */
    @SuppressWarnings("unchecked")
    protected static <P extends FillerParameter> P getParam(int index, FillerParameter[] params, P fallback) {
        if (params == null || params.length <= index) return fallback;
        FillerParameter atIndex = params[index];
        return atIndex != null && atIndex.getClass() == fallback.getClass() ? (P) atIndex : fallback;
    }

    @Override
    public String toString() {
        return uniqueTag();
    }
}
