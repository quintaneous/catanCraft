package com.quin.catancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** A visible Confirm/Cancel step. Prices and one-use commands come only from the server. */
public final class DefenseConfirmationScreen extends Screen {
    private final List<String> lines;
    private final NationDashboardScreen parent;
    private boolean submitted;
    private Button confirm, cancel;

    public DefenseConfirmationScreen(List<String> lines, NationDashboardScreen parent) {
        super(Component.literal("Confirm wall purchase"));
        this.lines=List.copyOf(lines);this.parent=parent;
    }
    NationDashboardScreen parentDashboard(){return parent;}
    @Override protected void init() {
        int width=Math.min(520,this.width-24),left=(this.width-width)/2;
        int buttonY=this.height-38;
        confirm=addRenderableWidget(Button.builder(Component.literal("Confirm purchase"),b->submit(0))
                .bounds(left+12,buttonY,(width-30)/2,20).build());
        cancel=addRenderableWidget(Button.builder(Component.literal("Cancel"),b->submit(1))
                .bounds(left+18+(width-30)/2,buttonY,(width-30)/2,20).build());
        confirm.active=!submitted;cancel.active=!submitted;
    }
    private List<String> actions(){return lines.stream().filter(s->s.startsWith("A|")).toList();}
    private void submit(int index) {
        if(submitted)return;
        var actions=actions();var minecraft=Minecraft.getInstance();
        if(index>=actions.size()||minecraft.player==null||minecraft.player.connection==null)return;
        submitted=true;confirm.active=false;cancel.active=false;
        minecraft.player.connection.sendCommand(actions.get(index).split("\\|",3)[1]);
    }
    @Override public void onClose() {
        if(!submitted)submit(1);
        Minecraft.getInstance().setScreen(parent);
    }
    @Override public void render(@NotNull GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g);
        int w=Math.min(520,this.width-24),left=(this.width-w)/2,top=16,bottom=this.height-48;
        g.fill(left,top,left+w,this.height-12,0xF012161D);
        int y=top+10;
        for(String encoded:lines){
            if(encoded.length()<3||encoded.startsWith("V|")||encoded.startsWith("A|"))continue;
            int color=switch(encoded.charAt(0)){case 'G'->0x7EE787;case 'Y'->0xFFD166;case 'P'->0xD2A8FF;case 'D'->0xAAB4C0;default->0xFFFFFF;};
            for(var line:font.split(Component.literal(encoded.substring(2)),w-24)) {
                if(y+10>bottom)break;
                g.drawString(font,line,left+12,y,color,false);y+=12;
            }
            y+=4;
        }
        if(submitted)g.drawCenteredString(font,"Checking purchase...",this.width/2,this.height-61,0xFFD166);
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen(){return false;}
}
