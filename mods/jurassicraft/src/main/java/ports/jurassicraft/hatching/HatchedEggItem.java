package ports.jurassicraft.hatching;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurFamilyData;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.DinosaurAttributes;
import ports.jurassicraft.genetics.GeneticsItem;

/** A ready egg is consumed only if the requested animal can actually spawn. */
public final class HatchedEggItem extends GeneticsItem {
    private final Species species;
    public HatchedEggItem(Species species) {
        super(new Properties().tab(JurassicContent.TAB).stacksTo(1));
        this.species = species;
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Gender") && !tag.contains("Gender", Tag.TAG_BYTE)) return;
        String mode = tag == null || !tag.contains("Gender") ? "random" : tag.getBoolean("Gender") ? "male" : "female";
        tooltip.add(Component.translatable("tooltip.jurassicraft.spawn_egg_gender",
                Component.translatable("mode.jurassicraft." + mode)).withStyle(ChatFormatting.GOLD));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        if (context.getLevel() instanceof ServerLevel level && !hatch(level, pos, player, stack)) return InteractionResult.FAIL;
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /** Let a gestated fish be released by targeting the water surface, like an aquatic spawn egg. */
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (species != Species.COELACANTH) return InteractionResultHolder.pass(stack);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)
                || !level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource())
            return InteractionResultHolder.pass(stack);
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack))
            return InteractionResultHolder.fail(stack);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        if (!hatch(server, pos, player, stack)) return InteractionResultHolder.fail(stack);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        return InteractionResultHolder.consume(stack);
    }

    private boolean hatch(ServerLevel level, BlockPos pos, @Nullable Player player, ItemStack stack) {
        // The original item fills missing data on first use; initialized eggs retain it.
        CompoundTag tag = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        if (!tag.contains("DNAQuality")) tag.putInt("DNAQuality", player != null && player.getAbilities().instabuild ? 100 : 0);
        if (!tag.contains("Genetics")) tag.putString("Genetics", Genetics.randomGenetics(level.random));
        if (!tag.contains("StorageId")) tag.putString("StorageId", "DinoDNA");
        if (!tag.contains("Dinosaur") && !tag.contains("SpeciesId")) tag.putInt("Dinosaur", species.legacyId());
        var dna = Genetics.read(tag);
        if (dna.isEmpty() || dna.get().dinosaur() != species || tag.contains("Gender") && !tag.contains("Gender", Tag.TAG_BYTE))
            return false;
        if (!tag.contains("Gender")) tag.putBoolean("Gender", level.random.nextBoolean());
        DinosaurAttributes inherited = DinosaurAttributes.read(tag.getCompound("GeneticAttributes")).orElse(null);
        DinosaurEntity animal = DinosaurRegistry.spawn(species, level, pos, true, tag.getBoolean("Gender"), inherited);
        if (animal == null) return false;
        animal.setGenetics(dna.get().genetics());
        animal.setDNAQuality(dna.get().quality());
        DinosaurFamilyData.joinHatchling(animal, tag);
        if (player != null && !player.isShiftKeyDown()) animal.setOwner(player.getUUID());
        stack.setTag(tag);
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        return true;
    }
}
