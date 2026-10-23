package ports.buildcraft.lib;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.platform.Platform;

/**
 * Protection checks for machines that change the world on their owner's behalf. Upstream acted through the owner's
 * fake player ({@code FakePlayerProvider}) and posted Forge's block break and place events for it
 * ({@code BlockUtil.breakBlock}, {@code PipeExtensionManager}), giving up when a handler cancelled them. Each check
 * here first makes vanilla's own checks (the build height, spawn protection and the world border through
 * {@code Level.mayInteract}, and adventure mode), then the loader's events through {@link Platform.Backend}: Forge
 * posts {@code BlockEvent.BreakEvent} and {@code BlockEvent.EntityPlaceEvent}, Fabric fires
 * {@code PlayerBlockBreakEvents.BEFORE}. Every check is made before the world changes, so a refusal leaves it as
 * it was.
 */
public final class BlockProtection {
    /** Upstream {@code FakePlayerProvider.NULL_PROFILE}: the player of machines that have no owner. */
    public static final GameProfile NULL_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("buildcraft.core".getBytes(StandardCharsets.UTF_8)), "[BuildCraft]");

    private BlockProtection() {}

    /**
     * Upstream {@code FakePlayerProvider.getFakePlayer(world, profile, pos)}: the loader's fake player for
     * {@code owner} (or {@link #NULL_PROFILE}), with an empty inventory, at {@code pos}.
     */
    public static ServerPlayer fakePlayer(ServerLevel level, @Nullable GameProfile owner, BlockPos pos) {
        ServerPlayer player = Platform.get().fakePlayer(level, owner == null ? NULL_PROFILE : owner);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        return player;
    }

    /** Whether {@code player} may break the block at {@code pos}. */
    public static boolean mayBreak(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return mayChange(level, pos, player) && Platform.get().fireBlockBreak(level, pos, level.getBlockState(pos), player);
    }

    /**
     * Whether {@code player} may place a block at {@code pos}, on {@code face} of the block {@code placedAgainst}
     * beside it, replacing what is there.
     */
    public static boolean mayPlace(ServerLevel level, BlockPos pos, Direction face, BlockState placedAgainst, ServerPlayer player) {
        return mayChange(level, pos, player) && player.mayUseItemAt(pos, face, ItemStack.EMPTY)
                && Platform.get().fireBlockPlace(level, pos, placedAgainst, player);
    }

    /**
     * Whether {@code player} may use {@code stack} on {@code face} of {@code pos}, by vanilla's checks. Loader events
     * are left to the item: on Forge, {@code ItemStack.useOn} posts the place events for the blocks it changes.
     */
    public static boolean mayUse(ServerLevel level, BlockPos pos, Direction face, ItemStack stack, ServerPlayer player) {
        return mayChange(level, pos, player) && player.mayUseItemAt(pos, face, stack);
    }

    private static boolean mayChange(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return !level.isOutsideBuildHeight(pos) && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }
}
