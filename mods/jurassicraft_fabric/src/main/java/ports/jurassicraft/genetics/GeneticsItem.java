package ports.jurassicraft.genetics;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Displays genetic data without creating or mutating it on the client. */
public class GeneticsItem extends Item {
    public GeneticsItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Genetics.read(stack.getTag()).ifPresent(data -> {
            tooltip.add(Component.translatable("tooltip.jurassicraft.dna_species", Component.translatable("species.jurassicraft." + data.species())).withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.jurassicraft.dna_quality", data.quality()).withStyle(ChatFormatting.AQUA));
            if (data.kind() == DNAData.Kind.DINOSAUR) {
                tooltip.add(Component.translatable("tooltip.jurassicraft.genetic_code", data.genetics()).withStyle(ChatFormatting.BLUE));
            }
        });
    }
}
