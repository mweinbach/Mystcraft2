package ports.jurassicraft.fabric.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.KeyMapping;

/**
 * Bindings that are active only while riding. Fabric has no conflict-context API, so the controls
 * screen mixin uses this set to skip the red warning against an ordinary in-game binding, and the
 * key-dispatch mixin delivers shared-key events to these bindings (see {@link VehicleKeyDispatch}).
 */
public final class VehicleKeyScopes {
    private static final Set<KeyMapping> SCOPED = Collections.newSetFromMap(new IdentityHashMap<>());

    private VehicleKeyScopes() {}

    public static void mark(KeyMapping mapping) { SCOPED.add(mapping); }

    public static boolean isScoped(KeyMapping mapping) { return SCOPED.contains(mapping); }

    /** True when exactly one binding is limited to riding a vehicle. */
    public static boolean separated(KeyMapping first, KeyMapping second) {
        return SCOPED.contains(first) != SCOPED.contains(second);
    }
}
