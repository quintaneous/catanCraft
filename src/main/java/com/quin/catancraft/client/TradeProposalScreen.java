package com.quin.catancraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public final class TradeProposalScreen extends Screen {
    private static final int WIDTH = 360;
    private final Screen parent;

    private EditBox nation;
    private EditBox offerAsset;
    private EditBox offerAmount;
    private EditBox requestAsset;
    private EditBox requestAmount;
    private String error = "";

    public TradeProposalScreen(Screen parent) {
        super(Component.literal("Create Trade Proposal"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int fieldX = left + 140;
        int fieldWidth = WIDTH - 160;
        int y = 66;

        nation = field(fieldX, y, fieldWidth, "Nation");
        y += 32;
        offerAsset = field(fieldX, y, fieldWidth, "Offer asset");
        offerAsset.setValue("money");
        y += 32;
        offerAmount = field(fieldX, y, fieldWidth, "Offer amount");
        offerAmount.setValue("1000");
        y += 32;
        requestAsset = field(fieldX, y, fieldWidth, "Request asset");
        requestAsset.setValue("steel");
        y += 32;
        requestAmount = field(fieldX, y, fieldWidth, "Request amount");
        requestAmount.setValue("100");

        int buttonY = y + 42;
        addRenderableWidget(Button.builder(
                Component.literal("Send Proposal"),
                button -> submit()
        ).bounds(left + 20, buttonY, 150, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(left + 190, buttonY, 150, 20).build());

        setInitialFocus(nation);
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setMaxLength(64);
        addRenderableWidget(box);
        return box;
    }

    private void submit() {
        String nationName = nation.getValue().trim();
        String offered = offerAsset.getValue().trim().toLowerCase();
        String wanted = requestAsset.getValue().trim().toLowerCase();

        if (nationName.isBlank() || offered.isBlank() || wanted.isBlank()) {
            error = "Nation and both asset fields are required.";
            return;
        }

        long offeredAmount;
        long wantedAmount;
        try {
            offeredAmount = Long.parseLong(offerAmount.getValue().trim());
            wantedAmount = Long.parseLong(requestAmount.getValue().trim());
        } catch (NumberFormatException ex) {
            error = "Amounts must be whole numbers.";
            return;
        }

        if (offeredAmount <= 0 || wantedAmount <= 0) {
            error = "Amounts must be greater than zero.";
            return;
        }

        if (!offered.matches("[a-z0-9_]+") || !wanted.matches("[a-z0-9_]+")) {
            error = "Assets must be resource IDs such as steel, oil, or money.";
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            error = "Not connected to a server.";
            return;
        }

        String safeNation = nationName
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        minecraft.player.connection.sendCommand(
                "nation trade propose \"" + safeNation + "\" " +
                        offered + " " + offeredAmount + " " +
                        wanted + " " + wantedAmount
        );
    }

    @Override
    public void render(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics);

        int left = (this.width - WIDTH) / 2;
        int right = left + WIDTH;
        int top = 24;
        int bottom = Math.min(this.height - 24, 292);

        graphics.fill(left, top, right, bottom, 0xF012161D);
        graphics.fill(left, top, right, top + 28, 0xF0243044);
        graphics.drawCenteredString(
                font,
                "CREATE TRADE PROPOSAL",
                this.width / 2,
                top + 10,
                0xFFFFFF
        );

        int labelX = left + 20;
        int y = 72;
        graphics.drawString(font, "Nation", labelX, y, 0xD7DEE8, false);
        y += 32;
        graphics.drawString(font, "You offer", labelX, y, 0x7EE787, false);
        y += 32;
        graphics.drawString(font, "Offer amount", labelX, y, 0xD7DEE8, false);
        y += 32;
        graphics.drawString(font, "You request", labelX, y, 0xFFD166, false);
        y += 32;
        graphics.drawString(font, "Request amount", labelX, y, 0xD7DEE8, false);

        graphics.drawString(
                font,
                "Asset IDs: money, wood, stone, agriculture, iron, coal, oil, copper,",
                left + 20,
                238,
                0x8B949E,
                false
        );
        graphics.drawString(
                font,
                "steel, concrete, fabric, fuel, mechanical_parts, electronics, explosives",
                left + 20,
                250,
                0x8B949E,
                false
        );

        if (!error.isBlank()) {
            graphics.drawCenteredString(
                    font,
                    error,
                    this.width / 2,
                    270,
                    0xFF7B72
            );
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
