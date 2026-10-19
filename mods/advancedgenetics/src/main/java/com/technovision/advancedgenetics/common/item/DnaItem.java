package com.technovision.advancedgenetics.common.item;

import com.technovision.advancedgenetics.api.genetics.Entities;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DnaItem extends Item {

    public DnaItem() {
        super(ItemRegistry.properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        Genes gene = Genes.getGeneByItem(stack);
        if (gene == null) return;
        MutableComponent geneName;
        if (isDecoded(stack)) {
            geneName = Component.literal(gene.getName());
        } else {
            geneName = Component.literal(gene.getEncryptedName());
        }
        tooltip.add(geneName.withStyle(ChatFormatting.GRAY));
    }

    public static boolean isDecoded(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("decoded");
    }

    public static void setGene(ItemStack cellStack, ItemStack dnaStack) {
        Entities entity = Entities.findEntityByType(((CellItem) cellStack.getItem()).getEntityType());
        Genes gene = entity == null ? Genes.BASIC : entity.getRandomGene();
        final CompoundTag tag = dnaStack.getOrCreateTag();
        tag.putString("gene", gene.toString());
        tag.putBoolean("decoded", false);
    }

    public static void decode(ItemStack stack) {
        final CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("decoded", true);
    }
}
