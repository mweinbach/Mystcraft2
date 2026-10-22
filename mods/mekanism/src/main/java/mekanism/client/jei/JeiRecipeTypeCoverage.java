package mekanism.client.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registration.impl.RecipeTypeRegistryObject;

/**
 * The recipe types {@link MekanismJEI} registers a category for. {@link #direct()} is what the plugin registers.
 * Rotary is one recipe type and two categories, so it is covered by name and registered on its own.
 * Boiler, SPS and the nutritional liquifier are JEI-only lists, not {@link MekanismRecipeType} entries.
 */
public final class JeiRecipeTypeCoverage {

    public enum Covered {
        CRUSHING("crushing"),
        ENRICHING("enriching"),
        SMELTING("smelting"),
        CHEMICAL_INFUSING("chemical_infusing"),
        COMBINING("combining"),
        SEPARATING("separating"),
        WASHING("washing"),
        EVAPORATING("evaporating"),
        ACTIVATING("activating"),
        CENTRIFUGING("centrifuging"),
        CRYSTALLIZING("crystallizing"),
        DISSOLUTION("dissolution"),
        COMPRESSING("compressing"),
        PURIFYING("purifying"),
        INJECTING("injecting"),
        NUCLEOSYNTHESIZING("nucleosynthesizing"),
        ENERGY_CONVERSION("energy_conversion"),
        GAS_CONVERSION("gas_conversion"),
        OXIDIZING("oxidizing"),
        INFUSION_CONVERSION("infusion_conversion"),
        PIGMENT_EXTRACTING("pigment_extracting"),
        PIGMENT_MIXING("pigment_mixing"),
        METALLURGIC_INFUSING("metallurgic_infusing"),
        PAINTING("painting"),
        REACTION("reaction"),
        ROTARY("rotary"),
        SAWING("sawing");

        private final String registryPath;

        Covered(String registryPath) {
            this.registryPath = registryPath;
        }

        public String registryPath() {
            return registryPath;
        }
    }

    public record Binding<RECIPE extends MekanismRecipe>(MekanismJEIRecipeType<RECIPE> category, RecipeTypeRegistryObject<RECIPE, ?> recipeType) {
    }

    private JeiRecipeTypeCoverage() {
    }

    public static Set<String> coveredRecipeTypeNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Covered covered : Covered.values()) {
            names.add(covered.registryPath());
        }
        return names;
    }

    public static List<Binding<?>> direct() {
        List<Binding<?>> bindings = new ArrayList<>();
        for (Covered covered : Covered.values()) {
            Binding<?> binding = binding(covered);
            if (binding != null) {
                bindings.add(binding);
            }
        }
        return bindings;
    }

    private static Binding<?> binding(Covered covered) {
        return switch (covered) {
            case CRUSHING -> bind(MekanismJEIRecipeType.CRUSHING, MekanismRecipeType.CRUSHING);
            case ENRICHING -> bind(MekanismJEIRecipeType.ENRICHING, MekanismRecipeType.ENRICHING);
            case SMELTING -> bind(MekanismJEIRecipeType.SMELTING, MekanismRecipeType.SMELTING);
            case CHEMICAL_INFUSING -> bind(MekanismJEIRecipeType.CHEMICAL_INFUSING, MekanismRecipeType.CHEMICAL_INFUSING);
            case COMBINING -> bind(MekanismJEIRecipeType.COMBINING, MekanismRecipeType.COMBINING);
            case SEPARATING -> bind(MekanismJEIRecipeType.SEPARATING, MekanismRecipeType.SEPARATING);
            case WASHING -> bind(MekanismJEIRecipeType.WASHING, MekanismRecipeType.WASHING);
            case EVAPORATING -> bind(MekanismJEIRecipeType.EVAPORATING, MekanismRecipeType.EVAPORATING);
            case ACTIVATING -> bind(MekanismJEIRecipeType.ACTIVATING, MekanismRecipeType.ACTIVATING);
            case CENTRIFUGING -> bind(MekanismJEIRecipeType.CENTRIFUGING, MekanismRecipeType.CENTRIFUGING);
            case CRYSTALLIZING -> bind(MekanismJEIRecipeType.CRYSTALLIZING, MekanismRecipeType.CRYSTALLIZING);
            case DISSOLUTION -> bind(MekanismJEIRecipeType.DISSOLUTION, MekanismRecipeType.DISSOLUTION);
            case COMPRESSING -> bind(MekanismJEIRecipeType.COMPRESSING, MekanismRecipeType.COMPRESSING);
            case PURIFYING -> bind(MekanismJEIRecipeType.PURIFYING, MekanismRecipeType.PURIFYING);
            case INJECTING -> bind(MekanismJEIRecipeType.INJECTING, MekanismRecipeType.INJECTING);
            case NUCLEOSYNTHESIZING -> bind(MekanismJEIRecipeType.NUCLEOSYNTHESIZING, MekanismRecipeType.NUCLEOSYNTHESIZING);
            case ENERGY_CONVERSION -> bind(MekanismJEIRecipeType.ENERGY_CONVERSION, MekanismRecipeType.ENERGY_CONVERSION);
            case GAS_CONVERSION -> bind(MekanismJEIRecipeType.GAS_CONVERSION, MekanismRecipeType.GAS_CONVERSION);
            case OXIDIZING -> bind(MekanismJEIRecipeType.OXIDIZING, MekanismRecipeType.OXIDIZING);
            case INFUSION_CONVERSION -> bind(MekanismJEIRecipeType.INFUSION_CONVERSION, MekanismRecipeType.INFUSION_CONVERSION);
            case PIGMENT_EXTRACTING -> bind(MekanismJEIRecipeType.PIGMENT_EXTRACTING, MekanismRecipeType.PIGMENT_EXTRACTING);
            case PIGMENT_MIXING -> bind(MekanismJEIRecipeType.PIGMENT_MIXING, MekanismRecipeType.PIGMENT_MIXING);
            case METALLURGIC_INFUSING -> bind(MekanismJEIRecipeType.METALLURGIC_INFUSING, MekanismRecipeType.METALLURGIC_INFUSING);
            case PAINTING -> bind(MekanismJEIRecipeType.PAINTING, MekanismRecipeType.PAINTING);
            case REACTION -> bind(MekanismJEIRecipeType.REACTION, MekanismRecipeType.REACTION);
            case ROTARY -> null;
            case SAWING -> bind(MekanismJEIRecipeType.SAWING, MekanismRecipeType.SAWING);
        };
    }

    private static <RECIPE extends MekanismRecipe> Binding<RECIPE> bind(MekanismJEIRecipeType<RECIPE> category,
          RecipeTypeRegistryObject<RECIPE, ?> recipeType) {
        return new Binding<>(category, recipeType);
    }
}
