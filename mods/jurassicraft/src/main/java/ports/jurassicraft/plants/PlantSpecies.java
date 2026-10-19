package ports.jurassicraft.plants;

import java.util.Locale;

/** Stable species identities; declaration order follows the 1.12.2 PlantHandler. */
public enum PlantSpecies {
    AJUGINUCULA_SMITHII, SMALL_ROYAL_FERN, CALAMITES(true), SMALL_CHAIN_FERN,
    SMALL_CYCAD, GINKGO(true), BENNETTITALEAN_CYCADEOIDEA, CRY_PANSY,
    SCALY_TREE_FERN, CYCAD_ZAMITES, DICKSONIA, WILD_ONION,
    DICROIDIUM_ZUBERI, DICTYOPHYLLUM, WEST_INDIAN_LILAC(false, false),
    SERENNA_VERIFORMANS, LADINIA_SIMPLEX, ORONTIUM_MACKII, UMALTOLEPIS,
    LIRIODENDRITES, RAPHAELIA, ENCEPHALARTOS, PSARONIUS(true), PHOENIX(true),
    WILD_POTATO, ARAUCARIA(true), CINNAMON_FERN, BRISTLE_FERN, TEMPSKYA,
    WOOLLY_STALKED_BEGONIA, LARGESTIPULE_LEATHER_ROOT, RHACOPHYTON,
    GRAMINIDITES_BAMBUSOIDES, ENALLHELIA, AULOPORA, CLADOCHONUS,
    LITHOSTROTION, STYLOPHYLLOPSIS, HIPPURITES_RADIOSUS,
    HELICONIA(false, false), RHAMNUS_SALIFOCIFUS;

    private final boolean tree;
    private final boolean prehistoric;

    PlantSpecies() { this(false, true); }
    PlantSpecies(boolean tree) { this(tree, true); }
    PlantSpecies(boolean tree, boolean prehistoric) {
        this.tree = tree;
        this.prehistoric = prehistoric;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public boolean isTree() { return tree; }
    public boolean isPrehistoric() { return prehistoric; }
}
