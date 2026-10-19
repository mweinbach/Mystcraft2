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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkullGameTests {
    private static final BlockPos SUPPORT=new BlockPos(1,1,1);
    private SkullGameTests() {}
    private static void check(GameTestHelper h,boolean value,String message) { if (!value) h.fail(message); }
    public static final class DenyPlacement {
        private final Player player;
        private DenyPlacement(Player player) { this.player=player; }
        @SubscribeEvent public void cancel(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity()==player && event.getPlacedBlock().is(SkullRegistry.DISPLAY.get())) event.setCanceled(true);
        }
    }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
    public static void deniedPlacementRestoresItemAndBlock(GameTestHelper h) {
        h.setBlock(SUPPORT,Blocks.STONE); BlockPos support=h.absolutePos(SUPPORT);
        var player=h.makeMockPlayer(); player.setPos(support.getX()-1,support.getY(),support.getZ()-1);
        ItemStack skull=new ItemStack(JurassicContent.BONES.get(Species.TYRANNOSAURUS).get("skull").get());
        skull.getOrCreateTag().putString("marker","preserved");
        player.setItemInHand(InteractionHand.MAIN_HAND,skull);
        var click=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support),Direction.UP,support,false));
        var existing=h.getEntities(EntityType.ITEM,SUPPORT.above(),2).stream().map(item -> item.getUUID()).collect(java.util.stream.Collectors.toSet());
        DenyPlacement listener=new DenyPlacement(player);
        InteractionResult result;
        MinecraftForge.EVENT_BUS.register(listener);
        try { result=skull.useOn(click); } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        check(h,result==InteractionResult.FAIL,"cancelled placement must fail");
        check(h,h.getLevel().getBlockState(support.above()).isAir(),"cancelled placement must restore the block");
        check(h,skull.getCount()==1 && "preserved".equals(skull.getTag().getString("marker")),"cancelled placement must restore the original item");
        check(h,h.getEntities(EntityType.ITEM,SUPPORT.above(),2).stream().noneMatch(item -> !existing.contains(item.getUUID())),"rollback must not drop an extra skull");
        h.succeed();
    }
    @GameTest(template="cleaning_station_test",templateNamespace=PortMod.MOD_ID)
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
}
