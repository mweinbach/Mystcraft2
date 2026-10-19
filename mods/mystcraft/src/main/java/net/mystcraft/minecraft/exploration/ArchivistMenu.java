package net.mystcraft.minecraft.exploration;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.ExplorationRegistry;

/** Source shop cards buy directly from player currency; clients send only a bounded card index. */
public final class ArchivistMenu extends AbstractContainerMenu {
    private final Villager villager;
    private final Inventory playerInventory;
    private final SimpleContainer display=new SimpleContainer(3);
    private final ContainerData data=new SimpleContainerData(6);
    private final ArchivistStock.State stock;
    public ArchivistMenu(int id,Inventory inventory,FriendlyByteBuf buffer){this(id,inventory,find(inventory,buffer.readInt()));}
    private static Villager find(Inventory inventory,int id){return inventory.player.level.getEntity(id) instanceof Villager villager?villager:null;}
    public ArchivistMenu(int id,Inventory inventory,Villager villager){
        super(ExplorationRegistry.ARCHIVIST_MENU.get(),id);this.villager=villager;this.playerInventory=inventory;
        stock=!inventory.player.level.isClientSide&&villager!=null?ArchivistStock.get(villager).inventory(villager):null;
        for(int i=0;i<3;i++)addSlot(new Slot(display,i,42+i*48,12){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+col*18,99+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,157));
        addDataSlots(data);refresh();
    }
    public ItemStack page(int index){return display.getItem(index);}
    public int price(int index){return index==3?20:data.get(index+2);}
    public int boosters(){return data.get(0);}
    public int emeralds(){return data.get(1);}
    public boolean catchingUp(){return data.get(5)!=0;}
    public Villager villager(){return villager;}
    private void refresh(){if(stock==null)return;stock.simulate(villager);for(int i=0;i<3;i++){display.setItem(i,stock.page(villager,i));data.set(i+2,stock.price(villager,i));}data.set(0,stock.boosters());data.set(1,ArchivistPurchases.emeralds(playerInventory));data.set(5,stock.catchingUp(villager.level.getGameTime())?1:0);}
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean clickMenuButton(Player player,int index){
        if(player.level.isClientSide||player.isSpectator()||!player.isAlive()||playerInventory.player!=player||stock==null||index<0||index>3||!stillValid(player))return false;
        stock.simulate(villager);
        if(stock.catchingUp(villager.level.getGameTime()))return false;
        ItemStack result=index==3?(stock.boosters()>0?new ItemStack(MystcraftItems.BOOSTER.get()):ItemStack.EMPTY):stock.page(villager,index);
        int price=index==3?20:stock.price(villager,index);
        if(!result.isEmpty())result.setCount(1);
        if(!ArchivistPurchases.buy(playerInventory,result,price))return false;
        stock.consume(index);broadcastChanges();return true;
    }
    @Override public boolean stillValid(Player player){return player.level.isClientSide||villager!=null&&VillageArchivistPools.settings().archivistEnabled()&&player.level==villager.level&&(villager.getTradingPlayer()==null||villager.getTradingPlayer()==player)&&villager.isAlive()&&!villager.isBaby()&&villager.getVillagerData().getProfession()==ExplorationRegistry.ARCHIVIST.get()&&player.distanceToSqr(villager)<=64;}
    @Override public ItemStack quickMoveStack(Player player,int slotIndex){
        if(slotIndex<3||slotIndex>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(slotIndex);if(!slot.hasItem())return ItemStack.EMPTY;ItemStack stack=slot.getItem(),copy=stack.copy();
        if(slotIndex<30?!moveItemStackTo(stack,30,39,false):!moveItemStackTo(stack,3,30,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return copy;
    }
    @Override public void removed(Player player){super.removed(player);if(villager!=null&&villager.getTradingPlayer()==player)villager.setTradingPlayer(null);}
}
