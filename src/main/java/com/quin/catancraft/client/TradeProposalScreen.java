package com.quin.catancraft.client;

import com.quin.catancraft.data.ResourceType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TradeProposalScreen extends Screen {
    private enum Page { EDIT, PARTNERS, OFFER, REQUEST, REVIEW }

    private final NationDashboardScreen parent;
    private final List<String> partners;
    private final Map<String, Long> available;
    private final List<String> assets = new ArrayList<>();
    private String partner, offer, request;
    private String offerAmount = "1", requestAmount = "1";
    private Page page = Page.EDIT;
    private int pickerPage;
    private boolean submitted;
    private Button review;

    public TradeProposalScreen(NationDashboardScreen parent, List<String> partners, Map<String, Long> available) {
        super(Component.literal("New trade offer"));
        this.parent = parent;
        this.partners = List.copyOf(partners);
        this.available = Map.copyOf(available);
        if (partners.size() == 1) partner = partners.get(0);
        assets.add("money");
        for (ResourceType type : ResourceType.values()) assets.add(type.id());
    }

    private int contentWidth() { return Math.min(380, width - 32); }
    private int left() { return (width - contentWidth()) / 2; }
    private int top() { return Math.max(8, (height - 188) / 2); }
    private int footer() { return top() + 165; }

    @Override
    protected void init() {
        review = null;
        switch (page) {
            case EDIT -> editor();
            case REVIEW -> reviewButtons();
            default -> picker();
        }
    }

    private void show(Page next) {
        page = next;
        pickerPage = 0;
        rebuildWidgets();
    }

    private Button button(int x, int y, int w, String label, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action)
                .bounds(x, y, w, 20).build());
    }

    private void editor() {
        int x = left(), w = contentWidth(), y = top();
        button(x, y + 33, w, partner == null ? "Choose a nation" : partner,
                b -> show(Page.PARTNERS)).active = !partners.isEmpty();
        quantityRow(true, y + 70);
        quantityRow(false, y + 107);
        int half = (w - 6) / 2;
        button(x, footer(), half, "Back", b -> onClose());
        review = button(x + half + 6, footer(), w - half - 6, "Review offer", b -> show(Page.REVIEW));
        updateReview();
    }

    private void quantityRow(boolean giving, int y) {
        int x = left(), w = contentWidth();
        int selectWidth = w / 2 - 3, amountX = x + selectWidth + 6;
        int stepWidth = 28, amountWidth = w - selectWidth - 6 - 2 * (stepWidth + 3);
        String asset = giving ? offer : request;
        button(x, y, selectWidth, asset == null ? "Choose resource" : label(asset),
                b -> show(giving ? Page.OFFER : Page.REQUEST));
        EditBox box = new EditBox(font, amountX + stepWidth + 3, y, amountWidth, 20,
                Component.literal(giving ? "Amount you give" : "Amount you get"));
        box.setMaxLength(19);
        box.setFilter(text -> text.chars().allMatch(c -> c >= '0' && c <= '9'));
        box.setValue(giving ? offerAmount : requestAmount);
        box.setResponder(text -> {
            if (giving) offerAmount = text;
            else requestAmount = text;
            updateReview();
        });
        button(amountX, y, stepWidth, "−1", b -> adjust(box, -1));
        addRenderableWidget(box);
        button(x + w - stepWidth, y, stepWidth, "+1", b -> adjust(box, 1));
    }

    private static void adjust(EditBox field, int change) {
        long current = amount(field.getValue());
        long next = change > 0 && current == Long.MAX_VALUE ? current : Math.max(1, current + change);
        field.setValue(Long.toString(next));
    }

    private static long amount(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateReview() {
        if (review != null) review.active = problem().isEmpty()
                && partner != null && offer != null && request != null;
    }

    private String problem() {
        if (partners.isEmpty()) return "Another nation must exist before you can trade.";
        if (amount(offerAmount) < 1 || amount(requestAmount) < 1) return "Enter a positive whole amount in each box.";
        if (offer != null && amount(offerAmount) > available.getOrDefault(offer, 0L))
            return "Your nation has too little " + label(offer) + " for this offer.";
        return "";
    }

    private void picker() {
        boolean nations = page == Page.PARTNERS;
        List<String> choices = nations ? partners : assets;
        int rows = 5;
        int pages = (choices.size() + rows - 1) / rows;
        int x = left(), w = contentWidth();
        for (int i = pickerPage * rows; i < Math.min(choices.size(), (pickerPage + 1) * rows); i++) {
            String value = choices.get(i);
            String name = nations ? value : label(value);
            if (page == Page.OFFER) name += "   (" + available.getOrDefault(value, 0L) + " available)";
            button(x, top() + 28 + (i % rows) * 23, w, name, b -> {
                switch (page) {
                    case PARTNERS -> partner = value;
                    case OFFER -> offer = value;
                    case REQUEST -> request = value;
                    default -> throw new IllegalStateException("Unexpected trade picker");
                }
                show(Page.EDIT);
            });
        }
        int third = (w - 12) / 3;
        button(x, footer(), third, "Back", b -> show(Page.EDIT));
        button(x + third + 6, footer(), third, "Previous", b -> {
            pickerPage--;
            rebuildWidgets();
        }).active = pickerPage > 0;
        button(x + 2 * (third + 6), footer(), w - 2 * (third + 6), "Next", b -> {
            pickerPage++;
            rebuildWidgets();
        }).active = pickerPage + 1 < pages;
    }

    private void reviewButtons() {
        int w = contentWidth(), half = (w - 6) / 2;
        button(left(), footer(), half, "Edit offer", b -> show(Page.EDIT));
        button(left() + half + 6, footer(), w - half - 6, "Send offer", b -> send()).active = !submitted;
    }

    private void send() {
        if (submitted || minecraft == null || minecraft.player == null || minecraft.player.connection == null) return;
        submitted = true;
        String name = partner.replace("\\", "\\\\").replace("\"", "\\\"");
        minecraft.player.connection.sendCommand("nation trade propose \"" + name + "\" "
                + offer + " " + amount(offerAmount) + " " + request + " " + amount(requestAmount));
        minecraft.setScreen(parent);
    }

    private static String label(String asset) {
        return asset.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + asset.substring(1).replace('_', ' ');
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top(), w = contentWidth();
        g.fill(x - 8, y - 6, x + w + 8, footer() + 26, 0xF012161D);
        String heading = switch (page) {
            case EDIT -> "New trade offer";
            case PARTNERS -> "Choose a nation";
            case OFFER -> "Choose what you give";
            case REQUEST -> "Choose what you get";
            case REVIEW -> "Review your offer";
        };
        g.drawCenteredString(font, heading, width / 2, y + 4, 0xFFFFFF);
        if (page == Page.EDIT) {
            g.drawString(font, "Trade with", x, y + 21, 0xD7DEE8, false);
            g.drawString(font, "You give", x, y + 58, 0xD7DEE8, false);
            g.drawString(font, "You get", x, y + 95, 0xD7DEE8, false);
            int amountX = x + w / 2 + 3;
            g.drawString(font, "Amount", amountX, y + 58, 0xD7DEE8, false);
            g.drawString(font, "Amount", amountX, y + 95, 0xD7DEE8, false);
            if (offer != null) g.drawString(font, "Available: " + available.getOrDefault(offer, 0L) + " " + label(offer),
                    x, y + 131, 0xAAB4C0, false);
            g.drawWordWrap(font, Component.literal(problem()), x, y + 145, w, 0xFFAB99);
        } else if (page == Page.REVIEW) {
            int row = y + 27;
            for (String text : List.of("To " + partner, "You give " + offerAmount + " " + label(offer),
                    "You get " + requestAmount + " " + label(request),
                    "Sending reserves what you give until the offer is accepted, declined, or canceled.")) {
                for (var line : font.split(Component.literal(text), w)) {
                    g.drawString(font, line, x, row, 0xD7DEE8, false);
                    row += 11;
                }
                row += 6;
            }
        } else {
            int total = page == Page.PARTNERS ? partners.size() : assets.size();
            g.drawCenteredString(font, "Page " + (pickerPage + 1) + " of " + ((total + 4) / 5),
                    width / 2, footer() - 14, 0xAAB4C0);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (page != Page.EDIT) show(Page.EDIT);
        else if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
