package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.mystcraft.minecraft.instability.*;

/** Real loaded-chunk profiling, deterministic scoring/decks, crumble and arbitrary-BE falling lifecycle. */
public final class InstabilitySmoke {
    private InstabilitySmoke() {}
    public static List<String> run(MinecraftServer server,ServerLevel origin) {
        List<String> passed=new ArrayList<>();
        InstabilityProfile actual=new InstabilityProfile();
        var chunk=origin.getChunk(32>>4,32>>4);
        check(actual.profile(chunk) && !actual.profile(chunk) && actual.count()==1,"same loaded chunk must profile exactly once");
        check(actual.score(Map.of())==0,"one real profiled chunk must never enable block instability");
        check(InstabilityProfile.load(actual.save()).save().equals(actual.save()),"actual profiler maps and seen chunks must roundtrip");
        CompoundTag synthetic=new CompoundTag(); long[] seen=new long[401];for(int i=0;i<seen.length;i++)seen[i]=i;
        synthetic.putLongArray("Chunks",seen);synthetic.putIntArray("Solid",new int[65536]);
        CompoundTag blocks=new CompoundTag();int[] diamonds=new int[65536];diamonds[64<<8]=401;blocks.putIntArray("minecraft:diamond_ore",diamonds);synthetic.put("Blocks",blocks);synthetic.put("Watchers",InstabilityProfile.watcherSignature());
        InstabilityProfile fixture=InstabilityProfile.load(synthetic);
        check(fixture.score(null)==0 && fixture.score(Map.of("minecraft:diamond_ore",4000))==20,
                "source availability factors4000+20 and positive baseline subtraction must yield20 only with baseline");
        CompoundTag decks=InstabilityDecks.reconcile(99,new CompoundTag());
        check(decks.equals(InstabilityDecks.reconcile(123,decks)),"persisted deck order must survive changed seed/reconstruction");
        check(InstabilityDecks.levels(decks,0).isEmpty(),"zero legitimate score must select no provider");
        check(InstabilityDecks.score(false,999999,999999,999999,0,0,3)==0,"missing profile gates all source score components");
        passed.add("instability: real loaded chunk profiles once and survives NBT; synthetic formula fixture verifies source factors/baseline gate; seeded decks retain persisted order");
        ServerLevel age=null;for(ServerLevel level:server.getAllLevels())if(DecayPropagation.isAge(level)){age=level;break;}
        check(age!=null,"AgeSmoke must precede instability runtime smoke");
        BlockPos center=new BlockPos(3370,200,3370),chest=center.above(3);
        Map<BlockPos,BlockState> states=new LinkedHashMap<>();Map<BlockPos,CompoundTag> blockEntities=new LinkedHashMap<>();
        DecayFallingBlockEntity falling=null,restored=null;
        try {
            for(BlockPos pos:BlockPos.betweenClosed(center.offset(-2,-1,-2),center.offset(2,5,2))) {
                BlockPos at=pos.immutable();states.put(at,age.getBlockState(at));BlockEntity be=age.getBlockEntity(at);if(be!=null){blockEntities.put(at,be.saveWithFullMetadata());age.removeBlockEntity(at);}
                age.setBlock(at,pos.getY()==center.getY()-1?Blocks.OBSIDIAN.defaultBlockState():Blocks.AIR.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
            }
            age.setBlock(center,Blocks.DIAMOND_ORE.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
            check(InstabilityEffects.crumble(age,center) && age.getBlockState(center).is(Blocks.COAL_ORE),"source crumble must convert actual diamond ore to coal ore");
            age.setBlock(center,Blocks.AIR.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
            age.setBlock(chest,Blocks.CHEST.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
            ChestBlockEntity inventory=(ChestBlockEntity)age.getBlockEntity(chest);inventory.setItem(4,new ItemStack(Items.DIAMOND,7));
            CompoundTag expected=inventory.saveWithFullMetadata();
            falling=DecayFallingBlockEntity.dropEntity(age,chest);
            check(falling!=null && age.isEmptyBlock(chest),"arbitrary chest state must enter custom falling entity before source removal");
            CompoundTag saved=((Entity)falling).saveWithoutId(new CompoundTag());
            restored=new DecayFallingBlockEntity(net.mystcraft.integration.registry.DecayFallingRegistry.FALLING.get(),age);((Entity)restored).load(saved);((Entity)falling).discard();
            check(age.addFreshEntity(restored),"saved falling entity must rejoin actual server level");
            check(((FallingBlockEntity)restored).getBlockState().is(Blocks.CHEST),"falling arbitrary state must survive entity NBT");
            for(int tick=0;tick<100 && !((Entity)restored).isRemoved();tick++)((Entity)restored).tick();
            BlockEntity landed=age.getBlockEntity(center);
            check(((Entity)restored).isRemoved() && landed instanceof ChestBlockEntity
                    && ((ChestBlockEntity)landed).getItem(4).is(Items.DIAMOND) && ((ChestBlockEntity)landed).getItem(4).getCount()==7,
                    "falling chest must place original inventory exactly once after entity save/reload");
            age.setBlock(center.east(),Blocks.WATER.defaultBlockState(),DecayPropagation.CONVERSION_FLAGS);
            check(BlackDecay.corrupt(age,center.east()) && age.isEmptyBlock(center.east()),"black horizontal corruption must erase liquids");
            passed.add("instability: actual source crumble mapping, black liquid erasure and falling chest save/reload preserve seven-diamond inventory exactly once");
        } finally {
            if(falling!=null)((Entity)falling).discard();if(restored!=null)((Entity)restored).discard();
            for(var entry:states.entrySet()){age.removeBlockEntity(entry.getKey());age.setBlock(entry.getKey(),entry.getValue(),DecayPropagation.CONVERSION_FLAGS);}
            for(var entry:blockEntities.entrySet()){BlockEntity be=BlockEntity.loadStatic(entry.getKey(),states.get(entry.getKey()),entry.getValue());if(be!=null)age.setBlockEntity(be);}
        }
        return passed;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
