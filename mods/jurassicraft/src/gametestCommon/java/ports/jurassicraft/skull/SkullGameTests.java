package ports.jurassicraft.skull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

public final class SkullGameTests {
    private static final BlockPos SUPPORT=new BlockPos(1,1,1);
    private SkullGameTests() {}
    private static void check(GameTestHelper h,boolean value,String message) { if (!value) h.fail(message); }
    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void placementAndMetadata(GameTestHelper h) {
        h.setBlock(SUPPORT,Blocks.STONE); BlockPos support=h.absolutePos(SUPPORT);
        var player=h.makeMockPlayer(); player.setPos(support.getX()-1,support.getY(),support.getZ()-1);
        ItemStack fresh=new ItemStack(AssemblyRegistry.FRESH_BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        fresh.getOrCreateTag().putString("Genetics","ACGTACGTAC"); fresh.getTag().putBoolean("Type",false);
        player.setItemInHand(InteractionHand.MAIN_HAND,fresh);
        var floorClick=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support),Direction.UP,support,false));
        check(h,player.getItemInHand(InteractionHand.MAIN_HAND).useOn(floorClick).consumesAction(),"skull item must handle placement through normal useOn");
        BlockPos floor=support.above();
        check(h,h.getLevel().getBlockState(floor).is(SkullRegistry.DISPLAY.get()) && h.getLevel().getBlockState(floor).getValue(SkullBlock.FACING)==Direction.UP,"fresh skull must place on a floor");
        SkullBlockEntity placed=(SkullBlockEntity)h.getLevel().getBlockEntity(floor);
        check(h,placed.fresh() && !placed.stand() && "ACGTACGTAC".equals(placed.skull().getTag().getString("Genetics")),"placement must preserve the skull's DNA and stand pose");
        SkullBlockEntity loaded=new SkullBlockEntity(floor,placed.getBlockState()); loaded.load(placed.saveWithoutMetadata());
        check(h,loaded.fresh() && !loaded.stand() && "ACGTACGTAC".equals(loaded.skull().getTag().getString("Genetics")),"saved skull must preserve identity and metadata");
        var existing=h.getEntities(EntityType.ITEM,SUPPORT.above(),2).stream().map(item -> item.getUUID()).collect(java.util.stream.Collectors.toSet());
        h.getLevel().destroyBlock(floor,true);
        var drops=h.getEntities(EntityType.ITEM,SUPPORT.above(),2);
        check(h,drops.stream().anyMatch(item -> !existing.contains(item.getUUID()) && item.getItem().is(AssemblyRegistry.FRESH_BONES.get(Species.TYRANNOSAURUS).get("skull").get()) && item.getItem().hasTag() && "ACGTACGTAC".equals(item.getItem().getTag().getString("Genetics"))),"removed display must return the original tagged skull");
        ItemStack fossil=new ItemStack(JurassicContent.BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        player.setItemInHand(InteractionHand.MAIN_HAND,fossil);
        var wallClick=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support),Direction.EAST,support,false));
        check(h,player.getItemInHand(InteractionHand.MAIN_HAND).useOn(wallClick).consumesAction(),"fossil skull item must handle normal useOn");
        BlockPos wall=support.east();
        check(h,h.getLevel().getBlockState(wall).is(SkullRegistry.DISPLAY.get()) && h.getLevel().getBlockState(wall).getValue(SkullBlock.FACING)==Direction.EAST,"fossil skull must place on a wall");
        check(h,!((SkullBlockEntity)h.getLevel().getBlockEntity(wall)).fresh(),"wall display must retain fossil identity");
        h.getLevel().setBlock(support,Blocks.AIR.defaultBlockState(),3);
        check(h,h.getLevel().getBlockState(wall).isAir(),"display must fall when its support is removed");
        h.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void freshFloorAndFossilWall(GameTestHelper helper) {
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
    public static void undersidePlacementIsRejected(GameTestHelper helper) {
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
