package ports.jurassicraft.decor;

import java.util.List;

/** Stable artwork identifiers, corresponding to the original sign textures. */
public final class ParkSigns {
    public static final List<String> ATTRACTIONS = List.of("aquarium", "aquarium_coral", "aviary", "aviary_plants",
            "gallimimus_valley", "gallimimus_valley_plants", "gentle_giants", "gentle_giants_plants",
            "raptor_paddock", "raptor_paddock_plants", "safari", "safari_plants", "triceratops_territorium",
            "garden", "garden_plants", "triceratops_territorium_plants", "tyrannosaurus_kingdom", "tyrannosaurus_kingdom_plants");
    public static final List<String> PADDOCKS = List.of("velociraptor", "coelacanth", "microraptor", "brachiosaurus",
            "mussaurus", "dilophosaurus", "gallimimus", "parasaurolophus", "triceratops", "tyrannosaurus");
    private ParkSigns() {}
    public static String validate(boolean attraction, String id) {
        List<String> allowed = attraction ? ATTRACTIONS : PADDOCKS;
        return allowed.contains(id) ? id : allowed.get(0);
    }
}
