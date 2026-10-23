package ports.buildcraft.transport;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ports.buildcraft.lib.Power;

/** Upstream {@code ItemPipeHolder}: shows the pipe's {@code tip.} line, if it has one, and its fluid or power transfer rate. */
public class PipeItem extends BlockItem {
    private final PipeDefinition definition;

    public PipeItem(PipeBlock block, Properties properties, PipeDefinition definition) {
        super(block, properties);
        this.definition = definition;
    }

    public PipeDefinition definition() {
        return definition;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String tip = "tip." + getDescriptionId();
        if (Language.getInstance().has(tip)) tooltip.add(Component.translatable(tip).withStyle(ChatFormatting.GRAY));
        switch (definition.flow) {
            case FLUID -> tooltip.add(Component.translatable("tip.buildcraft.fluid_flow", definition.fluidTransferPerTick)
                    .withStyle(ChatFormatting.GRAY));
            case POWER -> tooltip.add(Component.translatable("tip.buildcraft.power_flow",
                    Power.formatMj(definition.powerTransferPerTick), Power.toUnits(definition.powerTransferPerTick))
                    .withStyle(ChatFormatting.GRAY));
            case ITEM, STRUCTURE -> {
            }
        }
    }
}
