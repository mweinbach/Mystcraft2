package ports.buildcraft.silicon.gate;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableItem;

/** Upstream {@code ItemPluggableGate} for one variant: places the gate, sounding like its material's block. */
public class GateItem extends Item implements PluggableItem {
    public final GateVariant variant;

    public GateItem(Properties properties, GateVariant variant) {
        super(properties);
        this.variant = variant;
    }

    @Nullable
    @Override
    public PipePluggable onPlace(ItemStack stack, PipeBlockEntity holder, Direction side, Player player, InteractionHand hand) {
        Level level = holder.getLevel();
        if (level == null) return null;
        BlockPos pos = holder.getBlockPos();
        SoundType sound = variant.material().block.defaultBlockState().getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        return new PluggableGate(BCSilicon.GATE, holder, side, variant);
    }

    @Override
    public Component getName(ItemStack stack) {
        return variant.localizedName();
    }

    /** Upstream showed the trigger count on the action line too; this shows the action count. */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gate.slots", variant.numSlots()));
        int triggerArgs = variant.numTriggerArgs(), actionArgs = variant.numActionArgs();
        if (triggerArgs == actionArgs) {
            if (triggerArgs > 0) tooltip.add(Component.translatable("gate.params", triggerArgs));
            return;
        }
        if (triggerArgs > 0) tooltip.add(Component.translatable("gate.params.trigger", triggerArgs));
        if (actionArgs > 0) tooltip.add(Component.translatable("gate.params.action", actionArgs));
    }
}
