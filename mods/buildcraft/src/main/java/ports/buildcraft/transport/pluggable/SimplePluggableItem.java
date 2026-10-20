package ports.buildcraft.transport.pluggable;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.transport.PipeBlockEntity;

/** Upstream {@code ItemPluggableSimple}: places its definition's default pluggable, playing the pipe's place sound. */
public class SimplePluggableItem extends Item implements PluggableItem {
    @FunctionalInterface
    public interface PlacementPredicate {
        boolean canPlace(ItemStack stack, PipeBlockEntity holder, Direction side);
    }

    /** Upstream {@code PIPE_BEHAVIOUR_ACCEPTS_RS_POWER}: the pipe's behaviour takes redstone engine power on that side. */
    public static final PlacementPredicate BEHAVIOUR_ACCEPTS_REDSTONE_POWER = (stack, holder, side) -> {
        EnergyPort port = holder.behaviour().energyPort(side);
        return port != null && port.acceptsRedstoneEngine();
    };

    private final PluggableDefinition definition;
    @Nullable
    private final PlacementPredicate canPlace;

    public SimplePluggableItem(Properties properties, PluggableDefinition definition, @Nullable PlacementPredicate canPlace) {
        super(properties);
        this.definition = definition;
        this.canPlace = canPlace;
    }

    @Nullable
    @Override
    public PipePluggable onPlace(ItemStack stack, PipeBlockEntity holder, Direction side, Player player, InteractionHand hand) {
        if (canPlace != null && !canPlace.canPlace(stack, holder, side)) return null;
        playPlaceSound(holder.getLevel(), holder.getBlockPos());
        return definition.create(holder, side);
    }

    /** Upstream {@code SoundUtil.playBlockPlace}. */
    public static void playPlaceSound(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }
}
