package ports.jurassicraft.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Independent default-relative pose passes, adapted from the original dinosaur animation handler. */
public final class DinosaurPoseEngine {
    public record Transform(float[] position, float[] rotation, float[] scale) {}
    public record Frame(float time, Map<String, Transform> parts) {}
    public record Input(String action, int actionStart, float actionElapsed, String movement,
                        boolean moving, float amount, boolean inFluid, boolean onLand, boolean carcass) {}

    private static final Set<String> HOLD = Set.of("SLEEPING", "RESTING", "DYING", "GLIDING", "LEAP", "LEAP_LAND");
    private static final Set<String> LINEAR = Set.of("IDLE", "WALKING", "RUNNING", "SWIMMING", "CLIMBING", "ON_LAND", "DYING", "LEAP_LAND");
    // A long period outside the camera must not allocate thousands of intermediate gait poses.
    private static final float MAX_MOVEMENT_CATCH_UP = 8;
    private final Map<String, Transform> base;
    private final Map<String, List<Frame>> animations;
    private final boolean marine;

    public DinosaurPoseEngine(Map<String, Transform> base, Map<String, List<Frame>> animations, boolean marine) {
        this.base = base;
        this.animations = animations;
        this.marine = marine;
    }
    public State newState() { return new State(); }

    /** A later hit interrupts an earlier action, but an action started after a hit takes precedence. */
    public static boolean injuryInterrupts(int injuryStart, int actionStart) {
        return injuryStart >= actionStart;
    }
    public final class State {
        private final Pass action = new Pass(false);
        private final Pass movement = new Pass(true);
        private final Pass land = new Pass(true);
        private final Map<String, Transform> output = new HashMap<>();
        private float lastTime = Float.NaN;
        private int actionStart = Integer.MIN_VALUE;
        private String actionName = "";

        private State() {
            for (var entry : base.entrySet()) {
                Transform value = entry.getValue();
                output.put(entry.getKey(), new Transform(value.position().clone(), value.rotation().clone(), value.scale().clone()));
            }
        }
    }

    /** Returns a reusable pose owned by the state, which must never retain its entity. */
    public Map<String, Transform> sample(State state, Input input, float time) {
        boolean first = Float.isNaN(state.lastTime) || time < state.lastTime;
        float elapsed = first ? 0 : time - state.lastTime;
        state.lastTime = time;
        if (first) {
            state.action.clear();
            state.movement.clear();
            state.land.clear();
        }
        String action = supported(input.action());
        boolean restart = first || !action.equals(state.actionName) || state.actionStart != input.actionStart();
        state.actionName = action;
        state.actionStart = input.actionStart();
        float degree = Math.max(input.moving() ? .5f : 0, Math.min(1, input.amount() * (input.inFluid() ? 4 : 1)));
        float movementSpeed = input.moving() ? degree : 3;
        state.action.select(action, 1, restart);
        state.movement.select(supported(input.movement()), degree, first);
        state.land.select(supported(input.onLand() ? "ON_LAND" : "IDLE"), 1, first);
        if (first && HOLD.contains(action)) {
            // A newly tracked sleeper, glider, or carcass is already in its held pose.
            state.action.holdLast();
        } else if (restart && input.actionElapsed() > 0 && !action.equals("IDLE")) {
            // Persisted or temporarily interrupted actions can resume midway through a sequence.
            state.action.advance(input.actionElapsed(), 1);
        } else if (!restart) {
            state.action.advance(elapsed, 1);
        }
        if (!input.carcass()) {
            state.movement.advance(Math.min(elapsed * movementSpeed, MAX_MOVEMENT_CATCH_UP), 1);
            if (marine) state.land.advance(Math.min(elapsed, MAX_MOVEMENT_CATCH_UP), 1);
        }
        float actionFactor = state.action.factor();
        float movementFactor = state.movement.factor();
        float landFactor = state.land.factor();
        for (var entry : base.entrySet()) {
            String id = entry.getKey();
            Transform baseline = entry.getValue();
            Transform output = state.output.get(id);
            float[] position = output.position();
            float[] rotation = output.rotation();
            System.arraycopy(baseline.position(), 0, position, 0, 3);
            System.arraycopy(baseline.rotation(), 0, rotation, 0, 3);
            state.action.addTo(id, position, rotation, actionFactor);
            if (!input.carcass()) {
                state.movement.addTo(id, position, rotation, movementFactor);
                if (marine) state.land.addTo(id, position, rotation, landFactor);
            }
        }
        return state.output;
    }
    private String supported(String name) {
        return name != null && animations.containsKey(name) && !animations.get(name).isEmpty() ? name : "IDLE";
    }

