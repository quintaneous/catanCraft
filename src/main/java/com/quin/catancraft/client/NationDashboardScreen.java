package com.quin.catancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NationDashboardScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int TOP = 18;
    private static final int BOTTOM_MARGIN = 24;
    private static final int LINE_HEIGHT = 12;
    private static final int ACTION_HEIGHT = 18;
    private static final int ACTION_GAP = 3;

    private static final Set<String> EXPANDED_TERRITORIES = new HashSet<>();

    private final List<String> lines;
    private double scroll;

    public NationDashboardScreen(List<String> lines) {
        super(Component.literal("Nation Dashboard"));
        this.lines = List.copyOf(lines);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelWidth = Math.min(PANEL_WIDTH, this.width - 24);
        int left = (this.width - panelWidth) / 2;
        int right = left + panelWidth;
        int bottom = this.height - BOTTOM_MARGIN;
        int contentTop = TOP + 30;

        graphics.fill(left, TOP, right, bottom, 0xEA12161D);
        graphics.fill(left, TOP, right, TOP + 26, 0xEA243044);
        graphics.drawCenteredString(font, "CATANCRAFT • NATION CONTROL",
                this.width / 2, TOP + 9, 0xFFFFFF);

        graphics.enableScissor(left + 6, contentTop, right - 6, bottom - 8);

        int y = contentTop + 2 - (int) scroll;
        boolean territoryHidden = false;
        for (String encoded : lines) {
            if (encoded.startsWith("T|")) {
                TerritoryRow territory = TerritoryRow.parse(encoded);
                if (territory != null) {
                    boolean expanded = EXPANDED_TERRITORIES.contains(territory.id());
                    territoryHidden = !expanded;

                    int rowLeft = left + 10;
                    int rowRight = right - 10;
                    boolean hovered = mouseX >= rowLeft && mouseX <= rowRight
                            && mouseY >= y && mouseY <= y + ACTION_HEIGHT;
                    graphics.fill(rowLeft, y, rowRight, y + ACTION_HEIGHT,
                            hovered ? 0xEE31475F : 0xDD202C3A);
                    graphics.drawString(
                            font,
                            (expanded ? "▼ " : "▶ ") + territory.label(),
                            rowLeft + 7,
                            y + 5,
                            hovered ? 0xFFFFFF : 0x79C0FF,
                            false
                    );
                    y += ACTION_HEIGHT + ACTION_GAP;
                }
                continue;
            }

            if (encoded.startsWith("E|")) {
                territoryHidden = false;
                y += 4;
                continue;
            }

            if (territoryHidden) continue;

            if (encoded.isEmpty()) {
                y += 7;
                continue;
            }

            if (encoded.startsWith("A|")) {
                ActionRow action = ActionRow.parse(encoded);
                if (action != null) {
                    int actionLeft = left + 14;
                    int actionRight = right - 14;
                    boolean hovered = mouseX >= actionLeft && mouseX <= actionRight
                            && mouseY >= y && mouseY <= y + ACTION_HEIGHT;

                    graphics.fill(
                            actionLeft,
                            y,
                            actionRight,
                            y + ACTION_HEIGHT,
                            hovered ? 0xEE36506E : 0xCC28384D
                    );
                    graphics.fill(
                            actionLeft,
                            y + ACTION_HEIGHT - 1,
                            actionRight,
                            y + ACTION_HEIGHT,
                            hovered ? 0xFF79C0FF : 0xAA52657A
                    );
                    graphics.drawCenteredString(
                            font,
                            action.label(),
                            (actionLeft + actionRight) / 2,
                            y + 5,
                            hovered ? 0xFFFFFF : 0xD7DEE8
                    );
                    y += ACTION_HEIGHT + ACTION_GAP;
                    continue;
                }
            }

            String text = encoded;
            int color = 0xD7DEE8;
            int x = left + 14;

            if (encoded.length() > 2 && encoded.charAt(1) == '|') {
                char type = encoded.charAt(0);
                text = encoded.substring(2);
                switch (type) {
                    case 'H' -> {
                        color = 0xFFD166;
                        x = left + 12;
                        y += 4;
                    }
                    case 'G' -> color = 0x7EE787;
                    case 'Y' -> color = 0xFFD166;
                    case 'R' -> color = 0xFF7B72;
                    case 'B' -> color = 0x79C0FF;
                    case 'P' -> color = 0xD2A8FF;
                    case 'D' -> color = 0x8B949E;
                    default -> { }
                }
            }

            graphics.drawString(font, text, x, y, color, false);
            y += LINE_HEIGHT;
        }

        graphics.disableScissor();

        int contentHeight = measuredContentHeight();
        int visibleHeight = Math.max(1, bottom - 8 - contentTop);
        if (contentHeight > visibleHeight) {
            double ratio = visibleHeight / (double) contentHeight;
            int barHeight = Math.max(18, (int) (visibleHeight * ratio));
            int track = visibleHeight - barHeight;
            int maxScroll = Math.max(1, contentHeight - visibleHeight);
            int barY = contentTop + (int) (track * (scroll / maxScroll));
            graphics.fill(right - 5, contentTop, right - 3, bottom - 8, 0x70404A58);
            graphics.fill(right - 5, barY, right - 3, barY + barHeight, 0xFFD7DEE8);
        }

        graphics.drawCenteredString(font,
                "Click actions  •  Mouse wheel to scroll  •  Esc to close",
                this.width / 2, bottom + 6, 0x8B949E);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int panelWidth = Math.min(PANEL_WIDTH, this.width - 24);
        int left = (this.width - panelWidth) / 2;
        int right = left + panelWidth;
        int contentTop = TOP + 30;
        int y = contentTop + 2 - (int) scroll;
        boolean territoryHidden = false;

        for (String encoded : lines) {
            if (encoded.startsWith("T|")) {
                TerritoryRow territory = TerritoryRow.parse(encoded);
                if (territory != null) {
                    boolean expanded = EXPANDED_TERRITORIES.contains(territory.id());
                    int rowLeft = left + 10;
                    int rowRight = right - 10;
                    if (mouseX >= rowLeft && mouseX <= rowRight
                            && mouseY >= y && mouseY <= y + ACTION_HEIGHT) {
                        if (expanded) {
                            EXPANDED_TERRITORIES.remove(territory.id());
                        } else {
                            EXPANDED_TERRITORIES.add(territory.id());
                        }
                        clampScroll();
                        return true;
                    }
                    territoryHidden = !expanded;
                    y += ACTION_HEIGHT + ACTION_GAP;
                }
                continue;
            }

            if (encoded.startsWith("E|")) {
                territoryHidden = false;
                y += 4;
                continue;
            }

            if (territoryHidden) continue;

            if (encoded.isEmpty()) {
                y += 7;
                continue;
            }

            if (encoded.startsWith("A|")) {
                ActionRow action = ActionRow.parse(encoded);
                if (action != null) {
                    int actionLeft = left + 14;
                    int actionRight = right - 14;
                    if (mouseX >= actionLeft && mouseX <= actionRight
                            && mouseY >= y && mouseY <= y + ACTION_HEIGHT) {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.player != null && minecraft.player.connection != null) {
                            minecraft.player.connection.sendCommand(action.command());
                        }
                        return true;
                    }
                    y += ACTION_HEIGHT + ACTION_GAP;
                    continue;
                }
            }

            if (encoded.startsWith("H|")) y += 4;
            y += LINE_HEIGHT;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleHeight = Math.max(1, (this.height - BOTTOM_MARGIN - 8) - (TOP + 30));
        int maxScroll = Math.max(0, measuredContentHeight() - visibleHeight);
        scroll = Math.max(0, Math.min(maxScroll, scroll - delta * 24));
        return true;
    }

    private int measuredContentHeight() {
        int height = 4;
        boolean territoryHidden = false;

        for (String line : lines) {
            if (line.startsWith("T|")) {
                TerritoryRow territory = TerritoryRow.parse(line);
                if (territory != null) {
                    territoryHidden = !EXPANDED_TERRITORIES.contains(territory.id());
                    height += ACTION_HEIGHT + ACTION_GAP;
                }
                continue;
            }

            if (line.startsWith("E|")) {
                territoryHidden = false;
                height += 4;
                continue;
            }

            if (territoryHidden) continue;

            if (line.isEmpty()) {
                height += 7;
            } else if (line.startsWith("A|")) {
                height += ACTION_HEIGHT + ACTION_GAP;
            } else {
                if (line.startsWith("H|")) height += 4;
                height += LINE_HEIGHT;
            }
        }
        return height;
    }

    private void clampScroll() {
        int visibleHeight = Math.max(1, (this.height - BOTTOM_MARGIN - 8) - (TOP + 30));
        int maxScroll = Math.max(0, measuredContentHeight() - visibleHeight);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record TerritoryRow(String id, String label) {
        private static TerritoryRow parse(String encoded) {
            String[] pieces = encoded.split("\\|", 3);
            if (pieces.length != 3 || pieces[1].isBlank() || pieces[2].isBlank()) {
                return null;
            }
            return new TerritoryRow(pieces[1], pieces[2]);
        }
    }

    private record ActionRow(String command, String label) {
        private static ActionRow parse(String encoded) {
            int first = encoded.indexOf('|');
            int second = encoded.indexOf('|', first + 1);
            if (first < 0 || second < 0 || second >= encoded.length() - 1) {
                return null;
            }
            String command = encoded.substring(first + 1, second);
            String label = encoded.substring(second + 1);
            if (command.isBlank() || label.isBlank()) return null;
            return new ActionRow(command, label);
        }
    }
}
