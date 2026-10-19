package net.mystcraft.minecraft.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.mystcraft.integration.registry.InkRegistry;

/** Classic water-like black ink: eight levels, no infinite source conversion, one bucket per source. */
public class ClassicInkFluid extends FlowingFluid {
    private final boolean source;
    public ClassicInkFluid(boolean source){this.source=source;}
    @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid,FluidState> builder){super.createFluidStateDefinition(builder);builder.add(LEVEL);}
    @Override public Fluid getFlowing(){return InkRegistry.FLOWING_BLACK_INK.get();}
    @Override public Fluid getSource(){return InkRegistry.BLACK_INK.get();}
    @Override public Item getBucket(){return InkRegistry.BLACK_INK_BUCKET.get();}
    @Override protected boolean canConvertToSource(){return false;}
    @Override protected void beforeDestroyingBlock(LevelAccessor level,BlockPos pos,BlockState state){Block.dropResources(state,level,pos,state.hasBlockEntity()?level.getBlockEntity(pos):null);}
    @Override protected int getSlopeFindDistance(LevelReader level){return 4;}
    @Override protected int getDropOff(LevelReader level){return 1;}
    @Override public int getTickDelay(LevelReader level){return 5;}
    @Override protected float getExplosionResistance(){return 100F;}
    @Override protected BlockState createLegacyBlock(FluidState state){return InkRegistry.BLACK_INK_BLOCK.get().defaultBlockState().setValue(LiquidBlock.LEVEL,getLegacyLevel(state));}
    @Override public boolean isSource(FluidState state){return source;}
    @Override public int getAmount(FluidState state){return source?8:state.getValue(LEVEL);}
    @Override public boolean isSame(Fluid fluid){return fluid==getSource()||fluid==getFlowing();}
    @Override protected boolean canBeReplacedWith(FluidState state,BlockGetter level,BlockPos pos,Fluid fluid,Direction direction){return false;}
}
