package ports.gridwright.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.grid.GridManager;
import ports.gridwright.registry.ModPower;

/**
 * A fire, lava, manual or dragon egg mill. Like the phase 1 producers it re-reads its surroundings once
 * a second and reports its output to its owner's grid every tick; the manual mill instead runs for a
 * short while after each crank.
 */
public class MillBlockEntity extends GridBlockEntity {

    /** How often, in ticks, a mill re-reads its surroundings. */
    public static final int REFRESH_TICKS = 20;
    /** Ticks of turning one crank adds; holding use on the block cranks about every 4 ticks. */
    public static final int CRANK_TICKS = 10;
    /** A manual mill never stores more than this many ticks of turning. */
    public static final int MAX_WOUND = 40;
    /** Hunger exhaustion each crank costs the player (sprinting one block costs 0.1). */
    public static final float CRANK_EXHAUSTION = 0.025F;

    private double output;
    private int refresh;
    private int wound;

    public MillBlockEntity(BlockPos pos, BlockState state) {
        super(ModPower.MILL.get(), pos, state);
    }

    public MillKind kind() {
        return ((MillBlock) getBlockState().getBlock()).kind();
    }

    /** The output measured at the last refresh (every tick for the manual mill), before diminishing returns. */
    public double output() {
        return output;
    }

    /** Ticks of turning a manual mill has left. */
    public int wound() {
        return wound;
    }

    /**
     * Whether {@code player} may crank a manual mill: a living, non-spectator player who is connected to
     * the server. Fake players (Forge's, Gridwright's own and other mods' machine players) are never in
     * the player list, so machines cannot turn it.
     */
    public static boolean isCrankingPlayer(Player player) {
        return player instanceof ServerPlayer server && !server.isSpectator() && server.isAlive()
                && server.server.getPlayerList().getPlayer(server.getUUID()) == server;
    }

    /** Cranks a manual mill for {@code player}; returns whether it turned. */
    public boolean crank(Player player) {
        if (kind() != MillKind.MANUAL || !isCrankingPlayer(player)) return false;
        windUp();
        player.causeFoodExhaustion(CRANK_EXHAUSTION);
        return true;
    }

    /** Adds one crank's worth of turning, up to {@link #MAX_WOUND}. */
    public void windUp() {
        wound = Math.min(MAX_WOUND, wound + CRANK_TICKS);
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel server) || !(blockEntity instanceof MillBlockEntity mill)) return;
        MillKind kind = mill.kind();
        if (kind == MillKind.MANUAL) {
            mill.output = mill.wound > 0 ? MillKind.MANUAL_GP : 0;
            if (mill.wound > 0) {
                mill.wound--;
                mill.setChanged();
            }
        } else if (mill.refresh-- <= 0) {
            mill.output = kind.output(server, pos);
            mill.refresh = REFRESH_TICKS - 1;
        }
        String grid = mill.gridKey(server.getServer());
        if (grid != null) {
            GridManager.get(server.getServer()).addProduction(grid, Registry.BLOCK.getKey(state.getBlock()).toString(), mill.output);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (wound > 0) tag.putInt("Wound", wound);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        wound = Math.max(0, Math.min(MAX_WOUND, tag.getInt("Wound")));
    }
}
