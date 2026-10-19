package ports.jurassicraft.figure;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.jurassicraft.entity.DinosaurProfile;

public final class ActionFigureBlock extends BaseEntityBlock {
    public ActionFigureBlock() { super(BlockBehaviour.Properties.of(Material.WOOD).strength(0).sound(SoundType.WOOD).noOcclusion().dynamicShape()); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ActionFigureBlockEntity(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(level.getBlockEntity(pos) instanceof ActionFigureBlockEntity figure)) return Block.box(0, 0, 0, 16, 16, 16);
        DinosaurProfile profile = DinosaurProfile.of(figure.species());
        float width = Mth.clamp(profile.adultWidth() * .25F, .1F, 1F) * 16;
        float height = Mth.clamp(profile.adultHeight() * .25F, .1F, 1F) * 16;
        return Block.box(8 - width / 2, 0, 8 - width / 2, 8 + width / 2, height, 8 + width / 2);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isSolidRender(level, below);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos sourcePos, boolean moving) {
        if (!level.isClientSide && !canSurvive(state, level, pos)) level.destroyBlock(pos, true);
        else super.neighborChanged(state, level, pos, source, sourcePos, moving);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ActionFigureBlockEntity figure) {
            figure.place(placer == null ? 0 : placer.getYHeadRot());
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
    private static ItemStack stack(ActionFigureBlockEntity figure) {
        ItemStack result = ActionFigureItem.create(figure.species());
        result.getOrCreateTagElement("BlockEntityTag").putInt("Gender", figure.gender());
        return result;
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return blockEntity instanceof ActionFigureBlockEntity figure ? List.of(stack(figure)) : List.of();
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof ActionFigureBlockEntity figure ? stack(figure) : ItemStack.EMPTY;
    }
}
