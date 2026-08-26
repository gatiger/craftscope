package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeScreen
        extends CraftScopeBaseScreen {

    private final Screen parent;

    public CraftScopeScreen(
            Screen parent
    ) {
        super(
                Component.literal(
                        "CraftScope"
                )
        );

        this.parent =
                parent;
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

        int buttonHeight =
                24;

        int gap =
                8;

        int startY =
                top + 88;

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - buttonWidth / 2,
                        startY,
                        buttonWidth,
                        buttonHeight,
                        Component.literal(
                                "Projects"
                        ),
                        () ->
                                minecraft.setScreen(
                                        new CraftScopeProjectsScreen(
                                                this
                                        )
                                )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - buttonWidth / 2,
                        startY
                                + buttonHeight
                                + gap,
                        buttonWidth,
                        buttonHeight,
                        Component.literal(
                                "Guide"
                        ),
                        () ->
                                minecraft.setScreen(
                                        new CraftScopeGuideScreen(
                                                this
                                        )
                                )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - buttonWidth / 2,
                        startY
                                + (
                                buttonHeight
                                        + gap
                        ) * 2,
                        buttonWidth,
                        buttonHeight,
                        Component.literal(
                                "Settings"
                        ),
                        () ->
                                minecraft.setScreen(
                                        new CraftScopeSettingsScreen(
                                                this,
                                                parent
                                        )
                                )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - 50,
                        craftscope$getStandardWindowBottom() - 38,
                        100,
                        22,
                        Component.literal(
                                "Exit"
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
                "",
                "See the whole craft."
        );

        int left =
                craftscope$getStandardWindowLeft() + 16;

        int right =
                craftscope$getStandardWindowRight() - 16;

        int top =
                craftscope$getStandardWindowTop() + 72;

        int bottom =
                craftscope$getStandardWindowBottom() - 52;

        CraftScopeUiTheme.drawPanel(
                graphics,
                left,
                top,
                right,
                bottom
        );

        graphics.drawCenteredString(
                font,
                "Plan complex crafting and production chains.",
                craftscope$getStandardCenterX(),
                top + 12,
                CraftScopeUiTheme.TEXT_SECONDARY
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