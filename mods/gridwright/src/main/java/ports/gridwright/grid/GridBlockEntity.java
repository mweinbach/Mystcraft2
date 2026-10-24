package ports.gridwright.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * A block on someone's grid. The player who places it owns it; if that player is on a scoreboard team
 * the block joins the team's shared grid instead, and follows the player when they change teams.
 * Blocks with no owner (placed by commands, structures or machines) belong to no grid.
 */
public abstract class GridBlockEntity extends BlockEntity {

    private static final String OWNER = "Owner";
    private static final String OWNER_NAME = "OwnerName";

    @Nullable private UUID owner;
    private String ownerName = "";

    protected GridBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setOwner(@Nullable UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = owner == null ? "" : ownerName;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /** The grid this block reports to, or {@code null} if it has no owner. */
    @Nullable
    public String gridKey(MinecraftServer server) {
        if (owner == null) return null;
        PlayerTeam team = ownerName.isEmpty() ? null : server.getScoreboard().getPlayersTeam(ownerName);
        return team != null ? "team:" + team.getName() : "player:" + owner;
    }

    /** A one-line description of this block's grid for the action bar. */
    public Component gridStatus(MinecraftServer server) {
        String key = gridKey(server);
        if (key == null) return Component.translatable("message.gridwright.grid.no_owner");
        GridSnapshot grid = GridManager.get(server).snapshot(key);
        Component name = key.startsWith("team:") ? Component.translatable("message.gridwright.grid.team", key.substring(5))
                : Component.literal(ownerName.isEmpty() ? owner.toString() : ownerName);
        return Component.translatable(grid.powered() ? "message.gridwright.grid.status" : "message.gridwright.grid.overloaded",
                name, format(grid.capacity()), format(grid.demand()));
    }

    public static String format(double gp) {
        return String.format(Locale.ROOT, "%.1f", gp);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID(OWNER, owner);
            tag.putString(OWNER_NAME, ownerName);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
        ownerName = owner == null ? "" : tag.getString(OWNER_NAME);
    }
}
