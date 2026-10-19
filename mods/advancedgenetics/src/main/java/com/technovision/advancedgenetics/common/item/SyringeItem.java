package com.technovision.advancedgenetics.common.item;

import com.mojang.datafixers.util.Pair;
import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SyringeItem extends Item {

    public SyringeItem() {
        super(ItemRegistry.properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        if (!isFilled(stack)) return;
        if (!isPurified(stack)) {
            tooltip.add(Component.literal("Contaminated").withStyle(ChatFormatting.DARK_RED));
        }
        for (Pair<Genes, Boolean> genePair : getGenes(stack)) {
            Genes gene = genePair.getFirst();
            if (gene.isEnabled()) {
                String geneName = gene.getName();
                if (genePair.getSecond()) geneName = "Anti " + geneName;
                tooltip.add(Component.literal(geneName).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean("filled") || (tag.getBoolean("purified") && tag.contains("genes"))) {
            return ItemUtils.startUsingInstantly(world, user, hand);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        if (!(user instanceof Player player)) return stack;
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.getBoolean("filled")) {
            // Draw blood
            if (!world.isClientSide()) {
                fill(stack, player);
                player.hurt(DamageSource.GENERIC, 1.0f);
            }
        } else if (tag.getBoolean("purified") && tag.contains("genes")) {
            // Inject genes into bloodstream
            if (!world.isClientSide()) {
                boolean ownBlood = tag.hasUUID("uuid") && tag.getUUID("uuid").equals(player.getUUID());
                if (!Config.Common.geneSharing.get() && !ownBlood) {
                    player.sendSystemMessage(Component.translatable("message."+AdvancedGenetics.MOD_ID+".syringe", "§7"+player.getName().getString()+"§f"));
                    return stack;
                }
                inject(player, stack);
                player.hurt(DamageSource.GENERIC, 1.0f);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20*10));
            }
        }
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    private static List<Pair<Genes, Boolean>> getGenes(ItemStack stack) {
        List<Pair<Genes, Boolean>> genes = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) return genes;
        CompoundTag genesTag = tag.getCompound("genes");
        for (String geneName : genesTag.getAllKeys()) {
            Genes gene = Genes.byName(geneName);
            if (gene != null) {
                genes.add(Pair.of(gene, genesTag.getBoolean(geneName)));
            }
        }
        return genes;
    }

    public static boolean isFilled(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("filled");
    }

    public static boolean isPurified(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("purified");
    }

    public static void fill(ItemStack stack, Player player) {
        final CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("filled", true);
        tag.putBoolean("purified", false);
        tag.putUUID("uuid", player.getUUID());

        // Add any existing genes
        for (Genes gene : ComponentRegistry.getGenetics(player).getGenes()) {
            addGene(stack, gene);
        }
    }

    public static void purify(ItemStack stack) {
        final CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("purified", true);
    }

    public static void addGene(ItemStack syringe, Genes gene) {
        final CompoundTag syringeTag = syringe.getOrCreateTag();
        CompoundTag genes = syringeTag.getCompound("genes");
        genes.putBoolean(gene.toString(), false);
        syringeTag.put("genes", genes);
        syringeTag.putBoolean("purified", false);
    }

    public static void addGene(ItemStack plasmid, ItemStack syringe) {
        setPlasmidGene(plasmid, syringe, false);
    }

    public static void addAntiGene(ItemStack plasmid, ItemStack syringe) {
        setPlasmidGene(plasmid, syringe, true);
    }

    private static void setPlasmidGene(ItemStack plasmid, ItemStack syringe, boolean anti) {
        String gene = plasmid.getOrCreateTag().getString("gene");
        final CompoundTag syringeTag = syringe.getOrCreateTag();
        CompoundTag genes = syringeTag.getCompound("genes");
        genes.putBoolean(gene, anti);
        syringeTag.put("genes", genes);
        syringeTag.putBoolean("purified", false);
    }

    public static void inject(Player player, ItemStack stack) {
        // Add genes to user
        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(player);
        for (Pair<Genes, Boolean> genePair : getGenes(stack)) {
            boolean isAnti = genePair.getSecond();
            Genes gene = genePair.getFirst();
            if (isAnti && component.hasGene(gene)) {
                component.removeGene(gene);
                player.sendSystemMessage(Component.translatable("message."+AdvancedGenetics.MOD_ID+".inject_remove", "§7"+gene.getName()+"§f"));
            } else if (!isAnti && !component.hasGene(gene)) {
                component.addGene(gene);
                player.sendSystemMessage(Component.translatable("message."+AdvancedGenetics.MOD_ID+".inject_add", "§7"+gene.getName()+"§f"));
            }
        }

        // Reset syringe data
        final CompoundTag tag = stack.getOrCreateTag();
        tag.remove("filled");
        tag.remove("purified");
        tag.remove("genes");
        tag.remove("uuid");
    }
}
