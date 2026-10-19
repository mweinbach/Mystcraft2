package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageCollectionMenu;

/** Classic176-wide,132-high page surface with original desk inventory art below it. */
public final class PageCollectionScreen extends AbstractContainerScreen<PageCollectionMenu> {
    private EditBox search;
    private boolean alphabetical, showAll;
    private final ClassicPageScroll scroll = new ClassicPageScroll();
    private int dragFrom=-1;
    private boolean dragging;
    private double pressX,pressY;
    private List<PageBrowserIndex.Entry> visible=List.of();
    public PageCollectionScreen(PageCollectionMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);alphabetical=menu.collection().getItem() instanceof PageCollectionItem item && !item.ordered();imageWidth=176;imageHeight=231;inventoryLabelY=123;
    }
    @Override protected void init() {
        super.init();String old=search==null?"":search.getValue();
        search=new EditBox(font,leftPos+40,topPos+1,132,16,Component.translatable("gui.mystcraft.pages.search"));
        search.setMaxLength(80);search.setValue(old);search.setSuggestion(old.isEmpty()?Component.translatable("gui.mystcraft.pages.search").getString():null);
        search.setResponder(value->{scroll.reset();search.setSuggestion(value.isEmpty()?Component.translatable("gui.mystcraft.pages.search").getString():null);});
        addRenderableWidget(search);
        addRenderableWidget(new ClassicPageWidgets.SourceButton(leftPos,topPos,18,18,Component.literal("AZ"),b->{alphabetical=!alphabetical;scroll.reset();},()->alphabetical));
        addRenderableWidget(new ClassicPageWidgets.SourceButton(leftPos+18,topPos,18,18,Component.literal("ALL"),b->{showAll=!showAll;scroll.reset();},()->showAll));
        button(8,"<",PageCollectionMenu.PREVIOUS);button(32,">",PageCollectionMenu.NEXT);
        button(56,"+",PageCollectionMenu.INSERT);button(80,"−",PageCollectionMenu.EXTRACT);
        button(128,"↑",PageCollectionMenu.UP);button(152,"↓",PageCollectionMenu.DOWN);
    }
    private void button(int x,String label,int command){addRenderableWidget(new ClassicPageWidgets.SourceButton(leftPos+x,topPos+213,20,18,Component.literal(label),b->send(command),()->false));}
    private void send(int command){net.mystcraft.integration.network.MenuActions.send(menu.containerId,command);}
    private boolean grouped(){return menu.collection().getItem() instanceof PageCollectionItem item && !item.ordered();}
    private void updateView(){visible=PageBrowserIndex.entries(menu.pages(),search.getValue(),alphabetical,grouped(),showAll);scroll.layout(visible.size(),113);}
    private PageBrowserIndex.Entry entry(int cell){return cell>=0 && cell<visible.size()?visible.get(cell):null;}
    private int cell(double x,double y){return scroll.cell(x,y,leftPos,topPos+19,113);}
    @Override protected void renderBg(PoseStack pose,float partial,int mouseX,int mouseY){
        updateView();RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,ClassicPageSurface.DESK);RenderSystem.setShaderColor(1,1,1,1);
        blit(pose,leftPos,topPos+133,0,82,176,80);fill(pose,leftPos,topPos+19,leftPos+155,topPos+132,0xaa000000);
        ClassicPageWidgets.slider(pose,scroll,leftPos+155,topPos+19,113);
        ClassicPageWidgets.clip(leftPos,topPos+19,155,112);
        try{for(int row=scroll.pixels()/41;row<=(scroll.pixels()+112)/41;row++)for(int col=0;col<5;col++){
            var page=entry(row*5+col);if(page==null)continue;int x=leftPos+col*31,y=topPos+19+row*41-scroll.pixels();
            ClassicPageSurface.draw(pose,page,x,y,30,40);if(grouped()&&page.count()>1)font.drawShadow(pose,""+page.count(),x+1,y+31,0xffffff);
        }}finally{RenderSystem.disableScissor();}
    }
    @Override protected void renderLabels(PoseStack pose,int mouseX,int mouseY){}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0 && x>=leftPos+155 && x<leftPos+175 && y>=topPos+19 && y<topPos+132){scroll.begin(y,topPos+19,113);return true;}
        var page=entry(cell(x,y));
        if(cell(x,y)>=0){
            if(!menu.getCarried().isEmpty()) {if(page!=null&&page.index()>=0)send(page.index());else send(PageCollectionMenu.APPEND);send(button==1?PageCollectionMenu.INSERT:PageCollectionMenu.INSERT_ALL);}
            else if(page!=null&&page.index()>=0){
                send(page.index());
                if(button==0 && hasAltDown()&&!grouped()&&!alphabetical&&search.getValue().isBlank()){dragFrom=page.index();pressX=x;pressY=y;dragging=false;}
                else if(button==0)send(hasShiftDown()?PageCollectionMenu.EXTRACT_ALL:PageCollectionMenu.EXTRACT);
            }
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&scroll.dragging()){scroll.drag(y,topPos+19,113);return true;}if(button==0&&dragFrom>=0){dragging|=Math.abs(x-pressX)+Math.abs(y-pressY)>4;return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&scroll.dragging()){scroll.end();return true;}if(button==0&&dragFrom>=0){var to=entry(cell(x,y));if(dragging&&to!=null&&to.index()>=0&&to.index()!=dragFrom)send(PageCollectionMenu.REORDER_BASE+dragFrom*PageCollectionItem.MAX_PAGES+to.index());dragFrom=-1;dragging=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double delta){if(x>=leftPos&&x<leftPos+176&&y>=topPos+19&&y<topPos+132){scroll.wheel(delta);updateView();return true;}return super.mouseScrolled(x,y,delta);}
    @Override public void containerTick(){super.containerTick();search.tick();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(search.isFocused()&&key!=256){search.keyPressed(key,scan,modifiers);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(PoseStack pose,int mouseX,int mouseY,float partial){renderBackground(pose);super.render(pose,mouseX,mouseY,partial);renderTooltip(pose,mouseX,mouseY);var page=entry(cell(mouseX,mouseY));if(page!=null)renderTooltip(pose,page.page(),mouseX,mouseY);}
}
