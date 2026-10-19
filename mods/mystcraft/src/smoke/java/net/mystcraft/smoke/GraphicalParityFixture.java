package net.mystcraft.smoke;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.network.BookDisplayNetwork;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.age.AgeServerLevel;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Explicit, bounded disposable-world fixtures; never bundled into the production mod. */
public final class GraphicalParityFixture {
    private static final BlockPos LECTERN = new BlockPos(20,100,0);
    private GraphicalParityFixture() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=Commands.literal("mystcraft_parity_gui").requires(source->source.hasPermission(4));
        for(String action:List.of("folder","archivist","lectern_page","lectern_map","display","skybook","skybook_nohorizon","sunset","below","above"))
            root.then(Commands.literal(action).then(Commands.argument("player",EntityArgument.player()).executes(context->{
                try { String result=run(action,EntityArgument.getPlayer(context,"player"));
                    context.getSource().sendSuccess(Component.literal("MYSTCRAFT_PARITY_GUI "+result),false);return 1;
                } catch(Throwable failure){context.getSource().sendFailure(Component.literal("MYSTCRAFT_PARITY_GUI_FAILURE "+action+" "+failure));return 0;}
            })));
        dispatcher.register(root);
    }
    private static String run(String action,ServerPlayer player) {
        player.closeContainer();
        if(action.equals("folder")) {
            ItemStack folder=new ItemStack(WritingDeskRegistry.FOLDER.get());List<CompoundTag> pages=new ArrayList<>();
            for(var definition:SymbolRegistry.all()) {CompoundTag page=new CompoundTag();page.putString("symbol",definition.legacyId());page.putInt("GuiOrder",pages.size());pages.add(page);}
            PageCollectionItem.setPages(folder,pages);folder.setHoverName(Component.literal("145 ordered source pages"));
            hold(player,folder);folder.use(player.level,player,InteractionHand.MAIN_HAND);
            return "FOLDER_READY pages="+pages.size()+" first/last_order_NBT_preserved; scroll_to_end_then_back";
        }
        if(action.startsWith("skybook")) {
            List<CompoundTag> pages=new ArrayList<>();pages.add(PageData.payload(new ItemStack(MystcraftItems.LINKING_PANEL.get())));
            add(pages,"terrainflat","biome/minecraft/plains","bioconsingle","weatheroff","lightingnormal","modcolorcyan","colorsky",
                    "modfull","modnorth","modnoon","sunnormal","moondark","starsdark","featurelargedummy","featuremediumdummy","featuresmalldummy");
            if(action.endsWith("nohorizon"))add(pages,"nohorizon");
            ItemStack book=AgeBookData.create("Sky audit "+action,pages);CompoundTag props=book.getOrCreateTag().getCompound("Props");props.putString("Seed","22345");book.getOrCreateTag().put("Props",props);
            hold(player,book);return "SKY_BOOK_READY activate_panel_then_sunset/above/below; seed22345";
        }
        if(List.of("sunset","below","above").contains(action)) {
            if(!(player.level instanceof AgeServerLevel))throw new IllegalStateException("Enter fixture Age first; overworld time is never changed");
            player.getLevel().setDayTime(6000);double horizon=net.mystcraft.minecraft.age.AgeManager.visualSettings(player.getLevel()).environment().horizon();
            double y=action.equals("below")?horizon-8:horizon+8;
            player.teleportTo(player.getLevel(),player.getX(),y,player.getZ(),90,action.equals("below")?35:0);
            return "SKY_CAMERA_READY age_only_time6000 y="+y+" yaw90 pitch="+player.getXRot();
        }
        var level=player.server.overworld();
        level.getChunk(LECTERN);for(int x=18;x<=24;x++)for(int z=-4;z<=2;z++)level.setBlock(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState(),2);
        player.teleportTo(level,20.5,100,-2.5,0,20);
        if(action.equals("archivist")) {
            // Repeated fixture use replaces only its own named entity, never ordinary villagers.
            for(var entity:level.getEntitiesOfClass(Villager.class,new net.minecraft.world.phys.AABB(18,99,-4,25,105,3)))
                if(entity.getTags().contains("MystcraftGuiFixture"))entity.discard();
            Villager villager=EntityType.VILLAGER.create(level);if(villager==null)throw new IllegalStateException("Villager factory rejected");
            villager.setVillagerData(villager.getVillagerData().setProfession(ExplorationRegistry.ARCHIVIST.get()));villager.setVillagerXp(1);
            villager.setNoAi(true);villager.setPersistenceRequired();villager.addTag("MystcraftGuiFixture");villager.moveTo(21.5,100,.5,180,0);
            level.addFreshEntity(villager);player.getInventory().add(new ItemStack(Items.EMERALD,32));player.getInventory().add(new ItemStack(Items.EMERALD_BLOCK,2));
            player.inventoryMenu.broadcastChanges();return "ARCHIVIST_READY right_click_adult_at21.5,100,.5; emerald32+blocks2_added";
        }
        if(!action.equals("display")) {
            level.setBlock(LECTERN,ExplorationRegistry.LECTERN.get().defaultBlockState(),3);
            if(!(level.getBlockEntity(LECTERN) instanceof MystLecternBlockEntity lectern))throw new IllegalStateException("Lectern missing");
            ItemStack content;
            if(action.equals("lectern_map")) {
                content=MapItem.create(level,20,0,(byte)0,true,false);var data=MapItem.getSavedData(content,level);
                if(data==null)throw new IllegalStateException("Map data missing");
                for(int y=0;y<128;y++)for(int x=0;x<128;x++)data.setColor(x,y,(byte)(4*((x/16+y/16)%15+1)+2));
            } else {content=new ItemStack(AgeRegistry.PAGE.get());PageData.setLegacySymbol(content,"mystcraft:sunnormal");}
            lectern.setBook(content);
            return "LECTERN_READY "+action+" at20,100,0; right_click_for_display";
        }
        if(!(level.getBlockEntity(LECTERN) instanceof MystLecternBlockEntity lectern))throw new IllegalStateException("Seed lectern first");
        BookDisplayNetwork.open(player,lectern);return "DISPLAY_READY exact_lectern_content";
    }
    private static void hold(ServerPlayer player,ItemStack stack) {
        int slot=player.getInventory().selected;
        ItemStack old=player.getInventory().getItem(slot);
        if(!old.isEmpty()){int free=player.getInventory().getFreeSlot();if(free<0)throw new IllegalStateException("Make inventory room before fixture");player.getInventory().setItem(free,old.copy());}
        player.getInventory().setItem(slot,stack);player.connection.send(new ClientboundSetCarriedItemPacket(slot));player.inventoryMenu.broadcastChanges();
    }
    private static void add(List<CompoundTag> pages,String... ids){for(String id:ids){CompoundTag page=new CompoundTag();page.putString("symbol","mystcraft:"+id);pages.add(page);}}
}
