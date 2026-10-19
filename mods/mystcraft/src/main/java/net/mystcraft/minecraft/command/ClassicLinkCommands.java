package net.mystcraft.minecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.minecraft.age.AgeAllocation;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.linking.LinkPermissions;
import net.mystcraft.minecraft.linking.LinkingRules;
import net.mystcraft.minecraft.writing.PageData;

/** Operator-only classic names; modern registry keys and canonical Age numbers replace legacy integer IDs. */
public final class ClassicLinkCommands {
    private ClassicLinkCommands(){}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("myst-permissions").requires(source->source.hasPermission(4))
                .then(Commands.argument("arguments",StringArgumentType.greedyString()).executes(context->permissions(context.getSource(),StringArgumentType.getString(context,"arguments")))));
        dispatcher.register(Commands.literal("tpx").requires(source->source.hasPermission(2))
                .then(Commands.argument("arguments",StringArgumentType.greedyString()).executes(context->travel(context.getSource(),StringArgumentType.getString(context,"arguments")))));
        dispatcher.register(Commands.literal("myst-create").requires(source->source.hasPermission(4))
                .executes(context->create(context.getSource(),"Age"))
                .then(Commands.argument("title",StringArgumentType.greedyString()).executes(context->create(context.getSource(),StringArgumentType.getString(context,"title")))));
    }
    private static int permissions(CommandSourceStack source,String arguments)throws CommandSyntaxException{
        String[] args=arguments.trim().split("\\s+");
        if(args.length!=4 || !List.of("permit","restrict").contains(args[1]) || !List.of("entry","depart").contains(args[2]))
            throw error("Usage: /myst-permissions <player> permit|restrict entry|depart <dimension|Age number|all>");
        ResourceLocation dimension=args[3].equals("all")?null:permissionDimension(source,args[3]);
        LinkPermissions.get(source.getServer()).change(args[0],args[1].equals("permit"),args[2].equals("entry")?LinkPermissions.Direction.ENTRY:LinkPermissions.Direction.DEPART,dimension);
        source.sendSuccess(Component.literal("Set Mystcraft permissions: "+arguments),true);return 1;
    }
    private static ResourceLocation permissionDimension(CommandSourceStack source,String token)throws CommandSyntaxException{
        try{long number=Long.parseLong(token);if(number<1)throw error("Use a dimension registry key or positive canonical Age number");
            for(var age:AgeManager.data(source.getServer()).entries())if(age.number()==number)return age.dimensionId();
            return new ResourceLocation("mystcraft","age_"+number);
        }catch(NumberFormatException ignored){ResourceLocation key=ResourceLocation.tryParse(token);if(key==null)throw error("Invalid dimension registry key");return key;}
    }
    private static int travel(CommandSourceStack source,String arguments)throws CommandSyntaxException{
        String[] args=arguments.trim().split("\\s+");
        if(!List.of(1,2,4,5).contains(args.length))throw error("Usage: /tpx [player] <dimension|Age number|target player> [x y z]");
        boolean subjectGiven=args.length==2||args.length==5;int targetIndex=subjectGiven?1:0;
        ServerPlayer subject=subjectGiven?source.getServer().getPlayerList().getPlayerByName(args[0]):source.getPlayerOrException();
        if(subject==null)throw error("The subject player must be online");
        ServerPlayer targetPlayer=source.getServer().getPlayerList().getPlayerByName(args[targetIndex]);
        ServerLevel target=targetPlayer==null?dimension(source,args[targetIndex]):targetPlayer.getLevel();
        if(LinkingRules.dead(source.getServer(),target.dimension()))throw error("Target dimension is marked dead");
        Vec3 arrival=targetPlayer==null?LinkingRules.safeSpawn(target,subject):targetPlayer.position();
        float yaw=targetPlayer==null?subject.getYRot():targetPlayer.getYRot(),pitch=targetPlayer==null?subject.getXRot():targetPlayer.getXRot();
        if(args.length>=4)arrival=new Vec3(coordinate(args[targetIndex+1],subject.getX()),coordinate(args[targetIndex+2],subject.getY()),coordinate(args[targetIndex+3],subject.getZ()));
        if(arrival==null)throw error("Target has no safe spawn");
        var bounds=subject.getBoundingBox().move(arrival.subtract(subject.position()));
        if(arrival.y<target.getMinBuildHeight() || bounds.maxY>target.getMaxBuildHeight() || !target.getWorldBorder().isWithinBounds(bounds))throw error("Unsafe operator travel destination");
        target.getChunk(((int)Math.floor(arrival.x))>>4,((int)Math.floor(arrival.z))>>4);
        if(!target.noCollision(subject,bounds)||target.containsAnyLiquid(bounds))throw error("Unsafe operator travel destination");
        // Explicit operator command bypasses only Mystcraft permissions; native loader travel vetoes remain active.
        Entity moved=GameplayPlatform.transfer(subject,target,arrival,yaw,pitch);
        if(moved!=subject)throw error("Operator travel was vetoed");
        LinkingRules.onDimensionChanged(subject);subject.setDeltaMovement(0,0,0);subject.fallDistance=0;
        source.sendSuccess(Component.literal("Moved "+subject.getGameProfile().getName()+" to "+target.dimension().location()),true);return 1;
    }
    private static double coordinate(String token,double base)throws CommandSyntaxException{
        try{boolean relative=token.startsWith("~");String number=relative?token.substring(1):token;double value=relative?base+(number.isEmpty()?0:Double.parseDouble(number)):Double.parseDouble(number);
            if(!Double.isFinite(value)||Math.abs(value)>30000000)throw new NumberFormatException();return value;
        }catch(NumberFormatException invalid){throw error("Coordinates must be finite numbers or relative ~ offsets");}
    }
    private static ServerLevel dimension(CommandSourceStack source,String token)throws CommandSyntaxException{
        ResourceLocation id=null;
        try{long number=Long.parseLong(token);for(var age:AgeManager.data(source.getServer()).entries())if(age.number()==number){id=age.dimensionId();break;}}
        catch(NumberFormatException ignored){id=ResourceLocation.tryParse(token);}
        if(id==null)throw error("Unknown dimension or canonical Age number: "+token);
        ServerLevel level=AgeManager.ensureLoaded(source.getServer(),ResourceKey.create(Registry.DIMENSION_REGISTRY,id));
        if(level==null)throw error("Unknown dimension: "+id);return level;
    }
    private static int create(CommandSourceStack source,String title)throws CommandSyntaxException{
        if(title.length()>256)throw error("Age title exceeds256 characters");
        long seed=source.getLevel().random.nextLong();CompoundTag panel=PageData.payload(new ItemStack(MystcraftItems.LINKING_PANEL.get()));
        try{
            var prepared=AgeAllocation.compile(source.getServer(),List.of(panel),seed);
            if(!prepared.compilation().successful())throw error("Cannot compile default Age: "+prepared.compilation().diagnostics());
            var entry=AgeManager.data(source.getServer()).allocatePrepared(seed,title,List.of(panel),prepared);
            source.getServer().overworld().getDataStorage().save();
            ServerLevel level=AgeManager.ensureLoaded(source.getServer(),ResourceKey.create(Registry.DIMENSION_REGISTRY,entry.dimensionId()));
            if(level==null)throw error("Age identity saved but runtime loading failed: "+entry.dimensionId());
            source.sendSuccess(Component.literal("Created Mystcraft Age "+entry.number()+": "+entry.dimensionId()+" ("+title+")"),true);return 1;
        }catch(RuntimeException failure){throw error("Cannot create Age: "+failure.getMessage());}
    }
    private static CommandSyntaxException error(String message){return new SimpleCommandExceptionType(Component.literal(message)).create();}
}
