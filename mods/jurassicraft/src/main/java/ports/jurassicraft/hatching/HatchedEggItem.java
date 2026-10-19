package ports.jurassicraft.hatching;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
    @Override public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        if (context.getLevel() instanceof ServerLevel level) {
            // The original item fills missing data on first use; initialized eggs retain it.
            CompoundTag tag = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
            if (!tag.contains("DNAQuality")) tag.putInt("DNAQuality", player != null && player.getAbilities().instabuild ? 100 : 0);
            if (!tag.contains("Genetics")) tag.putString("Genetics", Genetics.randomGenetics(level.random));
            if (!tag.contains("StorageId")) tag.putString("StorageId", "DinoDNA");
            if (!tag.contains("Dinosaur") && !tag.contains("SpeciesId")) tag.putInt("Dinosaur", species.legacyId());
            var dna = Genetics.read(tag);
            if (dna.isEmpty() || dna.get().dinosaur() != species || tag.contains("Gender") && !tag.contains("Gender", Tag.TAG_BYTE))
                return InteractionResult.FAIL;
            if (!tag.contains("Gender")) tag.putBoolean("Gender", level.random.nextBoolean());
            DinosaurAttributes inherited = DinosaurAttributes.read(tag.getCompound("GeneticAttributes")).orElse(null);
            DinosaurEntity animal = DinosaurRegistry.spawn(species, level, pos, true, tag.getBoolean("Gender"), inherited);
            if (animal == null) return InteractionResult.FAIL;
            animal.setGenetics(dna.get().genetics());
            animal.setDNAQuality(dna.get().quality());
            DinosaurFamilyData.joinHatchling(animal, tag);
            if (player != null && !player.isShiftKeyDown()) animal.setOwner(player.getUUID());
            stack.setTag(tag);
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
