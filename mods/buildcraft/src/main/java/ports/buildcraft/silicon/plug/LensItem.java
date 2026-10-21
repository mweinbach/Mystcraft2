package ports.buildcraft.silicon.plug;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableItem;
import ports.buildcraft.transport.pluggable.SimplePluggableItem;

/** Upstream {@code ItemPluggableLens} for one variant: goes only on item pipes, with stone's place sound. */
public class LensItem extends Item implements PluggableItem {
    public final LensVariant variant;

    public LensItem(Properties properties, LensVariant variant) {
        super(properties);
        this.variant = variant;
    }

    @Nullable
    @Override
    public PipePluggable onPlace(ItemStack stack, PipeBlockEntity holder, Direction side, Player player, InteractionHand hand) {
        if (!(holder.flow() instanceof ItemFlow) || holder.getLevel() == null) return null;
        SimplePluggableItem.playPlaceSound(holder.getLevel(), holder.getBlockPos(), SoundType.STONE);
        return new LensPluggable(BCSilicon.LENS, holder, side, variant);
    }

    @Override
    public Component getName(ItemStack stack) {
        return variant.localizedName();
    }
}
