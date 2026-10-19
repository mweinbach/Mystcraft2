package ports.jurassicraft.entity;

import net.minecraft.world.level.GameRules;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;

/** The three original dinosaur gamerules, registered before worlds are created. */
public final class DinosaurGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> METABOLISM = GameRuleRegistry.register(
            "dinoMetabolism", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanValue> GROWTH = GameRuleRegistry.register(
            "dinoGrowth", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanValue> KILL_HERD_OUTCAST = GameRuleRegistry.register(
            "killHerdOutcast", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));
    private DinosaurGameRules() {}
    static void init() { /* force registration during mod construction */ }
}
