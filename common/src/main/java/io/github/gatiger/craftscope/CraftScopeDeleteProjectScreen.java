package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeDeleteProjectScreen extends Screen {

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
                width / 2;

        addRenderableWidget(
                Button.builder(
                                Component.literal(
                                        "Cancel"
                                ),
                                button ->
                                        minecraft.setScreen(
                                                parent
                                        )
                        )
                        .bounds(
                                centerX - 105,
                                120,
                                100,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal(
                                        "Delete Project"
                                ),
                                button ->
                                        deleteProject()
                        )
                        .bounds(
                                centerX + 5,
                                120,
                                100,
                                20
                        )
                        .build()
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
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        graphics.drawCenteredString(
                font,
                "Delete Project?",
                width / 2,
                45,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                project.getName(),
                width / 2,
                68,
                0xFFAAAA
        );

        graphics.drawCenteredString(
                font,
                "This cannot be undone.",
                width / 2,
                88,
                0xAAAAAA
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(
                parent
        );
    }
}