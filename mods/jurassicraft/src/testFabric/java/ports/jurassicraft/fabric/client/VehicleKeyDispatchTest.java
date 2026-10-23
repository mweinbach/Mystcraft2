package ports.jurassicraft.fabric.client;

import java.util.List;
import java.util.Set;

/** Standalone checks of which bindings receive a shared-key event; run without a game client. */
public final class VehicleKeyDispatchTest {
    private static final String JUMP = "key.jump", SPRINT = "key.sprint", OTHER_MOD = "key.othermod.jetpack";
    private static final String ASCEND = "key.jurassicraft.helicopter_ascend", DESCEND = "key.jurassicraft.helicopter_descend";
    private static final String ROTATE = "key.jurassicraft.helicopter_rotate_left";
    private static final Set<String> SCOPED = Set.of(ASCEND, DESCEND, ROTATE);

    public static void main(String[] args) {
        // Vanilla gave Space to jump: the helicopter binding on Space also hears it.
        check(List.of(ASCEND), JUMP, List.of(JUMP, ASCEND), "vehicle binding sharing a vanilla key");
        check(List.of(DESCEND), SPRINT, List.of(DESCEND, SPRINT), "order of the bound list does not matter");
        // The helicopter binding owns Space: jump still works.
        check(List.of(JUMP), ASCEND, List.of(JUMP, ASCEND), "vanilla binding regains a key a vehicle binding owns");
        // With several ordinary bindings, only the one vanilla itself would pick (the last) gets it back.
        check(List.of(JUMP), ASCEND, List.of(OTHER_MOD, ASCEND, JUMP), "only vanilla's own choice regains the key");
        check(List.of(OTHER_MOD), ASCEND, List.of(JUMP, ASCEND, OTHER_MOD), "last ordinary binding is vanilla's choice");
        // Ordinary conflicts keep vanilla's single-owner behaviour: no game-wide fan-out.
        check(List.of(), JUMP, List.of(JUMP, OTHER_MOD), "conflicting ordinary bindings are not all fired");
        check(List.of(ASCEND), JUMP, List.of(JUMP, OTHER_MOD, ASCEND), "other mods' conflicts stay untouched");
        // Several vehicle bindings on one key each hear it once; the owner never twice.
        check(List.of(ASCEND, ROTATE), JUMP, List.of(JUMP, ASCEND, ROTATE), "every vehicle binding on the key");
        check(List.of(ROTATE, JUMP), ASCEND, List.of(JUMP, ASCEND, ROTATE), "owner excluded, vanilla restored");
        check(List.of(), ASCEND, List.of(ASCEND), "a lone binding is vanilla's business");
        check(List.of(), null, List.of(), "an unbound key reaches nobody");
        check(List.of(), JUMP, List.of(JUMP), "a lone vanilla binding is untouched");
        System.out.println("VehicleKeyDispatchTest passed");
    }

    private static void check(List<String> expected, String owner, List<String> bound, String message) {
        List<String> actual = VehicleKeyDispatch.extraRecipients(owner, bound, SCOPED::contains);
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
}
