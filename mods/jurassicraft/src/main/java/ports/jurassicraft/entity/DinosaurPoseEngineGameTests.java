package ports.jurassicraft.entity;

import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.client.DinosaurPoseEngine;
import ports.jurassicraft.client.DinosaurPoseEngine.Frame;
import ports.jurassicraft.client.DinosaurPoseEngine.Input;
import ports.jurassicraft.client.DinosaurPoseEngine.Transform;

/** Deterministic pose tests can also run without launching Minecraft. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurPoseEngineGameTests {
    private DinosaurPoseEngineGameTests() {}

    private static Transform pose(float x, float yaw) {
        return new Transform(new float[] {x, 0, 0}, new float[] {0, yaw, 0}, new float[] {1, 1, 1});
    }
    private static Frame frame(float ticks, float x, float yaw) {
        return new Frame(ticks, Map.of("body", pose(x, yaw)));
    }
    private static DinosaurPoseEngine engine(boolean marine) {
        Map<String, Transform> base = Map.of("body", pose(2, 10));
        return new DinosaurPoseEngine(base, Map.of(
                "IDLE", List.of(frame(10, 2, 10)),
                "ATTACKING", List.of(frame(10, 12, 30)),
                "WALKING", List.of(frame(10, 6, 14)),
                "RUNNING", List.of(frame(10, 10, 18)),
                "SLEEPING", List.of(frame(4, 8, 10), frame(6, 14, 10)),
                "DYING", List.of(frame(10, 14, 10)),
                "LAYING_EGG", List.of(frame(10, 12, 10), frame(10, 22, 10)),
                "ON_LAND", List.of(frame(10, 8, 10))), marine);
    }
    private static Input input(String action, int start, float phase, String movement,
                               boolean moving, float amount, boolean fluid, boolean land, boolean carcass) {
        return new Input(action, start, phase, movement, moving, amount, fluid, land, carcass);
    }
    private static float x(DinosaurPoseEngine engine, DinosaurPoseEngine.State state, Input input, float time) {
        return engine.sample(state, input, time).get("body").position()[0];
    }
    private static void close(float actual, float expected, String description) {
        if (Math.abs(actual - expected) > .0001f)
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
    }
    private static void verifyBlendingAndIsolation() {
        var engine = engine(false);
        var first = engine.newState();
        var second = engine.newState();
        var attackWalk = input("ATTACKING", 1, 0, "WALKING", true, 1, false, false, false);
        close(x(engine, first, attackWalk, 0), 2, "initial pose");
        var blended = engine.sample(first, attackWalk, 5).get("body");
        close(blended.position()[0], 9, "half action and half gait add independently");
        close(blended.rotation()[1], 22, "rotation channels blend too");
        close(x(engine, second, attackWalk, 5), 2, "another dinosaur starts with its own clock");
        close(x(engine, first, attackWalk, 10), 16, "another dinosaur must not change the first pose");

        var slow = engine.newState();
        var walk = input("IDLE", 0, 0, "WALKING", true, .5f, false, false, false);
        x(engine, slow, walk, 0);
        close(x(engine, slow, walk, 10), 3, "movement degree and speed scale together");
        var fluid = engine.newState();
        var fluidWalk = input("IDLE", 0, 0, "WALKING", true, .5f, true, false, false);
        x(engine, fluid, fluidWalk, 0);
        close(x(engine, fluid, fluidWalk, 5), 4, "fluid gait scales movement degree");
    }
    private static void verifyTransitionsAndResume() {
        var engine = engine(false);
        var state = engine.newState();
        var idle = input("IDLE", 0, 0, "IDLE", false, 0, false, false, false);
        var attack = input("ATTACKING", 1, 0, "IDLE", false, 0, false, false, false);
        x(engine, state, attack, 0);
        close(x(engine, state, attack, 5), 7, "action reaches midpoint");
        close(x(engine, state, idle, 5), 7, "switching actions preserves the current pose");
        close(x(engine, state, idle, 10), 4.5f, "idle returns from the interrupted pose");
        var restarted = engine.newState();
        x(engine, restarted, attack, 0);
        x(engine, restarted, attack, 5);
        var anotherAttack = input("ATTACKING", 2, 0, "IDLE", false, 0, false, false, false);
        close(x(engine, restarted, anotherAttack, 5), 7, "new action token restarts without a jump");
        close(x(engine, restarted, anotherAttack, 10), 9.5f, "new action token advances from the interrupted pose");

        var resumed = engine.newState();
        x(engine, resumed, idle, 0);
        var birth = input("LAYING_EGG", 7, 15, "IDLE", false, 0, false, false, false);
        close(x(engine, resumed, birth, 1), 17, "interrupted birth resumes at its saved phase");
        var newlyTracked = engine.newState();
        close(x(engine, newlyTracked, birth, 100), 17, "newly tracked action starts at its saved phase");
        var afterGap = engine.newState();
        x(engine, afterGap, idle, 0);
        var startedRecently = input("ATTACKING", 298, 2, "IDLE", false, 0, false, false, false);
        float expected = 2 + 10 * (float) (Math.sin(Math.PI * (.2 - .5)) * .5 + .5);
        close(x(engine, afterGap, startedRecently, 300), expected,
                "a new action after a long visibility gap uses its own age");
        var gaitAfterGap = engine.newState();
        var walk = input("IDLE", 0, 0, "WALKING", true, 1, false, false, false);
        x(engine, gaitAfterGap, walk, 0);
        close(x(engine, gaitAfterGap, walk, 1000000), 5.2f, "gait catch-up is bounded after long culling");
        if (!DinosaurPoseEngine.injuryInterrupts(11, 10) || !DinosaurPoseEngine.injuryInterrupts(11, 11)
                || DinosaurPoseEngine.injuryInterrupts(10, 11))
            throw new AssertionError("new injury interrupts actions, but a newer action interrupts old injury");
    }
    private static void verifyHoldsFallbackAndLand() {
        var engine = engine(false);
        var idle = input("IDLE", 0, 0, "IDLE", false, 0, false, false, false);
        var sleep = input("SLEEPING", 0, 0, "IDLE", false, 0, false, false, false);
        var existing = engine.newState();
        close(x(engine, existing, sleep, 0), 14, "newly tracked sleeper begins at final pose");
        var transition = engine.newState();
        x(engine, transition, idle, 0);
        close(x(engine, transition, sleep, 0), 2, "live sleep transition begins at current pose");
        close(x(engine, transition, sleep, 2), 5, "live sleep transition plays first keyframe");
        close(x(engine, transition, sleep, 20), 14, "completed sleep remains at its final pose");
        close(x(engine, transition, sleep, 30), 14, "held pose stays stable");
        var carcass = engine.newState();
        var dead = input("DYING", 1, 0, "WALKING", true, 1, false, true, true);
        close(x(engine, carcass, dead, 0), 14, "loaded carcass starts dead without a gait");
        close(x(engine, carcass, dead, 10), 14, "carcass ignores locomotion");
        var missing = engine.newState();
        close(x(engine, missing, input("ABSENT", 1, 0, "ABSENT", true, 1, false, false, false), 0), 2,
                "unsupported animation falls back to idle");
        var fish = engine(true);
        var fishState = fish.newState();
        var onLand = input("IDLE", 0, 0, "IDLE", false, 0, false, true, false);
        x(fish, fishState, onLand, 0);
        close(x(fish, fishState, onLand, 5), 5, "coelacanth has an independent land pass");
    }

    @GameTest(template = "feeder_test", templateNamespace = PortMod.MOD_ID)
    public static void simultaneousAnimationAndIndependentStates(GameTestHelper helper) {
        verifyBlendingAndIsolation();
        helper.succeed();
    }
    @GameTest(template = "feeder_test", templateNamespace = PortMod.MOD_ID)
    public static void actionTransitionsAndResumption(GameTestHelper helper) {
        verifyTransitionsAndResume();
        helper.succeed();
    }
    @GameTest(template = "feeder_test", templateNamespace = PortMod.MOD_ID)
    public static void heldPosesFallbackAndCoelacanth(GameTestHelper helper) {
        verifyHoldsFallbackAndLand();
        helper.succeed();
    }
    public static void main(String[] args) {
        verifyBlendingAndIsolation();
        verifyTransitionsAndResume();
        verifyHoldsFallbackAndLand();
        System.out.println("Dinosaur pose-engine checks passed");
    }
}
