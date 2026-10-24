package ports.gridwright.worker;

import com.mojang.authlib.GameProfile;
import ports.gridwright.grid.GridBlockEntity;
import ports.gridwright.platform.Platform;
import ports.gridwright.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * How machines act in the world: always as their owner, through a fake player carrying the owner's
 * profile, and only where the owner could act. A machine without an owner does nothing. Before a
 * block is broken, vanilla's spawn protection, world border and adventure-mode rules are checked and
 * the loader's break event is fired, so claim and protection mods can refuse it.
 */
public final class MachinePlayer {

    private MachinePlayer() { }

    /**
     * The owner's fake player, emptied, standing so that its eyes are on {@code machine}'s
     * {@code facing} face and it looks out of it. Null if the machine has no owner.
     */
    @Nullable
    public static ServerPlayer prepare(ServerLevel level, GridBlockEntity machine, Direction facing) {
        UUID owner = machine.owner();
        if (owner == null) return null;
        String name = machine.ownerName().isEmpty() ? "[Gridwright]" : machine.ownerName();
        ServerPlayer player = Platform.fakePlayer(level, new GameProfile(owner, name));
        player.getInventory().clearContent();
        catchUpCooldowns(level, player);
        Vec3 eye = Vec3.atCenterOf(machine.getBlockPos()).add(facing.getStepX() * 0.51, facing.getStepY() * 0.51,
                facing.getStepZ() * 0.51);
        float yaw = facing.getAxis().isHorizontal() ? facing.toYRot() : 0;
        float pitch = facing == Direction.UP ? -90 : facing == Direction.DOWN ? 90 : 0;
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setOnGround(true);
        player.setShiftKeyDown(false);
        return player;
    }

    /**
     * Fake players are never ticked (and are shared by all of an owner's machines in a level), so their
     * item cooldowns (ender pearls, chorus fruit...) are advanced here by the server ticks since they
     * were last prepared, at most {@link #MAX_COOLDOWN_CATCH_UP}.
     */
    private static void catchUpCooldowns(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        Long last = LAST_PREPARED.put(player, now);
        long elapsed = last == null ? 0 : Math.min(MAX_COOLDOWN_CATCH_UP, Math.max(0, now - last));
        for (long i = 0; i < elapsed; i++) player.getCooldowns().tick();
    }

    private static final int MAX_COOLDOWN_CATCH_UP = 24_000;
    private static final Map<ServerPlayer, Long> LAST_PREPARED = new WeakHashMap<>();

    /** Whether a machine could ever mine {@code state}: not air or a plain fluid, breakable, not tagged unminable. */
    public static boolean minable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return !state.is(ModTags.UNMINABLE);
    }

    /**
     * Whether {@code player} (a machine's owner) may break the block at {@code pos}: spawn protection,
     * the world border, adventure-mode rules, and the loader's break event, which claim mods can cancel.
     */
    public static boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!level.mayInteract(player, pos)) return false;
        if (player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return false;
        return Platform.mayBreak(level, pos, state, player);
    }

    /**
     * Breaks the block as {@code player} would once its drops have been collected by the caller: the
     * block reacts as to a player, is removed (leaving any fluid it held), the tool takes wear and the
     * loader's after-break event fires. Experience and other break effects happen if {@code effects}.
     */
    public static void destroy(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
                               ServerPlayer player, ItemStack tool, boolean effects) {
        state.getBlock().playerWillDestroy(level, pos, state, player);
        if (level.removeBlock(pos, false)) state.getBlock().destroy(level, pos, state);
        if (!tool.isEmpty()) tool.mineBlock(level, state, pos, player);
        if (effects) state.spawnAfterBreak(level, pos, tool, true);
        Platform.afterBreak(level, pos, state, blockEntity, player);
    }
}
