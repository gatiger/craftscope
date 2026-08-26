package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeGuideScreen
        extends CraftScopeBaseScreen {

    private final Screen parent;

    public CraftScopeGuideScreen(
            Screen parent
    ) {
        super(
                Component.literal(
                        "CraftScope Guide"
                )
        );

        this.parent =
                parent;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(
                new CraftScopeFlatButton(
                        craftscope$getStandardCenterX() - 50,
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
                "Guide",
                "CraftScope help and documentation"
        );

        int left =
                craftscope$getStandardWindowLeft() + 14;

        int right =
                craftscope$getStandardWindowRight() - 14;

        int top =
                craftscope$getStandardWindowTop() + 82;

        int bottom =
                craftscope$getStandardWindowBottom() - 52;

        CraftScopeUiTheme.drawPanel(
                graphics,
                left,
                top,
                right,
                bottom
        );

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                left + 1,
                top + 1,
                right - 1,
                top + 24
        );

        graphics.drawString(
                font,
                "Getting Started",
                left + 9,
                top + 8,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        graphics.drawString(
                font,
                "1. Create or open a project.",
                left + 12,
                top + 42,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "2. Choose a target item from JEI on Recipe Tree.",
                left + 12,
                top + 61,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "3. Set the quantity you want to produce.",
                left + 12,
                top + 80,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "4. Explore recipes, materials and production routes.",
                left + 12,
                top + 99,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "Searchable documentation will be added here later.",
                left + 12,
                bottom - 28,
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