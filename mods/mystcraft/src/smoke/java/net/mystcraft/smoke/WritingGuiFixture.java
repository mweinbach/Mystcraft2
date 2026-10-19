package net.mystcraft.smoke;

import com.mojang.brigadier.CommandDispatcher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.writing.DynamicSymbolPages;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.WritingDeskBlock;
import net.mystcraft.minecraft.writing.WritingDeskBlockEntity;

/** Explicit test-world fixtures for real GUI input; never included in production artifacts. */
public final class WritingGuiFixture {
    private static final BlockPos DESK=new BlockPos(1,100,0);
    private record Baseline(CompoundTag biome,CompoundTag fluid,ItemStack source,ItemStack book,ItemStack portable,ItemStack portableIdentity) {}
    private static final Map<UUID,Baseline> BASELINES=new LinkedHashMap<>();
    private WritingGuiFixture() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=Commands.literal("mystcraft_gui_fixture").requires(source->source.hasPermission(4));
        for(String action:List.of("seed","desk","collection","book","hold_biome","hold_fluid","world","check_write","blank","check_blank","check_portfolio","check_rename"))
            root.then(Commands.literal(action).then(Commands.argument("player",EntityArgument.player()).executes(context->{
                try {
                    ServerPlayer player=EntityArgument.getPlayer(context,"player");
                    String result=run(action,player);
                    context.getSource().sendSuccess(Component.literal("MYSTCRAFT_GUI_FIXTURE "+result),false);return 1;
                }catch(Throwable failure){context.getSource().sendFailure(Component.literal("MYSTCRAFT_GUI_FIXTURE_FAILURE "+action+" "+failure));return 0;}
            })));
        dispatcher.register(root);
    }
    private static String run(String action,ServerPlayer player) {
        ServerLevel level=player.server.overworld();
        if(action.equals("seed"))return seed(player,level);
        Baseline baseline=BASELINES.get(player.getUUID());check(baseline!=null,"Run seed first in this server process");
        WritingDeskBlockEntity desk=desk(level);
        switch(action) {
            case "desk" -> {player.closeContainer();player.teleportTo(level,1.5,100,-3.5,0,20);GameplayPlatform.openMenu(player,desk,DESK);}
            case "collection" -> {player.closeContainer();select(player,0);player.getItemInHand(InteractionHand.MAIN_HAND).use(player.getLevel(),player,InteractionHand.MAIN_HAND);}
            case "book" -> {player.closeContainer();select(player,3);LinkingNetwork.openBook(player,InteractionHand.MAIN_HAND);}
            case "hold_biome" -> {player.closeContainer();select(player,1);}
            case "hold_fluid" -> {player.closeContainer();select(player,2);}
            case "world" -> {player.closeContainer();player.teleportTo(level,3.5,100,-2.5,0,24);select(player,1);}
            case "check_write" -> {
                check(desk.ink()==950&&desk.inventory.getItem(1).getCount()==15,"Expected exactly50mB+1paper paid");
                check(PageCollectionItem.pages(desk.inventory.getItem(0)).equals(List.of(baseline.biome())),"Target must hold exactly one original dynamic-biome payload");
                immutableSources(desk,baseline);
                return "WRITE_PASS ink950 paper15 target1 exact_owned_dynamic_payload source_unchanged";
            }
            case "blank" -> {
                check(desk.ink()==950&&desk.inventory.getItem(1).getCount()==15,"Check paid folder write before blank stage");
                player.getInventory().setItem(6,desk.inventory.getItem(0).copy());
                ItemStack blank=new ItemStack(AgeRegistry.PAGE.get());blank.getOrCreateTag().putString("GuiBlankOwner","retained");
                desk.inventory.setItem(0,blank);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
            }
            case "check_blank" -> {
                ItemStack target=desk.inventory.getItem(0);
                check(desk.ink()==900&&desk.inventory.getItem(1).getCount()==15,"Blank page costs50mB and no extra paper");
                check(PageData.symbol(target).equals(PageData.symbol(baseline.biome()))&&target.hasTag()&&target.getTag().getString("GuiBlankOwner").equals("retained"),"Blank target symbol/extra NBT changed");
                immutableSources(desk,baseline);return "BLANK_PASS ink900 paper15 owned_dynamic_symbol extra_blank_NBT_retained";
            }
            case "check_portfolio" -> {
                ItemStack portable=player.getInventory().getItem(0);
                check(portable==baseline.portableIdentity()&&portable.getCount()==1,"Portable held stack identity changed");
                check(stock(PageCollectionItem.pages(portable)).equals(stock(PageCollectionItem.pages(baseline.portable()))),"Portable exact-NBT stock must return to four-biome/two-fluid payloads");
                check(player.containerMenu.getCarried().isEmpty(),"Return cursor stock before portfolio check");
                return "PORTFOLIO_PASS exact_six_owned_pages cursor_empty held_identity_retained";
            }
            case "check_rename" -> {
                ItemStack target=desk.inventory.getItem(0);check(target.getHoverName().getString().equals("GUI Verified"),"Type GUI Verified into target name field");
                check(PageCollectionItem.pages(target).equals(List.of(baseline.biome())),"Rename changed target page stock");
                immutableSources(desk,baseline);return "RENAME_PASS GUI_Verified target_page_retained source_unchanged";
            }
            default -> throw new IllegalArgumentException(action);
        }
        return action.toUpperCase()+"_READY "+player.getGameProfile().getName();
    }
    private static String seed(ServerPlayer player,ServerLevel level) {
        player.closeContainer();level.getChunk(0,0);level.getChunk(-1,-1);
        for(int x=-4;x<=7;x++)for(int z=-8;z<=4;z++) {
            for(int y=100;y<=104;y++) {
                BlockPos pos=new BlockPos(x,y,z);
                if(level.getBlockEntity(pos) instanceof WritingDeskBlockEntity old)old.inventory.clearContent();
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            }
            level.setBlock(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState(),2);
        }
        var state=WritingDeskRegistry.WRITING_DESK.get().defaultBlockState();
        level.setBlock(DESK.north(),state.setValue(WritingDeskBlock.FOOT,true),2);level.setBlock(DESK,state,3);
        WritingDeskBlockEntity desk=desk(level);
        CompoundTag biome=DynamicSymbolPages.choices(player.server.registryAccess()).stream()
                .filter(choice->choice.payload().getString("symbol").equals("mystcraft:biome/minecraft/plains"))
                .findFirst().orElseThrow().payload();
        biome.putString("GuiFixture","owned-biome");
        var nativeFluids=DynamicSymbolPages.fluidChoices();
        CompoundTag fluid;
        if(!nativeFluids.isEmpty())fluid=nativeFluids.get(0).payload();
        else {
            // Bare Minecraft has no eligible dynamic mod fluid; this explicit test-only native
            // water page exercises the same encoded fluid renderer without inventing a fluid.
            fluid=new CompoundTag();fluid.putString("symbol","mystcraft:fluid/minecraft/water");fluid.putString("Fluid","minecraft:water");
            fluid.put("BlockState",NbtUtils.writeBlockState(Blocks.WATER.defaultBlockState()));fluid.putBoolean("Sea",true);fluid.putBoolean("FluidMaterial",true);
        }
        fluid.putString("GuiFixture","owned-fluid");
        ItemStack source=collection(WritingDeskRegistry.PORTFOLIO.get(),List.of(biome,biome,biome,biome,fluid,fluid));
        ItemStack panel=new ItemStack(MystcraftItems.LINKING_PANEL.get());
        CompoundTag terrain=new CompoundTag();PageData.setLegacySymbol(terrain,"mystcraft:terrainvoid");
        ItemStack book=AgeBookData.create("Dynamic GUI Book",List.of(PageData.payload(panel),biome,fluid,terrain));
        check(!book.isEmpty(),"Real descriptive book fixture must be panel-first");
        desk.inventory.setItem(0,new ItemStack(WritingDeskRegistry.FOLDER.get()));
        desk.inventory.setItem(1,new ItemStack(Items.PAPER,16));desk.inventory.setItem(2,new ItemStack(InkMixerRegistry.INK_VIAL.get()));
        desk.inventory.setItem(5,source);desk.inventory.setItem(6,collection(WritingDeskRegistry.FOLDER.get(),List.of(terrain,biome)));
        desk.inventory.setItem(7,book.copy());
        WritingDeskBlockEntity.tick(level,DESK,((BlockEntity)desk).getBlockState(),desk);
        check(desk.ink()==1000,"Desk must start with paid vial volume");
        player.getInventory().clearContent();
        ItemStack portable=source.copy();player.getInventory().setItem(0,portable);
        player.getInventory().setItem(1,page(biome));player.getInventory().setItem(2,page(fluid));player.getInventory().setItem(3,book);
        player.getInventory().setItem(4,new ItemStack(AgeRegistry.PAGE.get()));player.getInventory().setItem(5,new ItemStack(WritingDeskRegistry.FOLDER.get()));
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(level,1.5,100,-3.5,0,20);select(player,0);
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
        ItemEntity loose=new ItemEntity(level,3.5,100.6,0.5,page(fluid));loose.setUnlimitedLifetime();loose.setPickUpDelay(32767);((Entity)loose).setNoGravity(true);level.addFreshEntity(loose);
        BASELINES.put(player.getUUID(),new Baseline(biome.copy(),fluid.copy(),source.copy(),book.copy(),portable.copy(),portable));
        while(BASELINES.size()>16)BASELINES.remove(BASELINES.keySet().iterator().next());
        return "SEED_READY desk1,100,0 ink1000 paper16 portfolio4biome2fluid fluid="+fluid.getString("Fluid")+" hotbar0portfolio1biome2fluid3book; search_Plains_then_rightclick_ownedpage_then_check_write";
    }
    private static WritingDeskBlockEntity desk(ServerLevel level){check(level.getBlockEntity(DESK) instanceof WritingDeskBlockEntity,"Fixture desk missing");return(WritingDeskBlockEntity)level.getBlockEntity(DESK);}
    private static void select(ServerPlayer player,int slot){player.getInventory().selected=slot;player.connection.send(new ClientboundSetCarriedItemPacket(slot));}
    private static ItemStack page(CompoundTag payload){ItemStack page=new ItemStack(AgeRegistry.PAGE.get());PageData.applyPayload(page,payload);return page;}
    private static ItemStack collection(net.minecraft.world.item.Item item,List<CompoundTag> pages){ItemStack result=new ItemStack(item);PageCollectionItem.setPages(result,pages);return result;}
    private static void immutableSources(WritingDeskBlockEntity desk,Baseline baseline){check(ItemStack.matches(desk.inventory.getItem(5),baseline.source())&&ItemStack.matches(desk.inventory.getItem(7),baseline.book()),"Owned library/book source changed");}
    private static Map<CompoundTag,Integer> stock(List<CompoundTag> pages){var result=new java.util.HashMap<CompoundTag,Integer>();for(CompoundTag page:pages)result.merge(page,1,Integer::sum);return result;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
