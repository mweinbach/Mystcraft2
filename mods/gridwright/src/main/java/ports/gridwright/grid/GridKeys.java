package ports.gridwright.grid;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.UUID;

/** Grid keys: a player's own grid, or their scoreboard team's shared grid while they are on one. */
public final class GridKeys {

    private GridKeys() { }

    /** The grid of the player {@code id} called {@code name}; an empty name never matches a team. */
    public static String of(MinecraftServer server, UUID id, String name) {
        PlayerTeam team = name.isEmpty() ? null : server.getScoreboard().getPlayersTeam(name);
        return team != null ? "team:" + team.getName() : "player:" + id;
    }
}
