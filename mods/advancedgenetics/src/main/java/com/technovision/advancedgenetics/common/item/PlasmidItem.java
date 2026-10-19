package com.technovision.advancedgenetics.common.item;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PlasmidItem extends Item {

    public PlasmidItem() {
        super(ItemRegistry.properties().stacksTo(1));
    }

    /**
     * Gene points a plasmid needs before it can be injected. Read lazily because Forge
     * loads configs after items are registered.
     */
    public static int maxGenes() {
        return Config.Common.plasmidRequirement.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        Genes gene = Genes.getGeneByItem(stack);
        if (gene != null) {
            int count = stack.getOrCreateTag().getInt("count");
            String text = gene.getName();
            if (count < maxGenes()) {
                text += " " + count + "/" + maxGenes();
            }
            tooltip.add(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean canCombine(ItemStack dnaItem, ItemStack plasmidItem) {
        if (!dnaItem.hasTag()) return false;
        String gene = dnaItem.getOrCreateTag().getString("gene");
        if (!plasmidItem.hasTag()) {
            return !gene.equals(Genes.BASIC.toString());
        }
        CompoundTag plasmidTag = plasmidItem.getOrCreateTag();
        return (gene.equals(Genes.BASIC.toString()) || gene.equals(plasmidTag.getString("gene")))
                && plasmidTag.getInt("count") < maxGenes();
    }

    public static void combine(ItemStack dnaItem, ItemStack plasmidItem) {
        String gene = dnaItem.getOrCreateTag().getString("gene");
        if (!plasmidItem.hasTag()) {
            CompoundTag tag = plasmidItem.getOrCreateTag();
            tag.putString("gene", gene);
            tag.putInt("count", 2);
        } else {
            CompoundTag tag = plasmidItem.getOrCreateTag();
            int increment = gene.equals(Genes.BASIC.toString()) ? 1 : 2;
            int newTotal = tag.getInt("count") + increment;
            if (newTotal > maxGenes()) newTotal = maxGenes();
            tag.putInt("count", newTotal);
        }
    }
}