    private final class Pass {
        private final boolean loop;
        private String name = "";
        private List<Frame> frames;
        private int index;
        private float progress;
        private boolean finished;
        private Map<String, float[]> from = new HashMap<>();
        private Map<String, float[]> to = new HashMap<>();
        private Pass(boolean loop) { this.loop = loop; }

        private void clear() {
            frames = null;
            name = "";
            from.clear();
            to.clear();
        }

        private void holdLast() {
            index = frames.size() - 1;
            from = new HashMap<>();
            target(1);
            from = to;
            progress = Math.max(1, frames.get(index).time());
            finished = true;
        }

        private void select(String requested, float degree, boolean restart) {
            currentDegree = degree;
            if (!restart && requested.equals(name)) return;
            Map<String, float[]> current = current();
            name = requested;
            frames = animations.get(name);
            index = 0;
            progress = 0;
            finished = false;
            from = current;
            target(loop && (name.equals("WALKING") || name.equals("RUNNING") || name.equals("SWIMMING") || name.equals("CLIMBING"))
                    ? degree : 1);
        }
        private void target(float degree) {
            Map<String, Transform> pose = frames.get(index).parts();
            Map<String, float[]> next = new HashMap<>();
            for (var entry : base.entrySet()) {
                String id = entry.getKey();
                Transform original = entry.getValue();
                Transform target = pose.getOrDefault(id, original);
                float[] previous = from.getOrDefault(id, new float[6]);
                float[] value = new float[6];
                for (int i = 0; i < 3; i++) {
                    value[i] = previous[i] + (target.position()[i] - original.position()[i] - previous[i]) * degree;
                    value[i + 3] = previous[i + 3] + (target.rotation()[i] - original.rotation()[i] - previous[i + 3]) * degree;
                }
                next.put(id, value);
            }
            to = next;
        }
        private float factor() {
            float factor = Math.max(0, Math.min(1, progress / Math.max(1, frames.get(index).time())));
            return LINEAR.contains(name) ? factor : (float) (Math.sin(Math.PI * (factor - .5)) * .5 + .5);
        }
        private float[] offset(String id) {
            float[] a = from.get(id), b = to.get(id);
            if (a == null || b == null) return null;
            float factor = factor();
            float[] result = new float[6];
            for (int i = 0; i < result.length; i++) result[i] = a[i] + (b[i] - a[i]) * factor;
            return result;
        }
        private void addTo(String id, float[] position, float[] rotation, float factor) {
            float[] a = from.get(id), b = to.get(id);
            if (a == null || b == null) return;
            for (int i = 0; i < 3; i++) {
                position[i] += a[i] + (b[i] - a[i]) * factor;
                rotation[i] += a[i + 3] + (b[i + 3] - a[i + 3]) * factor;
            }
        }
        private Map<String, float[]> current() {
            Map<String, float[]> result = new HashMap<>();
            for (String id : base.keySet()) result.put(id, frames == null ? new float[6] : offset(id));
            return result;
        }
        private void advance(float elapsed, float speed) {
            if (finished || elapsed <= 0 || speed <= 0) return;
            float remaining = elapsed * speed;
            int limit = 0;
            while (remaining > 0 && !finished && limit++ < 1024) {
                float duration = Math.max(1, frames.get(index).time());
                float step = Math.min(remaining, duration - progress);
                progress += step;
                remaining -= step;
                if (progress < duration) break;
                from = current();
                if (++index >= frames.size()) {
                    if (HOLD.contains(name)) { index = frames.size() - 1; finished = true; break; }
                    if (!loop) { index = frames.size() - 1; finished = true; break; }
                    index = 0;
                }
                progress = 0;
                // In the upstream movement pass, degree is sampled when a new frame begins.
                target(loop && (name.equals("WALKING") || name.equals("RUNNING") || name.equals("SWIMMING") || name.equals("CLIMBING"))
                        ? currentDegree : 1);
            }
        }
        private float currentDegree = 1;
    }
}
