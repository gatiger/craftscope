package io.github.gatiger.craftscope;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeProjectsScreen extends Screen {

    private final Screen parent;

    public CraftScopeProjectsScreen(Screen parent) {
        super(Component.literal("CraftScope Projects"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(
                Button.builder(
                                Component.literal("Back"),
                                button -> minecraft.setScreen(parent)
                        )
                        .bounds(
                                width / 2 - 50,
                                height - 40,
                                100,
                                20
                        )
                        .build()
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(
                font,
                "Projects",
                width / 2,
                30,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Your crafting projects will appear here.",
                width / 2,
                55,
                0xAAAAAA
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}