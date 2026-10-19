package ports.jurassicraft.plantculture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsItem;
import ports.jurassicraft.plants.AncientAquaticPlantBlock;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** A callus plants its species on farmland or, for aquatic species, in water. */
public final class PlantCallusItem extends GeneticsItem {
    private final PlantSpecies species;
    public PlantCallusItem(PlantSpecies species, Properties properties) { super(properties); this.species = species; }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos target = context.getClickedPos().above();
        if (player == null || context.getClickedFace() != Direction.UP || !validTag(context.getItemInHand().getTag())) return InteractionResult.PASS;
        if (!level.isInWorldBounds(target) || !player.mayUseItemAt(target, Direction.UP, context.getItemInHand())) return InteractionResult.FAIL;
        InteractionResult result = plant(level, context.getClickedPos(), species);
        if (result == InteractionResult.CONSUME && !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return result;
    }

    /** Plant after the caller has checked the player's permissions and sample tag. */
    static InteractionResult plant(Level level, BlockPos ground, PlantSpecies species) {
        var registered = PlantRegistry.plant(species);
        if (registered == null) return InteractionResult.PASS;
        Block plant = registered.get();
        BlockState state = plant.defaultBlockState();
        BlockPos target = ground.above();
        if (!level.isInWorldBounds(target)) return InteractionResult.FAIL;
        boolean aquatic = plant instanceof AncientAquaticPlantBlock;
        if (aquatic) {
            if (!level.getBlockState(target).is(Blocks.WATER) || level.getFluidState(target).getType() != Fluids.WATER
                    || !level.getFluidState(target).isSource() || !state.canSurvive(level, target)) return InteractionResult.PASS;
        } else {
            if (!level.getBlockState(ground).is(Blocks.FARMLAND) || !level.isEmptyBlock(target)) return InteractionResult.PASS;
            if (plant instanceof DoublePlantBlock && (!level.isInWorldBounds(target.above()) || !level.isEmptyBlock(target.above()))) return InteractionResult.PASS;
            if (plant instanceof CropBlock && !state.canSurvive(level, target)) return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState originalGround = level.getBlockState(ground);
        if (!aquatic && !(plant instanceof CropBlock)) level.setBlock(ground, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        if (!state.canSurvive(level, target)) {
            if (!aquatic && !(plant instanceof CropBlock)) level.setBlock(ground, originalGround, Block.UPDATE_ALL);
            return InteractionResult.FAIL;
        }
        if (plant instanceof DoublePlantBlock) DoublePlantBlock.placeAt(level, state, target, Block.UPDATE_ALL);
        else if (!level.setBlock(target, state, Block.UPDATE_ALL)) {
            if (!aquatic && !(plant instanceof CropBlock)) level.setBlock(ground, originalGround, Block.UPDATE_ALL);
            return InteractionResult.FAIL;
        }
        return InteractionResult.CONSUME;
    }

    private boolean validTag(CompoundTag tag) {
        if (tag == null) return true; // Creative-tab samples have no generated DNA.
        boolean hasData = false;
        for (String key : new String[]{"DNAQuality", "Genetics", "StorageId", "SpeciesId", "Dinosaur", "Plant"}) hasData |= tag.contains(key);
        if (!hasData) return true;
        return Genetics.read(tag).filter(data -> data.kind() == DNAData.Kind.PLANT && data.plant() == species && data.quality() == 100).isPresent();
    }
}
