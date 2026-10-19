package ports.jurassicraft.bugs;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

/** Preserve the original chances of finding insects while harvesting vegetation. */
public final class BugHarvest {
    private BugHarvest() {}
    public static void init() {
        PlayerBlockBreakEvents.AFTER.register((level,player,pos,state,blockEntity) -> {
            if (level.isClientSide || player.isCreative() || !level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) return;
            Item insect=null;
            if (state.is(Blocks.HAY_BLOCK) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || state.is(Blocks.COCOA)) {
                if (level.random.nextInt(2)==0) insect=level.random.nextBoolean() ? BugRegistry.COCKROACHES.get() : BugRegistry.MEALWORM_BEETLES.get();
            } else if (state.is(Blocks.GRASS_BLOCK)) {
                if (level.random.nextInt(2)==0 && level.random.nextInt(6)==0) insect=BugRegistry.CRICKETS.get();
            } else if (state.is(Blocks.GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
                if (level.random.nextInt(2)==0 && level.random.nextInt(5)==0) insect=BugRegistry.CRICKETS.get();
            }
            if (insect!=null) Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(insect));
        });
    }
}
