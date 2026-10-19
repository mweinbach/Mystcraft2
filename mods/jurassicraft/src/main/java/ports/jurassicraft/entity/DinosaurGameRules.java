package ports.jurassicraft.entity;

import net.minecraft.world.level.GameRules;

/** The three original dinosaur gamerules, registered before worlds are created. */
public final class DinosaurGameRules {
    public static final GameRules.Key<GameRules.BooleanValue> METABOLISM = GameRules.register(
            "dinoMetabolism", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> GROWTH = GameRules.register(
            "dinoGrowth", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> KILL_HERD_OUTCAST = GameRules.register(
            "killHerdOutcast", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    private DinosaurGameRules() {}
    static void init() { /* force registration during mod construction */ }
}
