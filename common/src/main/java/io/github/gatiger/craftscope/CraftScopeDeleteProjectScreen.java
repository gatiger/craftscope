package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeDeleteProjectScreen
        extends CraftScopeBaseScreen {

    private final Screen parent;
    private final Screen projectsScreen;
    private final CraftScopeProject project;

    public CraftScopeDeleteProjectScreen(
            Screen parent,
            Screen projectsScreen,
            CraftScopeProject project
    ) {
        super(
                Component.literal(
                        "Delete Project"
                )
        );

        this.parent =
                parent;

        this.projectsScreen =
                projectsScreen;

        this.project =
                project;
    }

    @Override
    protected void init() {
        super.init();

        int centerX =
                craftscope$getStandardCenterX();

        int top =
                craftscope$getStandardWindowTop();

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - 104,
                        top + 160,
                        98,
                        24,
                        Component.literal(
                                "Cancel"
                        ),
                        () ->
                                minecraft.setScreen(
                                        parent
                                )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX + 6,
                        top + 160,
                        98,
                        24,
                        Component.literal(
                                "Delete"
                        ),
                        this::deleteProject
                ).setDanger(
                        true
                )
        );
    }

    private void deleteProject() {
        CraftScopeProjectManager.deleteProject(
                project.getId()
        );

        minecraft.setScreen(
                projectsScreen
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
                "Delete Project",
                ""
        );

        int left =
                craftscope$getStandardWindowLeft() + 40;

        int right =
                craftscope$getStandardWindowRight() - 40;

        int top =
                craftscope$getStandardWindowTop() + 82;

        int bottom =
                craftscope$getStandardWindowTop() + 145;

        CraftScopeUiTheme.drawPanel(
                graphics,
                left,
                top,
                right,
                bottom,
                CraftScopeUiTheme.DANGER_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                left,
                top,
                right,
                bottom,
                CraftScopeUiTheme.DANGER
        );

        graphics.drawCenteredString(
                font,
                "Delete Project?",
                craftscope$getStandardCenterX(),
                top + 12,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        graphics.drawCenteredString(
                font,
                project.getName(),
                craftscope$getStandardCenterX(),
                top + 31,
                0xFFFFAAAA
        );

        graphics.drawCenteredString(
                font,
                "This cannot be undone.",
                craftscope$getStandardCenterX(),
                top + 47,
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