package ports.gridwright.block;

import ports.gridwright.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A spike block that hurts creatures standing on it. Its collision box stops a pixel short of the top,
 * like a cactus, so a creature standing on it is inside the block and is hit every time its
 * post-hit invulnerability runs out.
 */
public class SpikeBlock extends Block {

    /** Damage type for every spike; players and creatures killed by one get a spike death message. */
    public static final DamageSource DAMAGE = new DamageSource("gridwright.spike");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    private final SpikeKind kind;

    public SpikeBlock(SpikeKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public SpikeKind kind() {
        return kind;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living) hurt(server, living, kind);
    }

    /** One spike hit on {@code victim}; returns whether it was hurt. */
    public static boolean hurt(ServerLevel level, LivingEntity victim, SpikeKind kind) {
        if (!victim.isAlive()) return false;
        float amount = kind.damage();
        if (!kind.lethal()) amount = Math.min(amount, victim.getHealth() - 1.0F);
        if (amount <= 0) return false;
        // Credit the kill to a fake player, never to a real one, so experience and player-only loot
        // drop; players themselves are just hurt. The hit comes from the fake player itself (rather
        // than setLastHurtByPlayer, whose credit window depends on the victim's age) and is marked
        // no-aggro so mobs do not turn on a player who is not in the world.
        if (kind.playerKill() && !(victim instanceof Player)) {
            return victim.hurt(new EntityDamageSource(DAMAGE.getMsgId(), Platform.fakePlayer(level)).setNoAggro(), amount);
        }
        return victim.hurt(DAMAGE, amount);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright.spike." + kind.name().toLowerCase(java.util.Locale.ROOT)));
    }
}
