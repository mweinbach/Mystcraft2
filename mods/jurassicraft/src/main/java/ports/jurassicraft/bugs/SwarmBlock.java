package ports.jurassicraft.bugs;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SwarmBlock extends Block {
    private static final VoxelShape SHAPE=Block.box(0,0,0,16,1.44,16);
    private final Supplier<Item> item;
    public SwarmBlock(Supplier<Item> item) { super(BlockBehaviour.Properties.of(Material.PLANT).noCollission().noOcclusion().instabreak().sound(SoundType.GRASS).randomTicks()); this.item=item; }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return SHAPE; }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) { return level.getFluidState(pos.below()).is(FluidTags.WATER); }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if (!level.isClientSide && !canSurvive(state,level,pos)) level.destroyBlock(pos,true);
        else super.neighborChanged(state,level,pos,block,from,moving);
    }
    @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if (!canSurvive(state,level,pos)) { level.destroyBlock(pos,true); return; }
        if (random.nextInt(10)==0) Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,new ItemStack(item.get()));
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level,BlockPos pos,BlockState state) { return new ItemStack(item.get()); }
}
