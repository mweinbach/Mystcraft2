package ports.buildcraft.silicon;

/**
 * Upstream {@code EnumAssemblyRecipeState}: a recipe the inputs allow, one the player saved, a saved one the inputs
 * allow, and the saved one being assembled.
 */
public enum EnumAssemblyRecipeState {
    POSSIBLE,
    SAVED,
    SAVED_ENOUGH,
    SAVED_ENOUGH_ACTIVE
}
