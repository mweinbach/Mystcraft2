package ports.gridwright.item;

import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridKeys;
import ports.gridwright.grid.GridManager;
import ports.gridwright.grid.GridSnapshot;
import ports.gridwright.registry.ModUtilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Angel Ring flight. Once per server tick every survival or adventure player carrying a ring anywhere
 * in their inventory may fly while their grid (their own, or their team's) has the ring's GP to spare.
 * The ring asks its grid for {@link #GP} only while the player is actually flying, and like any other
 * consumer it counts towards the grid's demand, so an overloaded grid grounds the flyer.
 *
 * <p>Flight the ring grants is marked with a scoreboard tag on the player, so it is taken back (after a
 * relog or restart too) when the ring leaves the inventory or the grid cannot pay, and flight another
 * mod or the game mode grants is never touched. A player who loses flight in the air gets
 * {@link #SLOW_FALL_TICKS} of Slow Falling.
 */
public final class AngelRingFlight {

    /** Grid Power drawn while a player flies with a ring. */
    public static final double GP = 16.0;
    /** Marks flight the ring granted. */
    public static final String TAG = "gridwright.angel_ring";
    public static final int SLOW_FALL_TICKS = 200;
    private static final double EPSILON = 1e-6;

    /** What {@link #update} decided for a player this tick. */
    public enum State { NONE, UNPOWERED, READY, FLYING }

    private static MinecraftServer current;
    /** The server tick in which each flying player last added demand. */
    private static final Map<UUID, Integer> DEMANDED = new HashMap<>();

    private AngelRingFlight() { }

    public static String formatGp() {
        return GridBlockEntity.format(GP);
    }

    /** Runs once per server tick, after the players have ticked. */
    public static void tick(MinecraftServer server) {
        reset(server);
        int now = server.getTickCount();
        DEMANDED.values().removeIf(tick -> tick < now - 1);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) update(player);
    }

    private static synchronized void reset(MinecraftServer server) {
        if (current != server) {
            current = server;
            DEMANDED.clear();
        }
    }

    public static boolean hasRing(ServerPlayer player) {
        return player.getInventory().hasAnyOf(Set.of(ModUtilities.ANGEL_RING.get()));
    }

    /** Grants, keeps or takes back {@code player}'s ring flight for this tick. */
    public static State update(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return State.NONE;
        reset(server);
        UUID id = player.getUUID();
        Abilities abilities = player.getAbilities();
        boolean granted = player.getTags().contains(TAG);
        if (player.isCreative() || player.isSpectator()) {
            // The game mode owns flight here; switching back to survival resets it.
            if (granted) player.removeTag(TAG);
            DEMANDED.remove(id);
            return State.NONE;
        }
        if (!hasRing(player)) {
            if (granted) revoke(player);
            DEMANDED.remove(id);
            return State.NONE;
        }
        String grid = GridKeys.of(server, id, player.getGameProfile().getName());
        GridManager manager = GridManager.get(server);
        GridSnapshot snapshot = manager.snapshot(grid);
        Integer last = DEMANDED.get(id);
        int now = server.getTickCount();
        // Last tick's totals include the ring only if it was flying then; otherwise it needs headroom.
        boolean powered = last != null && last == now - 1 ? snapshot.powered()
                : snapshot.capacity() - snapshot.demand() + EPSILON >= GP;
        if (!powered) {
            if (granted) revoke(player);
            DEMANDED.remove(id);
            return State.UNPOWERED;
        }
        if (!granted) {
            if (abilities.mayfly) return State.NONE; // Someone else's flight; leave it alone.
            player.addTag(TAG);
        }
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            player.onUpdateAbilities();
        }
        if (abilities.flying) {
            manager.addDemand(grid, GP);
            DEMANDED.put(id, now);
            return State.FLYING;
        }
        DEMANDED.remove(id);
        return State.READY;
    }

    private static void revoke(ServerPlayer player) {
        player.removeTag(TAG);
        Abilities abilities = player.getAbilities();
        boolean wasFlying = abilities.flying;
        abilities.mayfly = false;
        abilities.flying = false;
        player.onUpdateAbilities();
        if (wasFlying && !player.isOnGround()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS));
        }
    }
}
