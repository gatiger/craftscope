package io.github.gatiger.craftscope;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeScreen extends Screen {

    private final Screen parent;

    public CraftScopeScreen(Screen parent) {
        super(Component.literal("CraftScope"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 100;
        int buttonHeight = 20;

        addRenderableWidget(
                Button.builder(
                                Component.literal("Back"),
                                button -> minecraft.setScreen(parent)
                        )
                        .bounds(
                                (width - buttonWidth) / 2,
                                height - 40,
                                buttonWidth,
                                buttonHeight
                        )
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(
                font,
                "CraftScope",
                width / 2,
                30,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "See the whole craft.",
                width / 2,
                50,
                0xAAAAAA
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}