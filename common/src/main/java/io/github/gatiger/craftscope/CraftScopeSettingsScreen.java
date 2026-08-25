package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeClientConfig;
import io.github.gatiger.craftscope.client.CraftScopeClientConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeSettingsScreen extends Screen {

    private final Screen parent;
    private final Screen containerScreen;

    public CraftScopeSettingsScreen(
            Screen parent,
            Screen containerScreen
    ) {
        super(Component.literal("CraftScope Settings"));

        this.parent = parent;
        this.containerScreen = containerScreen;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 150;
        int centerX = width / 2;

        addRenderableWidget(
                Button.builder(
                                Component.literal("Move Inventory Tab"),
                                button -> {
                                    CraftScopeClientConfig.setMoveTabMode(true);
                                    minecraft.setScreen(containerScreen);
                                }
                        )
                        .bounds(
                                centerX - buttonWidth / 2,
                                85,
                                buttonWidth,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Reset to Automatic"),
                                button -> {
                                    CraftScopeClientConfig.setPlacementMode(
                                            CraftScopeClientConfig
                                                    .PlacementMode.AUTO
                                    );

                                    CraftScopeClientConfig.setMoveTabMode(false);
                                    CraftScopeClientConfigManager.save();

                                    minecraft.setScreen(containerScreen);
                                }
                        )
                        .bounds(
                                centerX - buttonWidth / 2,
                                113,
                                buttonWidth,
                                20
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
                "Settings",
                width / 2,
                30,
                0xFFFFFF
        );

        String placement =
                CraftScopeClientConfig.getPlacementMode()
                        == CraftScopeClientConfig.PlacementMode.AUTO
                        ? "Inventory Tab: Automatic"
                        : "Inventory Tab: Custom";

        graphics.drawCenteredString(
                font,
                placement,
                width / 2,
                58,
                0xCCCCCC
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}