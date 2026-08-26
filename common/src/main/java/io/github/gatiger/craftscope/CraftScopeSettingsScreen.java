package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeClientConfig;
import io.github.gatiger.craftscope.client.CraftScopeClientConfigManager;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeSettingsScreen
        extends CraftScopeBaseScreen {

    private final Screen parent;
    private final Screen containerScreen;

    public CraftScopeSettingsScreen(
            Screen parent,
            Screen containerScreen
    ) {
        super(
                Component.literal(
                        "CraftScope Settings"
                )
        );

        this.parent =
                parent;

        this.containerScreen =
                containerScreen;
    }

    @Override
    protected void init() {
        super.init();

        int centerX =
                craftscope$getStandardCenterX();

        int top =
                craftscope$getStandardWindowTop();

        int buttonWidth =
                190;

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - buttonWidth / 2,
                        top + 122,
                        buttonWidth,
                        24,
                        Component.literal(
                                "Move Inventory Tab"
                        ),
                        () -> {
                            CraftScopeClientConfig.setMoveTabMode(
                                    true
                            );

                            minecraft.setScreen(
                                    containerScreen
                            );
                        }
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - buttonWidth / 2,
                        top + 154,
                        buttonWidth,
                        24,
                        Component.literal(
                                "Reset to Automatic"
                        ),
                        () -> {
                            CraftScopeClientConfig.setPlacementMode(
                                    CraftScopeClientConfig
                                            .PlacementMode.AUTO
                            );

                            CraftScopeClientConfig.setMoveTabMode(
                                    false
                            );

                            CraftScopeClientConfigManager.save();

                            minecraft.setScreen(
                                    containerScreen
                            );
                        }
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - 50,
                        craftscope$getStandardWindowBottom() - 38,
                        100,
                        22,
                        Component.literal(
                                "Back"
                        ),
                        () ->
                                minecraft.setScreen(
                                        parent
                                )
                )
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        craftscope$renderStandardShell(
                graphics,
                "Settings",
                "CraftScope client preferences"
        );

        int panelTop =
                craftscope$getStandardWindowTop() + 82;

        int panelBottom =
                craftscope$getStandardWindowBottom() - 52;

        craftscope$drawContentPanel(
                graphics,
                panelTop,
                panelBottom
        );

        String placement =
                CraftScopeClientConfig.getPlacementMode()
                        == CraftScopeClientConfig.PlacementMode.AUTO
                        ? "Automatic"
                        : "Custom";

        graphics.drawString(
                font,
                "Inventory Tab Position",
                craftscope$getStandardWindowLeft() + 32,
                panelTop + 18,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        graphics.drawString(
                font,
                "Current mode: "
                        + placement,
                craftscope$getStandardWindowLeft() + 32,
                panelTop + 38,
                CraftScopeUiTheme.TEXT_MUTED
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
        minecraft.setScreen(
                parent
        );
    }
}