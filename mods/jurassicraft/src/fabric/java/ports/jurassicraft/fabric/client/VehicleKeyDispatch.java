package ports.jurassicraft.fabric.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Routing for a key event that vanilla has already delivered. Vanilla gives each physical key to a
 * single owner mapping, so a vehicle binding that shares Space or Left Ctrl with jump or sprint would
 * otherwise never see the key (or, if it owns the key, take it from jump or sprint).
 *
 * <p>Only vehicle-scoped mappings gain extra deliveries. Ordinary bindings that share a key keep
 * vanilla's one-owner behaviour, so unrelated mods' conflicting keys are not all fired.
 */
public final class VehicleKeyDispatch {
    private VehicleKeyDispatch() {}

    /**
     * The mappings that receive the event in addition to {@code owner}.
     *
     * @param owner  the mapping vanilla delivered the event to, or null when none owns the key
     * @param bound  every mapping bound to the key, in vanilla's registration iteration order
     * @param scoped whether a mapping is active only while riding a vehicle
     */
    public static <M> List<M> extraRecipients(@Nullable M owner, List<M> bound, Predicate<? super M> scoped) {
        List<M> extra = new ArrayList<>();
        M ordinary = null;
        for (M mapping : bound) {
            if (scoped.test(mapping)) {
                if (mapping != owner) extra.add(mapping);
            } else {
                // Vanilla rebuilds the key table in this order, so the last ordinary binding is the one it picks.
                ordinary = mapping;
            }
        }
        // A vehicle binding took the key from the ordinary binding vanilla would have chosen; give it back.
        if (owner != null && scoped.test(owner) && ordinary != null) extra.add(ordinary);
        return extra;
    }
}
