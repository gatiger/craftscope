package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeProjectOptionsScreen
        extends CraftScopeBaseScreen {

    private final Screen parent;
    private final Screen projectsScreen;
    private final CraftScopeProject project;

    private EditBox nameField;

    private int fieldX;
    private int fieldY;

    public CraftScopeProjectOptionsScreen(
            Screen parent,
            Screen projectsScreen,
            CraftScopeProject project
    ) {
        super(
                Component.literal(
                        "Project Options"
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

        fieldX =
                centerX - 110;

        fieldY =
                top + 105;

        nameField =
                new EditBox(
                        font,
                        fieldX + 5,
                        fieldY + 4,
                        210,
                        16,
                        Component.literal(
                                "Project Name"
                        )
                );

        nameField.setBordered(
                false
        );

        nameField.setMaxLength(
                64
        );

        nameField.setValue(
                project.getName()
        );

        addRenderableWidget(
                nameField
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - 75,
                        top + 142,
                        150,
                        23,
                        Component.literal(
                                "Save Name"
                        ),
                        this::saveName
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - 75,
                        top + 178,
                        150,
                        23,
                        Component.literal(
                                "Delete Project"
                        ),
                        () ->
                                minecraft.setScreen(
                                        new CraftScopeDeleteProjectScreen(
                                                this,
                                                projectsScreen,
                                                project
                                        )
                                )
                ).setDanger(
                        true
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

        setInitialFocus(
                nameField
        );
    }

    private void saveName() {
        String value =
                nameField
                        .getValue()
                        .trim();

        if (value.isEmpty()) {
            return;
        }

        project.setName(
                value
        );

        CraftScopeProjectManager.save();

        minecraft.setScreen(
                parent
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
                "Project Options",
                project.getName()
        );

        int panelTop =
                craftscope$getStandardWindowTop() + 80;

        int panelBottom =
                craftscope$getStandardWindowBottom() - 52;

        craftscope$drawContentPanel(
                graphics,
                panelTop,
                panelBottom
        );

        graphics.drawString(
                font,
                "Project Name",
                fieldX,
                fieldY - 15,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        craftscope$drawFieldBackground(
                graphics,
                fieldX,
                fieldY,
                220,
                24
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