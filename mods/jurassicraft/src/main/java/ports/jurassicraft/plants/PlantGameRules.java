package ports.jurassicraft.plants;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/** The original gamerule controls voluntary ancient-plant and coral spreading. */
public final class PlantGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> PLANT_SPREADING = GameRules.register(
            "plantSpreading", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true));
    private PlantGameRules() {}
    public static void init() { /* force registration during mod construction */ }
    static boolean canSpread(Level level) { return level.getGameRules().getBoolean(PLANT_SPREADING); }
}
