package ports.buildcraft.statement;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.BuildCraft;

/**
 * Upstream {@code BCStatement}: registers itself under every tag it is given. The first tag is the one
 * saved; the others are upstream's older names, still read.
 */
public abstract class BCStatement implements Statement {
    protected final String uniqueTag;

    protected BCStatement(String... uniqueTags) {
        this.uniqueTag = uniqueTags[0];
        for (String tag : uniqueTags) StatementManager.registerStatement(tag, this);
    }

    /** The texture of one of upstream's trigger and action icons, copied to {@code textures/triggers}. */
    protected static ResourceLocation sprite(String name) {
        return BuildCraft.id("textures/triggers/" + name + ".png");
    }

    @Override
    public String getUniqueTag() {
        return uniqueTag;
    }

    @Override
    public int maxParameters() {
        return 0;
    }

    @Override
    public int minParameters() {
        return 0;
    }

    @Override
    public Statement rotateLeft() {
        return this;
    }

    @Override
    public Statement[] getPossible() {
        return new Statement[] {this};
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return null;
    }

    /** The parameter at {@code index} if it is the same kind as {@code fallback}, otherwise {@code fallback}. */
    @SuppressWarnings("unchecked")
    protected static <P extends StatementParameter> P getParam(int index, @Nullable StatementParameter[] params, P fallback) {
        if (params == null || params.length <= index) return fallback;
        StatementParameter atIndex = params[index];
        return atIndex != null && atIndex.getClass() == fallback.getClass() ? (P) atIndex : fallback;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + uniqueTag + "]";
    }
}
