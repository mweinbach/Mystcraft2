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
import net.minecraft.world.item.ItemStack;
import net.mystcraft.minecraft.writing.PageCollectionItem;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.WritingDeskMenu;

/** Classic409x186 desk: four storage tabs,30x40 pages and original176x166 work panel. */
public final class WritingDeskScreen extends AbstractContainerScreen<WritingDeskMenu> {
    private EditBox search, targetName;
    private boolean alphabetical = true, showAll, syncingTitle;
    private final ClassicPageScroll scroll = new ClassicPageScroll();
    private int targetOffset, lastTab = -1, copyFrom = -1;
    private ItemStack previewBook = ItemStack.EMPTY;
    private int bookPage;
    private boolean targetBook() { return menu.targetItem().getItem() instanceof net.mystcraft.minecraft.item.LinkingBookItem; }
    private List<PageBrowserIndex.Entry> visible = List.of();
    public WritingDeskScreen(WritingDeskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 409; imageHeight = 186;
        inventoryLabelX = 241; inventoryLabelY = 94;
    }
    @Override protected void init() {
        super.init();
        String old = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + 98, topPos + 1, 132, 16, Component.translatable("gui.mystcraft.pages.search"));
        search.setMaxLength(80); search.setValue(old);
        search.setResponder(value -> { scroll.reset(); search.setSuggestion(value.isEmpty() ? Component.translatable("gui.mystcraft.pages.search").getString() : null); });
        search.setSuggestion(old.isEmpty() ? Component.translatable("gui.mystcraft.pages.search").getString() : null);
        addRenderableWidget(search);
        toggle(58, "AZ", b -> { alphabetical = !alphabetical; scroll.reset(); }, () -> alphabetical);
        toggle(76, "ALL", b -> { showAll = !showAll; scroll.reset(); }, () -> showAll);
        button(235, 0, 77, Component.translatable("gui.mystcraft.desk.copy"), b -> send(WritingDeskMenu.COPY));
        button(314, 0, 42, "↑", b -> send(WritingDeskMenu.MOVE_UP));
        button(358, 0, 42, "↓", b -> send(WritingDeskMenu.MOVE_DOWN));
        targetName = new EditBox(font, leftPos + 261, topPos + 81, 99, 14, Component.translatable("gui.mystcraft.desk.rename"));
        targetName.setMaxLength(21);
        targetName.setResponder(value -> {
            if (syncingTitle) return;
            send(WritingDeskMenu.TITLE_BEGIN);
            for (int i = 0; i < value.length(); i++) send(WritingDeskMenu.TITLE_CHAR | value.charAt(i));
            send(WritingDeskMenu.TITLE_APPLY);
        });
        addRenderableWidget(targetName);
        syncName();
    }
    private void button(int x, int y, int width, String text, Button.OnPress action) { button(x, y, width, Component.literal(text), action); }
    private void button(int x, int y, int width, Component text, Button.OnPress action) { addRenderableWidget(new ClassicPageWidgets.SourceButton(leftPos+x, topPos+y, width, 18, text, action, () -> false)); }
    private void toggle(int x,String text,Button.OnPress action,java.util.function.BooleanSupplier state) { addRenderableWidget(new ClassicPageWidgets.SourceButton(leftPos+x,topPos,18,18,Component.literal(text),action,state)); }
    private void send(int command) { net.mystcraft.integration.network.MenuActions.send(menu.containerId, command); }
    private boolean grouped() { return menu.sourceItem().getItem() instanceof PageCollectionItem item && !item.ordered(); }
    private void updateView() {
        if (lastTab != menu.sourceSlot()) { lastTab = menu.sourceSlot(); scroll.reset(); }
        visible = PageBrowserIndex.entries(menu.sourcePages(), search.getValue(), alphabetical, grouped(), showAll);
        scroll.layout(visible.size(),166);
        targetOffset = Math.max(0, Math.min(targetOffset, Math.max(0, menu.targetPages().size()-1)));
    }
    private PageBrowserIndex.Entry entry(int cell) { return cell >= 0 && cell < visible.size() ? visible.get(cell) : null; }
    private int sourceCell(double x, double y) {
        return scroll.cell(x,y,leftPos+58,topPos+20,166);
    }
    private int targetCell(double x, double y) {
        int gx = (int)x-leftPos-260, gy = (int)y-topPos-26;
        return gx>=0 && gx<101 && gy>=0 && gy<50 ? targetOffset+Math.max(0,gx-2)/35 : -1;
    }
    private void syncName() {
        ItemStack target = menu.targetItem();
        targetName.setEditable(!target.isEmpty() && !net.mystcraft.minecraft.writing.WritingDeskBlockEntity.isPage(target));
        String actual = target.hasCustomHoverName() ? target.getHoverName().getString() : "";
        if (!targetName.isFocused() && !targetName.getValue().equals(actual)) {
            syncingTitle = true; targetName.setValue(actual); syncingTitle = false;
        }
        if (target.isEmpty()) targetName.setFocus(false);
    }
    @Override public void containerTick() { super.containerTick(); search.tick(); targetName.tick(); syncName(); }
    @Override protected void renderBg(PoseStack pose, float partial, int mouseX, int mouseY) {
        updateView();
        RenderSystem.setShader(GameRenderer::getPositionTexShader); RenderSystem.setShaderTexture(0, ClassicPageSurface.DESK); RenderSystem.setShaderColor(1,1,1,1);
        blit(pose,leftPos+233,topPos+20,0,0,176,166);
        float topTint=menu.firstTab()==0?.4F:1;
        RenderSystem.setShaderColor(menu.sourceSlot()<menu.firstTab()?.5F:topTint,menu.sourceSlot()<menu.firstTab()?.5F:topTint,menu.sourceSlot()<menu.firstTab()?1:topTint,1);
        blit(pose,leftPos,topPos+20,0,203,58,9);
        for (int i=0;i<4;i++) {
            int tab=menu.firstTab()+i;
            RenderSystem.setShaderColor(tab==menu.sourceSlot()?.5F:1,tab==menu.sourceSlot()?.5F:1,1,1);
            blit(pose,leftPos,topPos+29+i*37,0,166,58,37);
        }
        float bottomTint=menu.firstTab()==21?.4F:1;
        RenderSystem.setShaderColor(menu.sourceSlot()>=menu.firstTab()+4?.5F:bottomTint,menu.sourceSlot()>=menu.firstTab()+4?.5F:bottomTint,menu.sourceSlot()>=menu.firstTab()+4?1:bottomTint,1);
        blit(pose,leftPos,topPos+177,0,212,58,9);RenderSystem.setShaderColor(1,1,1,1);
        fill(pose,leftPos+58,topPos+20,leftPos+213,topPos+186,0xaa000000);
        ClassicPageWidgets.slider(pose,scroll,leftPos+213,topPos+20,166);
        ClassicPageWidgets.clip(leftPos+58,topPos+20,155,165);
        try {
            int firstRow=scroll.pixels()/41, lastRow=(scroll.pixels()+165)/41;
            for(int row=firstRow;row<=lastRow;row++) for(int col=0;col<5;col++) {
                var page=entry(row*5+col); if(page==null) continue;
                int x=leftPos+58+col*31,y=topPos+20+row*41-scroll.pixels();
                ClassicPageSurface.draw(pose,page,x,y,30,40);
                if(grouped() && page.count()>1) font.drawShadow(pose,""+page.count(),x+1,y+31,0xffffff);
            }
        } finally { RenderSystem.disableScissor(); }
        if(targetBook() && !ItemStack.matches(previewBook,menu.targetItem())) {
            previewBook=menu.targetItem().copy();bookPage=0;send(WritingDeskMenu.BOOK_BEGIN);
        } else if(!targetBook()) previewBook=ItemStack.EMPTY;
        if(targetBook()) DeskBookPreview.draw(pose,font,menu.targetItem(),bookPage,leftPos+263,topPos+26);
        var targets=targetBook()?java.util.List.<net.minecraft.nbt.CompoundTag>of():menu.targetPages();
        if(PageCollectionItem.isCollection(menu.targetItem())) {
            fill(pose,leftPos+260,topPos+26,leftPos+361,topPos+76,0xaa000000);
            ClassicPageWidgets.clip(leftPos+261,topPos+26,99,50);
            try { for(int i=0;i<3 && targetOffset+i<targets.size();i++) {
                int index=targetOffset+i,x=leftPos+262+i*35,y=topPos+29;
                ClassicPageSurface.draw(pose,new PageBrowserIndex.Entry(index,PageBrowserIndex.page(targets.get(index)),1),x,y,33,44);
            }} finally { RenderSystem.disableScissor(); }
            fill(pose,leftPos+260,topPos+26,leftPos+270,topPos+76,targetOffset==0?0x33000000:0xaa000000);
            fill(pose,leftPos+351,topPos+26,leftPos+361,topPos+76,targetOffset>=targets.size()-1?0x33000000:0xaa000000);
        } else if(net.mystcraft.minecraft.writing.WritingDeskBlockEntity.isPage(menu.targetItem())) {
            ClassicPageSurface.draw(pose,new PageBrowserIndex.Entry(0,menu.targetItem(),1),leftPos+265,topPos+26,37,50);
        }
        int inkHeight=70*menu.ink()/1000;
        fill(pose,leftPos+366,topPos+97-inkHeight,leftPos+382,topPos+97,0xff111023);
        // The retained paid-copy recovery output is an explicit modern extra slot.
        fill(pose,leftPos+384,topPos+53,leftPos+402,topPos+71,0xff796c51);
    }
    @Override protected void renderLabels(PoseStack pose,int mouseX,int mouseY) {
        for(int i=0;i<4 && menu.firstTab()+i<25;i++) {
            int tab=menu.firstTab()+i;
            ClassicBookGlyphs.drawWord(pose,""+tab,8,32+i*37,19);
            ItemStack source=menu.getSlot(5+tab).getItem();
            if(!source.isEmpty()) {
                var name=source.getHoverName(); float scale=Math.min(1,58F/(font.width(name)+16));
                pose.pushPose();pose.translate(4,54+i*37,0);pose.scale(scale,scale,1);font.draw(pose,name,0,0,0x404040);pose.popPose();
            }
        }
        font.draw(pose,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0x404040);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0 && x>=leftPos+213 && x<leftPos+233 && y>=topPos+20 && y<topPos+186) {
            scroll.begin(y,topPos+20,166);return true;
        }
        int cell=sourceCell(x,y); var page=entry(cell);
        if(cell>=0) {
            if(!menu.getCarried().isEmpty()) { if(page!=null && page.index()>=0) send(WritingDeskMenu.SELECT_SOURCE_PAGE+page.index()); else send(WritingDeskMenu.SOURCE_APPEND); send(button==1?WritingDeskMenu.SOURCE_INSERT_ONE:WritingDeskMenu.SOURCE_INSERT); }
            else if(page!=null && page.index()>=0) {
                send(WritingDeskMenu.SELECT_SOURCE_PAGE+page.index());
                if(button==0) send(hasShiftDown()?WritingDeskMenu.SOURCE_TAKE_ALL:WritingDeskMenu.SOURCE_TAKE);
                else if(button==1) copyFrom=page.index();
            }
            return true;
        }
        if(PageCollectionItem.isCollection(menu.targetItem()) && y>=topPos+26 && y<topPos+76) {
            if(x>=leftPos+260 && x<leftPos+270) { targetOffset=Math.max(0,targetOffset-1);return true; }
            if(x>=leftPos+351 && x<leftPos+361) { targetOffset=Math.min(Math.max(0,menu.targetPages().size()-1),targetOffset+1);return true; }
        }
        int target=targetCell(x,y);
        if(target>=0 && targetBook()) {
            double bx=(x-leftPos-263)*327/90, by=(y-topPos-26)*199/50;
            if(bookPage==0 && bx>=173 && bx<305 && by>=20 && by<103) send(WritingDeskMenu.BOOK_LINK);
            else bookPage=Math.max(0,Math.min(DeskBookPreview.pages(menu.targetItem()).size(),bookPage+(bx<163?-1:1)));
            return true;
        }
        if(target>=0) {
            if(!menu.getCarried().isEmpty()) { send(WritingDeskMenu.TARGET_SELECT+Math.min(target,menu.targetPages().size())); send(button==1?WritingDeskMenu.INSERT:WritingDeskMenu.TARGET_INSERT_ALL); }
            else if(target<menu.targetPages().size()) { send(WritingDeskMenu.TARGET_SELECT+target); send(WritingDeskMenu.EXTRACT); }
            return true;
        }
        int tx=(int)x-leftPos,ty=(int)y-topPos;
        if(tx>=0 && tx<58) {
            if(ty>=20 && ty<29) { send(WritingDeskMenu.TAB_PREV);return true; }
            if(ty>=177 && ty<186) { send(WritingDeskMenu.TAB_NEXT);return true; }
            int tab=menu.firstTab()+(ty-29)/37;
            if(ty>=29 && ty<177 && tab<25 && !(tx>=35 && tx<55 && (ty-29)%37<23)) {
                if(menu.getCarried().isEmpty()) send(tab); else send(WritingDeskMenu.TAB_INSERT+tab*2+(button==1?1:0)); return true;
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(button==0 && scroll.dragging()) { scroll.end();return true; }
        if(button==1 && copyFrom>=0) {
            if(sourceCell(x,y)>=0 || targetCell(x,y)>=0) { send(WritingDeskMenu.SELECT_SOURCE_PAGE+copyFrom);send(WritingDeskMenu.WRITE); }
            copyFrom=-1;return true;
        }
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { if(button==0 && scroll.dragging()) { scroll.drag(y,topPos+20,166);return true; } return button==1 && copyFrom>=0 || super.mouseDragged(x,y,button,dx,dy); }
    @Override public boolean mouseScrolled(double x,double y,double delta) {
        if(x>=leftPos+58 && x<leftPos+233 && y>=topPos+20 && y<topPos+186) { scroll.wheel(delta);updateView();return true; }
        if(targetCell(x,y)>=0 && targetBook()) { bookPage=Math.max(0,Math.min(DeskBookPreview.pages(menu.targetItem()).size(),bookPage-(int)Math.signum(delta)));return true; }
        if(targetCell(x,y)>=0) { targetOffset-=(int)Math.signum(delta);updateView();return true; }
        return super.mouseScrolled(x,y,delta);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        EditBox focused=search.isFocused()?search:targetName.isFocused()?targetName:null;
        if(focused!=null && key!=256) { focused.keyPressed(key,scan,modifiers);return true; }
        if(key==265 || minecraft.options.keyUp.matches(key,scan)) { send(WritingDeskMenu.TAB_PREV);return true; }
        if(key==264 || minecraft.options.keyDown.matches(key,scan)) { send(WritingDeskMenu.TAB_NEXT);return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(PoseStack pose,int mouseX,int mouseY,float partial) {
        renderBackground(pose);super.render(pose,mouseX,mouseY,partial);renderTooltip(pose,mouseX,mouseY);
        if(mouseY>=topPos && mouseY<topPos+18 && mouseX>=leftPos+58 && mouseX<leftPos+96)
            renderTooltip(pose,Component.translatable(mouseX<leftPos+78 ? (alphabetical?"gui.mystcraft.pages.alphabetical":"gui.mystcraft.pages.stored_order") : "gui.mystcraft.pages.all"),mouseX,mouseY);
        var page=entry(sourceCell(mouseX,mouseY));
        if(page!=null) renderTooltip(pose,page.page(),mouseX,mouseY);
        if(mouseX>=leftPos+366 && mouseX<leftPos+382 && mouseY>=topPos+27 && mouseY<topPos+97)
            renderTooltip(pose,Component.translatable("gui.mystcraft.desk.ink_level",menu.ink()),mouseX,mouseY);
        if(copyFrom>=0) {
            var pages=menu.sourcePages();
            if(copyFrom<pages.size()) ClassicPageSurface.draw(pose,new PageBrowserIndex.Entry(copyFrom,PageBrowserIndex.page(pages.get(copyFrom)),1),mouseX-15,mouseY-20,30,40);
        }
    }
}
