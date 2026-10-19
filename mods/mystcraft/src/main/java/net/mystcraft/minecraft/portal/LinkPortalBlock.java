package net.mystcraft.minecraft.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.mystcraft.minecraft.linking.LinkingService;

public final class LinkPortalBlock extends Block {
    public static final BooleanProperty PLANAR = BooleanProperty.create("planar");
    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.create("axis", Direction.Axis.class);
    public LinkPortalBlock() {
        super(Properties.of(Material.PORTAL).strength(-1, 3600000).noCollission().noOcclusion()
                .sound(SoundType.GLASS).lightLevel(state -> 11).randomTicks().noLootTable());
        registerDefaultState(stateDefinition.any().setValue(CrystalBlock.ACTIVE, false)
                .setValue(CrystalBlock.SOURCE, Direction.DOWN).setValue(AXIS, Direction.Axis.X).setValue(PLANAR, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CrystalBlock.ACTIVE, CrystalBlock.SOURCE, AXIS, PLANAR);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(PLANAR)) return Shapes.block();
        return switch (state.getValue(AXIS)) {
            case X -> Block.box(4, 0, 0, 12, 16, 16);
            case Y -> Block.box(0, 4, 0, 16, 12, 16);
            case Z -> Block.box(0, 0, 4, 16, 16, 12);
        };
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block changed, BlockPos from, boolean moving) {
        PortalController.validate(level, pos);
    }
    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { PortalController.validate(level, pos); }
    @Override public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || entity.isOnPortalCooldown() || !entity.isAlive()) return;
        BookReceptacleBlockEntity source = PortalController.source(level, pos);
        if (source == null || source.getBook().isEmpty() || !PortalController.tension(level, pos)) {
            PortalController.validate(level, pos); return;
        }
        // A cooldown also throttles rejected/unsafe destinations and prevents arrival loops.
        entity.setPortalCooldown();
        LinkingService.linkFromPortal(entity, source.getBook());
        source.setChanged();
    }
}
