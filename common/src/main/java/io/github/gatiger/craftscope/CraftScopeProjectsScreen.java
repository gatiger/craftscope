package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CraftScopeProjectsScreen
        extends CraftScopeBaseScreen {

    private static final int ROW_HEIGHT =
            28;

    private static final int MAX_VISIBLE_ROWS =
            6;

    private final Screen parent;

    private int scrollOffset;

    public CraftScopeProjectsScreen(
            Screen parent
    ) {
        super(
                Component.literal(
                        "CraftScope Projects"
                )
        );

        this.parent =
                parent;
    }

    @Override
    protected void init() {
        super.init();

        List<CraftScopeProject> projects =
                CraftScopeProjectManager.getProjects();

        int left =
                craftscope$getStandardWindowLeft();

        int right =
                craftscope$getStandardWindowRight();

        int centerX =
                craftscope$getStandardCenterX();

        int top =
                craftscope$getStandardWindowTop();

        int listTop =
                top + 96;

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - 75,
                        top + 58,
                        150,
                        24,
                        Component.literal(
                                "New Project"
                        ),
                        () ->
                                minecraft.setScreen(
                                        new CraftScopeNewProjectScreen(
                                                this
                                        )
                                )
                )
        );

        int remaining =
                projects.size()
                        - scrollOffset;

        int visibleCount =
                Math.min(
                        MAX_VISIBLE_ROWS,
                        Math.max(
                                0,
                                remaining
                        )
                );

        for (int i = 0;
             i < visibleCount;
             i++) {

            CraftScopeProject project =
                    projects.get(
                            scrollOffset + i
                    );

            String label =
                    project.getName();

            if (project.getTargetCount() > 1) {
                label +=
                        "   x"
                                + project.getTargetCount();
            }

            final CraftScopeProject selected =
                    project;

            addRenderableWidget(
                    new CraftScopeFlatButton(
                            left + 45,
                            listTop
                                    + i * ROW_HEIGHT,
                            right
                                    - left
                                    - 90,
                            22,
                            Component.literal(
                                    label
                            ),
                            () ->
                                    minecraft.setScreen(
                                            new CraftScopeProjectScreen(
                                                    this,
                                                    selected
                                            )
                                    )
                    )
            );
        }

        if (scrollOffset > 0) {

            addRenderableWidget(
                    new CraftScopeFlatButton(
                            right - 36,
                            listTop,
                            22,
                            22,
                            Component.literal(
                                    "▲"
                            ),
                            () -> {
                                scrollOffset--;

                                rebuildWidgets();
                            }
                    )
            );
        }

        if (scrollOffset
                + MAX_VISIBLE_ROWS
                < projects.size()) {

            addRenderableWidget(
                    new CraftScopeFlatButton(
                            right - 36,
                            listTop
                                    + (
                                    MAX_VISIBLE_ROWS - 1
                            ) * ROW_HEIGHT,
                            22,
                            22,
                            Component.literal(
                                    "▼"
                            ),
                            () -> {
                                scrollOffset++;

                                rebuildWidgets();
                            }
                    )
            );
        }

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
                "Projects",
                "Saved CraftScope projects"
        );

        int panelTop =
                craftscope$getStandardWindowTop() + 88;

        int panelBottom =
                craftscope$getStandardWindowBottom() - 52;

        craftscope$drawContentPanel(
                graphics,
                panelTop,
                panelBottom
        );

        List<CraftScopeProject> projects =
                CraftScopeProjectManager.getProjects();

        if (projects.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "No projects yet.",
                    craftscope$getStandardCenterX(),
                    panelTop + 48,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            graphics.drawCenteredString(
                    font,
                    "Create one to start planning a craft.",
                    craftscope$getStandardCenterX(),
                    panelTop + 66,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }

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