package ports.jurassicraft.skull;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

/** Floor and wall skull placement must keep the real fossil or fresh item. */
public final class SkullGameTests implements FabricGameTest {
    private static final BlockPos SUPPORT = new BlockPos(1, 1, 1);
    private static void check(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void freshFloorAndFossilWall(GameTestHelper helper) {
        helper.setBlock(SUPPORT, Blocks.STONE);
        BlockPos support = helper.absolutePos(SUPPORT);
        var player = helper.makeMockPlayer();
        player.setPos(support.getX() - 1, support.getY(), support.getZ() - 1);
        ItemStack fresh = new ItemStack(AssemblyRegistry.FRESH_BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        fresh.getOrCreateTag().putString("Genetics", "ACGTACGTAC");
        fresh.getTag().putBoolean("Type", false);
        player.setItemInHand(InteractionHand.MAIN_HAND, fresh);
        var floorClick = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
        check(helper, fresh.useOn(floorClick).consumesAction(), "fresh skull must place using normal item interaction");
        BlockPos floor = support.above();
        check(helper, helper.getLevel().getBlockState(floor).is(SkullRegistry.DISPLAY.get())
                && helper.getLevel().getBlockState(floor).getValue(SkullBlock.FACING) == Direction.UP, "floor orientation must be retained");
        SkullBlockEntity placed = (SkullBlockEntity) helper.getLevel().getBlockEntity(floor);
        check(helper, placed.fresh() && !placed.stand() && "ACGTACGTAC".equals(placed.skull().getTag().getString("Genetics")),
                "fresh skull identity, pose and DNA must survive placement");
        SkullBlockEntity loaded = new SkullBlockEntity(floor, placed.getBlockState());
        loaded.load(placed.saveWithoutMetadata());
        check(helper, loaded.fresh() && !loaded.stand() && "ACGTACGTAC".equals(loaded.skull().getTag().getString("Genetics")),
                "saved skull must retain identity, pose and DNA");
        AABB area = new AABB(floor).inflate(2);
        var existing = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        helper.getLevel().destroyBlock(floor, true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                item -> !existing.contains(item.getUUID()) && item.getItem().is(AssemblyRegistry.FRESH_BONES.get(Species.TYRANNOSAURUS).get("skull").get()));
        check(helper, drops.size() == 1 && "ACGTACGTAC".equals(drops.get(0).getItem().getTag().getString("Genetics")),
                "breaking the display must return the original tagged skull once");
        drops.forEach(ItemEntity::discard);
        ItemStack fossil = new ItemStack(JurassicContent.BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, fossil);
        var wallClick = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.EAST, support, false));
        check(helper, fossil.useOn(wallClick).consumesAction(), "fossil skull must place on a wall");
        BlockPos wall = support.east();
        check(helper, helper.getLevel().getBlockState(wall).is(SkullRegistry.DISPLAY.get())
                && helper.getLevel().getBlockState(wall).getValue(SkullBlock.FACING) == Direction.EAST, "wall orientation must be retained");
        check(helper, !((SkullBlockEntity) helper.getLevel().getBlockEntity(wall)).fresh(), "wall display must retain fossil identity");
        helper.getLevel().setBlock(support, Blocks.AIR.defaultBlockState(), 3);
        check(helper, helper.getLevel().getBlockState(wall).isAir(), "unsupported skull must fall");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void undersidePlacementIsRejected(GameTestHelper helper) {
        helper.setBlock(SUPPORT, Blocks.STONE);
        BlockPos support = helper.absolutePos(SUPPORT);
        var player = helper.makeMockPlayer();
        player.setPos(support.getX() - 1, support.getY(), support.getZ() - 1);
        ItemStack fossil = new ItemStack(JurassicContent.BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        player.setItemInHand(InteractionHand.MAIN_HAND, fossil);
        var click = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.DOWN, support, false));
        check(helper, fossil.useOn(click) == InteractionResult.PASS && fossil.getCount() == 1
                && helper.getLevel().getBlockState(support.below()).isAir(), "underside placement must leave the item and world untouched");
        helper.succeed();
    }
}
