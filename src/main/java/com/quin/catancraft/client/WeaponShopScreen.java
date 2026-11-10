package com.quin.catancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Generic server-authored counter view. Prices and one-use confirmation actions stay server-side. */
public final class WeaponShopScreen extends Screen {
    private final List<String> lines;
    private final List<TextLine> text = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private int left, panelWidth, viewportTop, viewportBottom, contentHeight;
    private double scroll;
    private boolean submitted;
    private record TextLine(FormattedCharSequence text, int y, int color) {}
    private record Action(Button button, int y) {}

    public WeaponShopScreen(List<String> lines) {
        super(Component.literal("Weapon counter")); this.lines = List.copyOf(lines);
    }
    @Override protected void init() {
        text.clear(); actions.clear();
        panelWidth = Math.max(120, Math.min(540, width - 24)); left = (width - panelWidth) / 2;
        viewportTop = 26; viewportBottom = Math.max(viewportTop + 24, height - 48);
        int y = 0;
        for (String line : lines) {
            if (line.length() < 3 || line.startsWith("V|") || line.startsWith("X|")) continue;
            if (line.startsWith("A|")) {
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3) continue;
                Button button = Button.builder(Component.literal(parts[2]), b -> submit(parts[1]))
                        .bounds(left + 12, viewportTop + y, panelWidth - 24, 20).build();
                addRenderableWidget(button); actions.add(new Action(button, y)); y += 26;
            } else {
                int color = switch (line.charAt(0)) {
                    case 'G' -> 0x7EE787; case 'Y' -> 0xFFD166; case 'P' -> 0xD2A8FF;
                    case 'D' -> 0xAAB4C0; default -> 0xFFFFFF;
                };
                for (var wrapped : font.split(Component.literal(line.substring(2)), panelWidth - 32)) {
                    text.add(new TextLine(wrapped, y, color)); y += 12;
                }
                y += 5;
            }
        }
        contentHeight = y;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + panelWidth - 100, height - 36, 88, 20).build());
        positionActions();
    }
    private void positionActions() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (viewportBottom - viewportTop))));
        for (Action action : actions) {
            int y = viewportTop + action.y() - (int) scroll;
            action.button().setY(y);
            action.button().visible = y >= viewportTop && y + 20 <= viewportBottom;
            action.button().active = !submitted;
        }
    }
    private void submit(String command) {
        var player = Minecraft.getInstance().player;
        if (submitted || player == null || player.connection == null) return;
        submitted = true; positionActions(); player.connection.sendCommand(command);
    }
    @Override public void onClose() {
        var player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            lines.stream().filter(line -> line.startsWith("X|")).findFirst()
                    .ifPresent(line -> player.connection.sendCommand(line.substring(2)));
        }
        Minecraft.getInstance().setScreen(null);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + panelWidth && mouseY >= viewportTop && mouseY <= viewportBottom) {
            scroll -= delta * 28; positionActions(); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, 14, left + panelWidth, height - 12, 0xF012161D);
        graphics.enableScissor(left + 8, viewportTop, left + panelWidth - 8, viewportBottom);
        for (TextLine line : text) {
            int y = viewportTop + line.y() - (int) scroll;
            if (y + 10 >= viewportTop && y < viewportBottom)
                graphics.drawString(font, line.text(), left + 12, y, line.color(), false);
        }
        graphics.disableScissor();
        if (submitted) graphics.drawString(font, "Checking selection...", left + 12, height - 30, 0xFFD166, false);
        else if (contentHeight > viewportBottom - viewportTop)
            graphics.drawString(font, "Scroll for more", left + 12, height - 30, 0xAAB4C0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
