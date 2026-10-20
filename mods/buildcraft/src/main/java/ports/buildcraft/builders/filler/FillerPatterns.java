package ports.buildcraft.builders.filler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Upstream {@code BCBuildersStatements}' patterns and {@code FillerStatementContext}'s groups. Upstream's
 * horizon and flatten patterns were disabled as broken, so they are not registered here either.
 */
public final class FillerPatterns {
    public static final Pattern NONE = new SimplePatterns.None();
    public static final Pattern CLEAR = new SimplePatterns.Clear();
    public static final Pattern FILL = new SimplePatterns.Fill();
    public static final Pattern BOX = new SimplePatterns.Box();
    public static final Pattern FRAME = new SimplePatterns.Frame();
    public static final Pattern PYRAMID = new PatternPyramid();
    public static final Pattern STAIRS = new PatternStairs();
    public static final Pattern TRIANGLE = new PatternShape2d.Triangle();
    public static final Pattern SQUARE = new PatternShape2d.Square();
    public static final Pattern PENTAGON = new PatternShape2d.Pentagon();
    public static final Pattern HEXAGON = new PatternShape2d.Hexagon();
    public static final Pattern OCTAGON = new PatternShape2d.Octagon();
    public static final Pattern ARC = new PatternShape2d.Arc();
    public static final Pattern SEMI_CIRCLE = new PatternShape2d.SemiCircle();
    public static final Pattern CIRCLE = new PatternShape2d.Circle();
    public static final Pattern SPHERE = new PatternSphere();
    public static final Pattern HEMI_SPHERE = new PatternSphere.Part(PatternSphere.SpherePartType.HALF);
    public static final Pattern QUARTER_SPHERE = new PatternSphere.Part(PatternSphere.SpherePartType.QUARTER);
    public static final Pattern EIGHTH_SPHERE = new PatternSphere.Part(PatternSphere.SpherePartType.EIGHTH);

    /** Upstream {@code BCBuildersStatements.PATTERNS}, which is also every pattern's {@code getPossible}. */
    public static final List<Pattern> PATTERNS = List.of(
            NONE, CLEAR, FILL, BOX, FRAME, PYRAMID, STAIRS,
            TRIANGLE, SQUARE, PENTAGON, HEXAGON, OCTAGON, ARC, SEMI_CIRCLE, CIRCLE,
            SPHERE, HEMI_SPHERE, QUARTER_SPHERE, EIGHTH_SPHERE);

    private static final Map<String, Pattern> BY_TAG = PATTERNS.stream().collect(Collectors.toMap(Pattern::uniqueTag, Function.identity()));

    /** Upstream {@code FillerStatementContext}: 2D shapes apart from the rest, each sorted by tag, "none" first. */
    public static final List<List<Pattern>> GROUPS = List.of(group(false), group(true));

    private FillerPatterns() {}

    private static List<Pattern> group(boolean shapes2d) {
        List<Pattern> group = new ArrayList<>(PATTERNS.stream().filter(p -> p instanceof PatternShape2d == shapes2d).toList());
        group.sort(Comparator.comparing(Pattern::uniqueTag));
        if (group.remove(NONE)) group.add(0, NONE);
        return List.copyOf(group);
    }

    @Nullable
    public static Pattern byTag(String uniqueTag) {
        return BY_TAG.get(uniqueTag);
    }

    public static int indexOf(Pattern pattern) {
        return PATTERNS.indexOf(pattern);
    }
}
