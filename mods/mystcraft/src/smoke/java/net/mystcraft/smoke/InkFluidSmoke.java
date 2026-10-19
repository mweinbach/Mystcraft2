package net.mystcraft.smoke;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.mystcraft.integration.registry.InkRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.fluid.InkContainerAccess;
import net.mystcraft.minecraft.fluid.InkAutomationChecks;
import net.mystcraft.minecraft.writing.WritingDeskBlockEntity;
public final class InkFluidSmoke {
    public static List<String> run(MinecraftServer server,ServerLevel level){
        check(server.isSameThread(),"ink fluid smoke runs on server thread");var passed=new ArrayList<String>();
        ItemStack vials=new ItemStack(InkMixerRegistry.INK_VIAL.get(),3);CompoundTag marker=new CompoundTag();marker.putString("Smoke","preserve-original-stack");vials.setTag(marker);
        var drained=InkContainerAccess.drainOne(vials,1000,true).orElseThrow(()->new AssertionError("native vial extraction unavailable"));
        check(drained.amount()==1000&&drained.remainder().is(Items.GLASS_BOTTLE)&&drained.remainder().getCount()==1,"one vial contains exactlyonebucket and returnsonebottle");
        check(vials.getCount()==3&&vials.getTag().equals(marker),"preview extraction leavesoriginalstack/NBTunchanged");
        check(InkContainerAccess.drainOne(vials,999,false).isEmpty(),"indivisible vial rejects partialdrain");
        var bucket=InkContainerAccess.drainOne(new ItemStack(InkRegistry.BLACK_INK_BUCKET.get()),1000,true).orElseThrow();check(bucket.amount()==1000&&bucket.remainder().is(Items.BUCKET),"nativebucketcap returns1000andemptybucket");
        passed.add("Ink containers: native vial/bucket extraction returns exact1000mB and correct empty item; previews preserve stack/NBT and partial vials reject");
        BlockPos fluidPos=new BlockPos(32,220,32),deskPos=fluidPos.offset(2,0,0),mixerPos=fluidPos.offset(4,0,0),dispenserPos=fluidPos.offset(-1,0,0),lavaPos=fluidPos.offset(0,0,1);var oldFluid=level.getBlockState(fluidPos);var oldDesk=level.getBlockState(deskPos);var oldMixer=level.getBlockState(mixerPos);var oldDispenser=level.getBlockState(dispenserPos);var oldLava=level.getBlockState(lavaPos);
        BlockPos farmPos=fluidPos.offset(0,0,-1),roofPos=farmPos.above(2);var oldFarm=level.getBlockState(farmPos);var oldFarmAbove=level.getBlockState(farmPos.above());var oldRoof=level.getBlockState(roofPos);
        try{
            level.setBlock(fluidPos,InkRegistry.BLACK_INK_BLOCK.get().defaultBlockState(),18);
            var state=level.getFluidState(fluidPos);check(state.isSource()&&state.getAmount()==8&&state.getType()==InkRegistry.BLACK_INK.get(),"worldsourcehasnativeblackinkidentity/eightlevels");
            var recovered=((BucketPickup)level.getBlockState(fluidPos).getBlock()).pickupBlock(level,fluidPos,level.getBlockState(fluidPos));
            check(recovered.is(InkRegistry.BLACK_INK_BUCKET.get())&&level.getFluidState(fluidPos).isEmpty(),"worldbucketpickupreturnsinkratherthanwaterandremovessource");
            var dispenserState=net.minecraft.world.level.block.Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,net.minecraft.core.Direction.EAST);
            level.setBlock(dispenserPos,dispenserState,18);var dispenser=(net.minecraft.world.level.block.entity.DispenserBlockEntity)level.getBlockEntity(dispenserPos);
            dispenser.setItem(0,new ItemStack(InkRegistry.BLACK_INK_BUCKET.get()));
            ((net.minecraft.world.level.block.DispenserBlock)net.minecraft.world.level.block.Blocks.DISPENSER).tick(dispenserState,level,dispenserPos,level.random);
            check(level.getFluidState(fluidPos).getType()==InkRegistry.BLACK_INK.get()&&dispenser.getItem(0).is(Items.BUCKET),"realdispenserplacesblackinkandretainsemptybucket");
            ((net.minecraft.world.level.block.DispenserBlock)net.minecraft.world.level.block.Blocks.DISPENSER).tick(dispenserState,level,dispenserPos,level.random);
            check(level.getFluidState(fluidPos).isEmpty()&&dispenser.getItem(0).is(InkRegistry.BLACK_INK_BUCKET.get()),"realemptybucketdispenserrecoversblackinksource");
            passed.add("Ink dispensers: actual filled-bucket placement and empty-bucket recovery preserve fluid and one physical container");
            dispenser.clearContent();
            level.setBlock(deskPos,WritingDeskRegistry.WRITING_DESK.get().defaultBlockState(),18);
            var desk=(WritingDeskBlockEntity)level.getBlockEntity(deskPos);check(desk!=null,"nativeinkwellfixtureexists");
            check(desk.fillInk(137,false)==137&&desk.inkAmount()==0,"sharedsimulationdoesnotfillinkwell");
            check(desk.fillInk(137,true)==137&&desk.drainInk(50,true)==50&&desk.inkAmount()==87,"sharedpartialinkconservation");
            desk.setInkAmount(0,false);InkAutomationChecks.run(level,deskPos);
            desk.setInkAmount(0,false);desk.inventory.setItem(WritingDeskBlockEntity.INK_VIAL,new ItemStack(InkRegistry.BLACK_INK_BUCKET.get()));
            WritingDeskBlockEntity.tick(level,deskPos,level.getBlockState(deskPos),desk);
            check(desk.inkAmount()==1000&&desk.inventory.getItem(WritingDeskBlockEntity.INK_VIAL).isEmpty()&&desk.inventory.getItem(WritingDeskBlockEntity.BOTTLES).is(Items.BUCKET),"desk native bucket input conservesexact1000andbucket");
            level.setBlock(mixerPos,InkMixerRegistry.INK_MIXER.get().defaultBlockState(),18);var mixer=(net.mystcraft.minecraft.ink.InkMixerBlockEntity)level.getBlockEntity(mixerPos);
            mixer.inventory.setItem(0,new ItemStack(InkRegistry.BLACK_INK_BUCKET.get()));mixer.inventory.setItem(1,new ItemStack(Items.PAPER));
            net.mystcraft.minecraft.ink.InkMixerBlockEntity.tick(level,mixerPos,level.getBlockState(mixerPos),mixer);
            check(mixer.inventory.getItem(0).isEmpty()&&mixer.inventory.getItem(2).is(Items.BUCKET)&&!mixer.takePanel(1).isEmpty()&&mixer.inventory.getItem(1).isEmpty(),"mixer native bucket paysforonepanelandreturnsexactbucket");
            passed.add("Ink machines: native bucket input pays exact ink at desk and mixer and preserves corresponding bucket remainder");
            passed.add("Ink fluid: real source pickup yields black-ink bucket, and native inkwell transactions simulate/commit/rollback exact fractional ink");
            // Classic ink constructed a distinct MaterialLiquid(MapColor.WATER), not Material.WATER.
            // Native 1.12 lava mixing tested Material.WATER identity, so ink must not make obsidian.
            level.setBlock(fluidPos,InkRegistry.BLACK_INK_BLOCK.get().defaultBlockState(),18);
            level.setBlock(lavaPos,net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(),18);
            check(level.getBlockState(lavaPos).is(net.minecraft.world.level.block.Blocks.LAVA),"classic custom ink must not solidify adjacent source lava; observed="+level.getBlockState(lavaPos));
            check(level.getFluidState(fluidPos).getType()==InkRegistry.BLACK_INK.get(),"lava contact must not replace ink with vanilla water/stone");
            passed.add("Ink reactions: distinct classic ink material does not turn adjacent source lava into obsidian");
            check(!level.getFluidState(fluidPos).is(net.minecraft.tags.FluidTags.WATER),"ink must not acquire global water chemistry/farmland membership");
            level.setBlock(farmPos.above(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),18);
            level.setBlock(roofPos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),18);
            var farmland=net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,1);
            level.setBlock(farmPos,farmland,18);
            ((net.minecraft.world.level.block.FarmBlock)net.minecraft.world.level.block.Blocks.FARMLAND).randomTick(farmland,level,farmPos,level.random);
            check(level.getBlockState(farmPos).is(net.minecraft.world.level.block.Blocks.FARMLAND)&&level.getBlockState(farmPos).getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE)==0,"classic ink must not irrigate adjacent farmland");
            passed.add("Ink material: absent from global water tag and actual roofed farmland random tick dries beside ink");
        }finally{level.setBlock(fluidPos,oldFluid,18);level.setBlock(deskPos,oldDesk,18);level.setBlock(mixerPos,oldMixer,18);level.setBlock(dispenserPos,oldDispenser,18);level.setBlock(lavaPos,oldLava,18);level.setBlock(farmPos,oldFarm,18);level.setBlock(farmPos.above(),oldFarmAbove,18);level.setBlock(roofPos,oldRoof,18);}
        return passed;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private InkFluidSmoke(){}
}
