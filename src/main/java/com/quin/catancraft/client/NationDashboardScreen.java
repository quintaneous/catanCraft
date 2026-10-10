package com.quin.catancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NationDashboardScreen extends Screen {
    private static final int PANEL_WIDTH = 660;
    private static final int TOP = 16;
    private static final int BOTTOM_MARGIN = 18;
    private static final int HEADER_HEIGHT = 34;
    private static final int TAB_HEIGHT = 22;
    private static final int LINE_HEIGHT = 13;
    private static final int ROW_HEIGHT = 20;
    private static final int GAP = 5;

    private static Page LAST_PAGE = Page.OVERVIEW;
    private static final Set<String> OPEN_TERRITORIES = new HashSet<>();
    private static final Set<String> OPEN_TERRITORY_SECTIONS = new HashSet<>();

    private final List<String> lines;
    private final List<HitTarget> hitTargets = new ArrayList<>();

    private Page page;
    private double scroll;
    private int lastContentHeight;
    private int lastVisibleHeight;

    public NationDashboardScreen(List<String> lines) {
        super(Component.literal("Nation Dashboard"));
        this.lines = List.copyOf(lines);
        this.page = LAST_PAGE;
    }

    @Override
    public void render(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics);
        hitTargets.clear();

        int panelWidth = Math.min(PANEL_WIDTH, this.width - 24);
        int left = (this.width - panelWidth) / 2;
        int right = left + panelWidth;
        int bottom = this.height - BOTTOM_MARGIN;

        graphics.fill(left, TOP, right, bottom, 0xF012161D);
        graphics.fill(left, TOP, right, TOP + HEADER_HEIGHT, 0xFF202A38);

        String nationName = nationName();
        graphics.drawString(font, nationName, left + 14, TOP + 9, 0xFFFFFF, false);
        graphics.drawString(
                font,
                "NATION CONTROL",
                right - 14 - font.width("NATION CONTROL"),
                TOP + 9,
                0x8B949E,
                false
        );

        int tabsTop = TOP + HEADER_HEIGHT;
        renderTabs(graphics, mouseX, mouseY, left, right, tabsTop);

        int contentTop = tabsTop + TAB_HEIGHT + 7;
        int contentBottom = bottom - 8;
        lastVisibleHeight = Math.max(1, contentBottom - contentTop);

        graphics.enableScissor(left + 6, contentTop, right - 6, contentBottom);

        int startY = contentTop + 2 - (int) scroll;
        int endY = switch (page) {
            case OVERVIEW -> renderOverview(
                    graphics, mouseX, mouseY, left, right, startY, contentTop, contentBottom);
            case RESOURCES -> renderResources(
                    graphics, mouseX, mouseY, left, right, startY, contentTop, contentBottom);
            case TERRITORIES -> renderTerritories(
                    graphics, mouseX, mouseY, left, right, startY, contentTop, contentBottom);
            case TRADE -> renderGenericSection(
                    graphics, mouseX, mouseY, left, right, startY,
                    contentTop, contentBottom, "trade");
            case MONUMENT -> renderGenericSection(
                    graphics, mouseX, mouseY, left, right, startY,
                    contentTop, contentBottom, "monument");
            case EXPANSION -> renderGenericSection(
                    graphics, mouseX, mouseY, left, right, startY,
                    contentTop, contentBottom, "expansion");
        };

        graphics.disableScissor();

        lastContentHeight = Math.max(0, endY - startY);
        drawScrollbar(graphics, right, contentTop, contentBottom);

        graphics.drawCenteredString(
                font,
                "Click territory and section rows to expand • Mouse wheel to scroll • Esc to close",
                this.width / 2,
                bottom + 5,
                0x7D8590
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabs(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int right,
            int top
    ) {
        Page[] pages = Page.values();
        int available = right - left - 12;
        int tabWidth = available / pages.length;
        int x = left + 6;

        for (int i = 0; i < pages.length; i++) {
            Page candidate = pages[i];
            int x2 = i == pages.length - 1 ? right - 6 : x + tabWidth;
            boolean selected = candidate == page;
            boolean hovered = mouseX >= x && mouseX <= x2
                    && mouseY >= top && mouseY <= top + TAB_HEIGHT;

            graphics.fill(
                    x,
                    top,
                    x2 - 2,
                    top + TAB_HEIGHT,
                    selected
                            ? 0xFF344A63
                            : hovered ? 0xFF293847 : 0xFF1B232E
            );
            if (selected) {
                graphics.fill(x, top + TAB_HEIGHT - 2, x2 - 2,
                        top + TAB_HEIGHT, 0xFF79C0FF);
            }
            graphics.drawCenteredString(
                    font,
                    candidate.label,
                    (x + x2 - 2) / 2,
                    top + 7,
                    selected ? 0xFFFFFF : 0xAAB4C0
            );
            hitTargets.add(new HitTarget(
                    x, top, x2 - 2, top + TAB_HEIGHT,
                    HitKind.PAGE, candidate.name()));
            x = x2;
        }
    }

    private int renderOverview(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int right,
            int y,
            int contentTop,
            int contentBottom
    ) {
        String treasury = findTopValue("Treasury:", "$0");
        String members = findTopValue("Members:", "0");
        String territories = findTopValue("Territories:", "0");
        String role = findTopValue("Role:", "Member");
        String quickStock = findTopLine("Quick stock:");

        int cardGap = 6;
        int cardWidth = (right - left - 28 - cardGap * 3) / 4;
        int cardLeft = left + 14;

        y = drawStatCard(graphics, cardLeft, y, cardWidth,
                "TREASURY", treasury, 0x7EE787);
        drawStatCard(graphics, cardLeft + (cardWidth + cardGap), y - 37, cardWidth,
                "MEMBERS", members, 0x79C0FF);
        drawStatCard(graphics, cardLeft + 2 * (cardWidth + cardGap), y - 37, cardWidth,
                "TERRITORIES", territories, 0xFFD166);
        drawStatCard(graphics, cardLeft + 3 * (cardWidth + cardGap), y - 37, cardWidth,
                "ROLE", role, 0xD2A8FF);

        y += 4;
        graphics.fill(left + 14, y, right - 14, y + 28, 0xCC1A222C);
        graphics.drawString(font, "QUICK STOCK", left + 22, y + 5, 0x8B949E, false);
        graphics.drawString(
                font,
                quickStock.isBlank() ? "No stock data." : quickStock,
                left + 22,
                y + 16,
                0xD7DEE8,
                false
        );
        y += 35;

        graphics.drawString(font, "AT A GLANCE", left + 14, y, 0xFFD166, false);
        y += 15;

        String resourceLabel = sectionLabel("resources");
        String tradeLabel = sectionLabel("trade");
        String monumentLabel = sectionLabel("monument");
        String territoryLabel = sectionLabel("territories");
        String expansionLabel = sectionLabel("expansion");

        y = drawOverviewRow(graphics, left, right, y, "Resources",
                cleanSectionSummary(resourceLabel, "Resources"), Page.RESOURCES,
                mouseX, mouseY, contentTop, contentBottom);
        y = drawOverviewRow(graphics, left, right, y, "Territories",
                cleanSectionSummary(territoryLabel, "Territories"), Page.TERRITORIES,
                mouseX, mouseY, contentTop, contentBottom);
        y = drawOverviewRow(graphics, left, right, y, "Trade",
                cleanSectionSummary(tradeLabel, "Trade"), Page.TRADE,
                mouseX, mouseY, contentTop, contentBottom);
        y = drawOverviewRow(graphics, left, right, y, "Active Monument",
                cleanSectionSummary(monumentLabel, "Active Monument"), Page.MONUMENT,
                mouseX, mouseY, contentTop, contentBottom);
        y = drawOverviewRow(graphics, left, right, y, "Expansion",
                cleanSectionSummary(expansionLabel, "Available Expansion"), Page.EXPANSION,
                mouseX, mouseY, contentTop, contentBottom);

        List<String> outside = outsideSectionLines();
        boolean startBlock = false;
        for (String encoded : outside) {
            if (encoded.startsWith("H|CHOOSE STARTING CITY")) {
                startBlock = true;
                y += 5;
            }
            if (!startBlock) continue;
            y = drawEncodedLine(
                    graphics, mouseX, mouseY, left, right, y,
                    contentTop, contentBottom, encoded);
        }

        return y;
    }

    private int drawStatCard(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String label,
            String value,
            int valueColor
    ) {
        graphics.fill(x, y, x + width, y + 32, 0xCC1A222C);
        graphics.drawString(font, label, x + 7, y + 5, 0x7D8590, false);
        graphics.drawString(font, value, x + 7, y + 17, valueColor, false);
        return y + 37;
    }

    private int drawOverviewRow(
            GuiGraphics graphics,
            int left,
            int right,
            int y,
            String title,
            String summary,
            Page target,
            int mouseX,
            int mouseY,
            int contentTop,
            int contentBottom
    ) {
        int x1 = left + 14;
        int x2 = right - 14;
        boolean hovered = mouseX >= x1 && mouseX <= x2
                && mouseY >= y && mouseY <= y + ROW_HEIGHT;

        graphics.fill(x1, y, x2, y + ROW_HEIGHT,
                hovered ? 0xEE2E4053 : 0xCC1A2633);
        graphics.drawString(font, title, x1 + 8, y + 6, 0xFFFFFF, false);
        int summaryX = x2 - 8 - font.width(summary);
        graphics.drawString(font, summary, summaryX, y + 6, 0x8B949E, false);

        if (y + ROW_HEIGHT >= contentTop && y <= contentBottom) {
            hitTargets.add(new HitTarget(
                    x1, y, x2, y + ROW_HEIGHT,
                    HitKind.PAGE, target.name()));
        }
        return y + ROW_HEIGHT + GAP;
    }

    private int renderResources(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int right,
            int y,
            int contentTop,
            int contentBottom
    ) {
        List<String> section = sectionLines("resources");
        List<String> raw = new ArrayList<>();
        List<String> processed = new ArrayList<>();
        List<String> target = raw;

        for (String line : section) {
            if (line.equals("H|RAW RESOURCES")) {
                target = raw;
                continue;
            }
            if (line.equals("H|PROCESSED RESOURCES")) {
                target = processed;
                continue;
            }
            if (line.startsWith("G|")) target.add(line.substring(2));
        }

        int gap = 10;
        int colWidth = (right - left - 38 - gap) / 2;
        int x1 = left + 14;
        int x2 = x1 + colWidth + gap;
        int rawY = y;
        int processedY = y;

        graphics.drawString(font, "RAW RESOURCES", x1, rawY, 0xFFD166, false);
        rawY += 17;
        for (String item : raw) {
            rawY = drawResourceCard(graphics, x1, rawY, colWidth, item);
        }

        graphics.drawString(font, "PROCESSED", x2, processedY, 0xFFD166, false);
        processedY += 17;
        for (String item : processed) {
            processedY = drawResourceCard(graphics, x2, processedY, colWidth, item);
        }

        return Math.max(rawY, processedY);
    }

    private int drawResourceCard(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String text
    ) {
        graphics.fill(x, y, x + width, y + 22, 0xCC19232D);
        int split = text.indexOf(':');
        if (split > 0) {
            String name = text.substring(0, split);
            String amount = text.substring(split + 1).trim();
            graphics.drawString(font, name, x + 8, y + 7, 0xD7DEE8, false);
            graphics.drawString(
                    font,
                    amount,
                    x + width - 8 - font.width(amount),
                    y + 7,
                    0x7EE787,
                    false
            );
        } else {
            graphics.drawString(font, text, x + 8, y + 7, 0xD7DEE8, false);
        }
        return y + 27;
    }

    private int renderTerritories(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int right,
            int y,
            int contentTop,
            int contentBottom
    ) {
        List<String> section = sectionLines("territories");
        boolean territoryHidden = false;
        boolean subsectionHidden = false;
        String currentTerritory = "";

        for (String encoded : section) {
            if (encoded.startsWith("T|")) {
                TerritoryRow territory = TerritoryRow.parse(encoded);
                if (territory == null) continue;

                currentTerritory = territory.id();
                boolean open = OPEN_TERRITORIES.contains(territory.id());
                territoryHidden = !open;
                subsectionHidden = false;

                int x1 = left + 14;
                int x2 = right - 14;
                boolean hovered = mouseX >= x1 && mouseX <= x2
                        && mouseY >= y && mouseY <= y + 26;
                graphics.fill(x1, y, x2, y + 26,
                        hovered ? 0xEE31475F : 0xDD202C3A);
                graphics.drawString(
                        font,
                        (open ? "▼ " : "▶ ") + territory.label(),
                        x1 + 8,
                        y + 9,
                        open ? 0xFFFFFF : 0x79C0FF,
                        false
                );

                if (y + 26 >= contentTop && y <= contentBottom) {
                    hitTargets.add(new HitTarget(
                            x1, y, x2, y + 26,
                            HitKind.TERRITORY, territory.id()));
                }
                y += 31;
                continue;
            }

            if (encoded.startsWith("E|")) {
                currentTerritory = "";
                territoryHidden = false;
                subsectionHidden = false;
                y += 4;
                continue;
            }

            if (territoryHidden) continue;

            if (encoded.startsWith("N|")) {
                TerritorySectionRow subsection =
                        TerritorySectionRow.parse(encoded);
                if (subsection == null) continue;

                String key = subsection.key();
                boolean open = OPEN_TERRITORY_SECTIONS.contains(key);
                subsectionHidden = !open;

                int x1 = left + 24;
                int x2 = right - 24;
                boolean hovered = mouseX >= x1 && mouseX <= x2
                        && mouseY >= y && mouseY <= y + 22;

                graphics.fill(
                        x1, y, x2, y + 22,
                        hovered ? 0xEE2A3A4C : 0xCC192530
                );
                graphics.drawString(
                        font,
                        (open ? "▼ " : "▶ ") + subsection.label(),
                        x1 + 8,
                        y + 7,
                        open ? 0xFFFFFF : 0xAAB4C0,
                        false
                );

                if (y + 22 >= contentTop && y <= contentBottom) {
                    hitTargets.add(new HitTarget(
                            x1, y, x2, y + 22,
                            HitKind.SUBSECTION, key));
                }
                y += 27;
                continue;
            }

            if (encoded.startsWith("Q|")) {
                subsectionHidden = false;
                y += 2;
                continue;
            }

            if (subsectionHidden) continue;

            y = drawEncodedLine(
                    graphics, mouseX, mouseY, left, right, y,
                    contentTop, contentBottom, encoded);
        }
        return y;
    }

    private int renderGenericSection(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int right,
            int y,
            int contentTop,
            int contentBottom,
            String sectionId
    ) {
        for (String encoded : sectionLines(sectionId)) {
            y = drawEncodedLine(
                    graphics, mouseX, mouseY, left, right, y,
                    contentTop, contentBottom, encoded);
        }
        return y;
    }

    private int drawEncodedLine(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            int left,
            int right,
            int y,
            int contentTop,
            int contentBottom,
            String encoded
    ) {
        if (encoded.isEmpty()) return y + 7;

        if (encoded.startsWith("A|") || encoded.startsWith("C|")) {
            ActionRow action = ActionRow.parse(encoded);
            if (action == null) return y;

            int x1 = left + 22;
            int x2 = right - 22;
            boolean hovered = mouseX >= x1 && mouseX <= x2
                    && mouseY >= y && mouseY <= y + ROW_HEIGHT;
            boolean special = encoded.startsWith("C|");

            graphics.fill(
                    x1, y, x2, y + ROW_HEIGHT,
                    special
                            ? (hovered ? 0xEE62477C : 0xCC463258)
                            : (hovered ? 0xEE36506E : 0xCC28384D)
            );
            graphics.drawCenteredString(
                    font,
                    action.label(),
                    (x1 + x2) / 2,
                    y + 6,
                    hovered ? 0xFFFFFF : 0xD7DEE8
            );

            if (y + ROW_HEIGHT >= contentTop && y <= contentBottom) {
                hitTargets.add(new HitTarget(
                        x1, y, x2, y + ROW_HEIGHT,
                        HitKind.ACTION,
                        (special ? "C:" : "A:") + action.command()));
            }
            return y + ROW_HEIGHT + GAP;
        }

        String text = encoded;
        int color = 0xD7DEE8;
        int x = left + 22;

        if (encoded.length() > 2 && encoded.charAt(1) == '|') {
            char type = encoded.charAt(0);
            text = encoded.substring(2);
            switch (type) {
                case 'H' -> {
                    color = 0xFFD166;
                    x = left + 14;
                    y += 5;
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
        return y + LINE_HEIGHT;
    }

    private List<String> sectionLines(String id) {
        List<String> result = new ArrayList<>();
        boolean inside = false;

        for (String line : lines) {
            if (line.startsWith("S|")) {
                SectionRow section = SectionRow.parse(line);
                inside = section != null && section.id().equals(id);
                continue;
            }
            if (line.startsWith("X|")) {
                String closing = line.substring(2);
                if (inside && closing.equals(id)) break;
                if (closing.equals(id)) inside = false;
                continue;
            }
            if (inside) result.add(line);
        }
        return result;
    }

    private List<String> outsideSectionLines() {
        List<String> result = new ArrayList<>();
        boolean inSection = false;

        for (String line : lines) {
            if (line.startsWith("S|")) {
                inSection = true;
                continue;
            }
            if (line.startsWith("X|")) {
                inSection = false;
                continue;
            }
            if (!inSection) result.add(line);
        }
        return result;
    }

    private String sectionLabel(String id) {
        for (String line : lines) {
            if (!line.startsWith("S|")) continue;
            SectionRow row = SectionRow.parse(line);
            if (row != null && row.id().equals(id)) return row.label();
        }
        return "";
    }

    private String nationName() {
        for (String line : lines) {
            if (line.startsWith("H|")) return line.substring(2);
        }
        return "Nation";
    }

    private String findTopValue(String prefix, String fallback) {
        String line = findTopLine(prefix);
        if (line.isBlank()) return fallback;
        return line.substring(prefix.length()).trim();
    }

    private String findTopLine(String prefix) {
        boolean inSection = false;
        for (String encoded : lines) {
            if (encoded.startsWith("S|")) {
                inSection = true;
                continue;
            }
            if (encoded.startsWith("X|")) {
                inSection = false;
                continue;
            }
            if (inSection) continue;

            String text = encoded.length() > 2 && encoded.charAt(1) == '|'
                    ? encoded.substring(2)
                    : encoded;
            if (text.startsWith(prefix)) return text;
        }
        return "";
    }

    private static String cleanSectionSummary(String value, String title) {
        if (value == null || value.isBlank()) return "";
        String cleaned = value;
        if (cleaned.regionMatches(true, 0, title, 0, Math.min(title.length(), cleaned.length()))) {
            cleaned = cleaned.substring(Math.min(title.length(), cleaned.length())).trim();
        }
        while (cleaned.startsWith("•")) cleaned = cleaned.substring(1).trim();
        return cleaned;
    }

    private void drawScrollbar(
            GuiGraphics graphics,
            int right,
            int contentTop,
            int contentBottom
    ) {
        if (lastContentHeight <= lastVisibleHeight) return;

        double ratio = lastVisibleHeight / (double) lastContentHeight;
        int barHeight = Math.max(20, (int) (lastVisibleHeight * ratio));
        int track = lastVisibleHeight - barHeight;
        int maxScroll = Math.max(1, lastContentHeight - lastVisibleHeight);
        int barY = contentTop + (int) (track * (scroll / maxScroll));

        graphics.fill(right - 5, contentTop, right - 3, contentBottom, 0x70404A58);
        graphics.fill(right - 5, barY, right - 3, barY + barHeight, 0xFFD7DEE8);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        for (HitTarget hit : hitTargets) {
            if (!hit.contains(mouseX, mouseY)) continue;

            if (hit.kind() == HitKind.PAGE) {
                page = Page.valueOf(hit.value());
                LAST_PAGE = page;
                scroll = 0;
                return true;
            }

            if (hit.kind() == HitKind.TERRITORY) {
                if (!OPEN_TERRITORIES.add(hit.value())) {
                    OPEN_TERRITORIES.remove(hit.value());
                }
                clampScroll();
                return true;
            }

            if (hit.kind() == HitKind.SUBSECTION) {
                if (!OPEN_TERRITORY_SECTIONS.add(hit.value())) {
                    OPEN_TERRITORY_SECTIONS.remove(hit.value());
                }
                clampScroll();
                return true;
            }

            if (hit.kind() == HitKind.ACTION) {
                Minecraft minecraft = Minecraft.getInstance();
                boolean clientAction = hit.value().startsWith("C:");
                String command = hit.value().substring(2);

                if (clientAction && command.equals("trade_create")) {
                    minecraft.setScreen(new TradeProposalScreen(this));
                } else if (minecraft.player != null
                        && minecraft.player.connection != null) {
                    minecraft.player.connection.sendCommand(command);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, lastContentHeight - lastVisibleHeight);
        scroll = Math.max(0, Math.min(maxScroll, scroll - delta * 26));
        return true;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, lastContentHeight - lastVisibleHeight);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        OVERVIEW("Overview"),
        RESOURCES("Resources"),
        TERRITORIES("Territories"),
        TRADE("Trade"),
        MONUMENT("Monument"),
        EXPANSION("Expansion");

        private final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private enum HitKind {
        PAGE,
        TERRITORY,
        SUBSECTION,
        ACTION
    }

    private record HitTarget(
            int x1,
            int y1,
            int x2,
            int y2,
            HitKind kind,
            String value
    ) {
        boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private record SectionRow(String id, String label) {
        private static SectionRow parse(String encoded) {
            String[] pieces = encoded.split("\\|", 3);
            if (pieces.length != 3 || pieces[1].isBlank()) return null;
            return new SectionRow(pieces[1], pieces[2]);
        }
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

    private record TerritorySectionRow(
            String territoryId,
            String sectionId,
            String label
    ) {
        private static TerritorySectionRow parse(String encoded) {
            String[] pieces = encoded.split("\\|", 4);
            if (pieces.length != 4
                    || pieces[1].isBlank()
                    || pieces[2].isBlank()
                    || pieces[3].isBlank()) {
                return null;
            }
            return new TerritorySectionRow(
                    pieces[1],
                    pieces[2],
                    pieces[3]
            );
        }

        private String key() {
            return territoryId + ":" + sectionId;
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
