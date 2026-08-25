package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CraftScopeProjectsScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int MAX_VISIBLE_ROWS = 6;

    private final Screen parent;

    private int scrollOffset = 0;

    public CraftScopeProjectsScreen(Screen parent) {
        super(Component.literal("CraftScope Projects"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        List<CraftScopeProject> projects =
                CraftScopeProjectManager.getProjects();

        int centerX = width / 2;
        int listTop = 70;

        addRenderableWidget(
                Button.builder(
                                Component.literal("New Project"),
                                button -> minecraft.setScreen(
                                        new CraftScopeNewProjectScreen(this)
                                )
                        )
                        .bounds(
                                centerX - 70,
                                42,
                                140,
                                20
                        )
                        .build()
        );

        int remaining = projects.size() - scrollOffset;

        int visibleCount = Math.min(
                MAX_VISIBLE_ROWS,
                Math.max(0, remaining)
        );

        for (int i = 0; i < visibleCount; i++) {

            CraftScopeProject project =
                    projects.get(scrollOffset + i);

            String label = project.getName();

            if (project.getTargetCount() > 1) {
                label += "  x" + project.getTargetCount();
            }

            addRenderableWidget(
                    Button.builder(
                                    Component.literal(label),
                                    button -> minecraft.setScreen(
                                            new CraftScopeProjectScreen(
                                                    this,
                                                    project
                                            )
                                    )
                            )
                            .bounds(
                                    centerX - 100,
                                    listTop + (i * ROW_HEIGHT),
                                    200,
                                    20
                            )
                            .build()
            );
        }

        if (scrollOffset > 0) {
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("▲"),
                                    button -> {
                                        scrollOffset--;
                                        rebuildWidgets();
                                    }
                            )
                            .bounds(
                                    centerX + 106,
                                    listTop,
                                    20,
                                    20
                            )
                            .build()
            );
        }

        if (scrollOffset + MAX_VISIBLE_ROWS < projects.size()) {
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("▼"),
                                    button -> {
                                        scrollOffset++;
                                        rebuildWidgets();
                                    }
                            )
                            .bounds(
                                    centerX + 106,
                                    listTop
                                            + ((MAX_VISIBLE_ROWS - 1)
                                            * ROW_HEIGHT),
                                    20,
                                    20
                            )
                            .build()
            );
        }

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
                "Projects",
                width / 2,
                20,
                0xFFFFFF
        );

        if (CraftScopeProjectManager.getProjects().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    "No projects yet.",
                    width / 2,
                    85,
                    0xAAAAAA
            );

            graphics.drawCenteredString(
                    font,
                    "Create one to start planning a craft.",
                    width / 2,
                    100,
                    0x777777
            );
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}