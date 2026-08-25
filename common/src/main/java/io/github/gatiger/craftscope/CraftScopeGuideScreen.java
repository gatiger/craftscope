package io.github.gatiger.craftscope;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeGuideScreen extends Screen {

    private final Screen parent;

    public CraftScopeGuideScreen(Screen parent) {
        super(Component.literal("CraftScope Guide"));
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
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        graphics.drawCenteredString(
                font,
                "CraftScope Guide",
                width / 2,
                30,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Searchable documentation will appear here.",
                width / 2,
                55,
                0xAAAAAA
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}