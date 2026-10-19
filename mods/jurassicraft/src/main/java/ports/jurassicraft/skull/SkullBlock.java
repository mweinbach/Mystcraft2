package ports.jurassicraft.skull;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.jurassicraft.platform.Platform;

public final class SkullBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    private static final VoxelShape FLOOR=box(-2,0,-2,18,20,18),WALL=box(0,-4,0,16,16,16);
    public SkullBlock() { super(BlockBehaviour.Properties.of(Material.STONE).noCollission().noOcclusion().instabreak().sound(SoundType.STONE)); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.UP)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder) { builder.add(FACING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new SkullBlockEntity(pos,state); }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return state.getValue(FACING)==Direction.UP ? FLOOR : WALL; }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
        Direction face=state.getValue(FACING);
        return face!=Direction.DOWN && level.getBlockState(pos.relative(face.getOpposite())).isFaceSturdy(level,pos.relative(face.getOpposite()),face);
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,net.minecraft.world.level.block.Block changed,BlockPos from,boolean moving) {
        if (!level.isClientSide && !canSurvive(state,level,pos)) level.destroyBlock(pos,true);
        else super.neighborChanged(state,level,pos,changed,from,moving);
    }
    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof SkullBlockEntity skull) skull.suppressDrops();
        super.playerWillDestroy(level,pos,state,player);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && !Platform.restoringBlockSnapshots(level) && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                && level.getBlockEntity(pos) instanceof SkullBlockEntity skull && skull.shouldDrop()) {
            Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,skull.skull());
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level,BlockPos pos,BlockState state) {
        return level.getBlockEntity(pos) instanceof SkullBlockEntity skull ? skull.skull() : ItemStack.EMPTY;
    }
}
