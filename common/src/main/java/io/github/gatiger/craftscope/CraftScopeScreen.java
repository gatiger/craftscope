package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeClientConfig;
import io.github.gatiger.craftscope.client.CraftScopeClientConfigManager;
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

        int buttonWidth = 150;
        int buttonHeight = 20;

        int centerX = width / 2;
        int startY = 80;

        addRenderableWidget(
                Button.builder(
                                Component.literal("Move Inventory Tab"),
                                button -> {
                                    CraftScopeClientConfig.setMoveTabMode(true);
                                    minecraft.setScreen(parent);
                                }
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
                                Component.literal("Reset to Automatic"),
                                button -> {
                                    CraftScopeClientConfig.setPlacementMode(
                                            CraftScopeClientConfig.PlacementMode.AUTO
                                    );

                                    CraftScopeClientConfig.setMoveTabMode(false);
                                    CraftScopeClientConfigManager.save();

                                    minecraft.setScreen(parent);
                                }
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
        renderBackground(
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

        String placementText =
                CraftScopeClientConfig.getPlacementMode()
                        == CraftScopeClientConfig.PlacementMode.AUTO
                                ? "Tab Placement: Automatic"
                                : "Tab Placement: Custom";

        graphics.drawCenteredString(
                font,
                placementText,
                width / 2,
                64,
                0xCCCCCC
        );

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}