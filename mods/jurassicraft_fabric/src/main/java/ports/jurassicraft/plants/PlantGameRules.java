package ports.jurassicraft.plants;

import net.minecraft.world.level.GameRules;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.Level;

/** The original gamerule controls voluntary ancient-plant and coral spreading. */
public final class PlantGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> PLANT_SPREADING = GameRuleRegistry.register(
            "plantSpreading", GameRules.Category.UPDATES, GameRuleFactory.createBooleanRule(true));
    private PlantGameRules() {}
    public static void init() { /* force registration during mod construction */ }
    static boolean canSpread(Level level) { return level.getGameRules().getBoolean(PLANT_SPREADING); }
}
