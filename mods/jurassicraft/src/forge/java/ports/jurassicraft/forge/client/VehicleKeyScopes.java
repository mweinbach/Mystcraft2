package ports.jurassicraft.forge.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.KeyMapping;

/**
 * Bindings that are active only while riding. {@code KeyMapping.same} still compares the physical
 * key, so a held flight binding can suppress jump and sprint. The controls-screen mixin uses this
 * set to skip the red warning against an ordinary in-game binding.
 */
public final class VehicleKeyScopes {
    private static final Set<KeyMapping> SCOPED = Collections.newSetFromMap(new IdentityHashMap<>());

    private VehicleKeyScopes() {}

    public static void mark(KeyMapping mapping) { SCOPED.add(mapping); }

    /** True when exactly one binding is limited to riding a vehicle. */
    public static boolean separated(KeyMapping first, KeyMapping second) {
        return SCOPED.contains(first) != SCOPED.contains(second);
    }
}
