package net.mystcraft.smoke.forge;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.minecraft.instability.DecayFallingBlockEntity;
import net.mystcraft.minecraft.instability.DecayPropagation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** A genuine Forge spawn veto must leave the original arbitrary block and inventory intact. */
public final class ForgeBlackDecayVetoSmoke {
    private ForgeBlackDecayVetoSmoke() {}
    public static List<String> run(MinecraftServer server,ServerLevel ignored) {
        ServerLevel age=null;for(ServerLevel level:server.getAllLevels())if(DecayPropagation.isAge(level)){age=level;break;}
        if(age==null)throw new AssertionError("Age fixture required");
        BlockPos pos=new BlockPos(3370,220,3370); BlockState old=age.getBlockState(pos),below=age.getBlockState(pos.below());
        if(age.getBlockEntity(pos)!=null || age.getBlockEntity(pos.below())!=null)throw new AssertionError("veto fixture cannot overwrite block entities");
        Listener veto=new Listener();MinecraftForge.EVENT_BUS.register(veto);
        try {
            age.setBlock(pos.below(),Blocks.AIR.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
            age.setBlock(pos,Blocks.CHEST.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
            ChestBlockEntity chest=(ChestBlockEntity)age.getBlockEntity(pos);chest.setItem(2,new ItemStack(Items.EMERALD,9));
            if(DecayFallingBlockEntity.drop(age,pos) || veto.calls!=1 || age.getBlockEntity(pos)!=chest
                    || chest.getItem(2).getCount()!=9 || !chest.getItem(2).is(Items.EMERALD))
                throw new AssertionError("real falling entity insertion veto must preserve original chest identity and all contents");
            return List.of("black decay veto: real Forge falling-entity insertion cancellation preserves original chest and nine emeralds");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
            age.removeBlockEntity(pos);age.setBlock(pos,old,DecayPropagation.CONVERSION_FLAGS);age.setBlock(pos.below(),below,DecayPropagation.CONVERSION_FLAGS);
        }
    }
    public static final class Listener {
        int calls;
        @SubscribeEvent public void join(EntityJoinLevelEvent event){if(event.getEntity() instanceof DecayFallingBlockEntity){calls++;event.setCanceled(true);}}
    }
}
