package ports.buildcraft.builders.filler;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import ports.buildcraft.BuildCraft;

/**
 * Upstream {@code Pattern} with {@code IFillerPatternShape} and the {@code BCStatement} parts patterns use:
 * a filler shape saved as {@code buildcraft:<tag>}, its parameters' defaults, and how it fills a box.
 */
public abstract class Pattern {
    public final String tag;

    protected Pattern(String tag) {
        this.tag = tag;
    }

    public String uniqueTag() {
        return BuildCraft.MOD_ID + ":" + tag;
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
