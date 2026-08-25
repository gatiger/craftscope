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

        int buttonWidth = 140;
        int buttonHeight = 20;
        int centerX = width / 2;
        int startY = 85;

        addRenderableWidget(
                Button.builder(
                                Component.literal("Projects"),
                                button -> minecraft.setScreen(
                                        new CraftScopeProjectsScreen(this)
                                )
                        )
                        .bounds(
                                centerX - buttonWidth / 2,
                                startY,
                                buttonWidth,
                                buttonHeight
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Guide"),
                                button -> minecraft.setScreen(
                                        new CraftScopeGuideScreen(this)
                                )
                        )
                        .bounds(
                                centerX - buttonWidth / 2,
                                startY + 28,
                                buttonWidth,
                                buttonHeight
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Settings"),
                                button -> minecraft.setScreen(
                                        new CraftScopeSettingsScreen(
                                                this,
                                                parent
                                        )
                                )
                        )
                        .bounds(
                                centerX - buttonWidth / 2,
                                startY + 56,
                                buttonWidth,
                                buttonHeight
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Back"),
                                button -> minecraft.setScreen(parent)
                        )
                        .bounds(
                                centerX - 50,
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
                "CraftScope",
                width / 2,
                30,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "See the whole craft.",
                width / 2,
                48,
                0xAAAAAA
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}