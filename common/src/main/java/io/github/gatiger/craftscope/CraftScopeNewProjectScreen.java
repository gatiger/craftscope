package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeNewProjectScreen
        extends CraftScopeBaseScreen {

    private final Screen parent;

    private EditBox nameField;

    private int fieldX;
    private int fieldY;

    public CraftScopeNewProjectScreen(
            Screen parent
    ) {
        super(
                Component.literal(
                        "New CraftScope Project"
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

        fieldX =
                centerX - 110;

        fieldY =
                top + 110;

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

        nameField.setHint(
                Component.literal(
                        "Enter project name"
                )
        );

        addRenderableWidget(
                nameField
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX - 88,
                        top + 150,
                        82,
                        22,
                        Component.literal(
                                "Create"
                        ),
                        this::createProject
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        centerX + 6,
                        top + 150,
                        82,
                        22,
                        Component.literal(
                                "Cancel"
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

    private void createProject() {
        String name =
                nameField
                        .getValue()
                        .trim();

        if (name.isEmpty()) {
            return;
        }

        CraftScopeProjectManager.createProject(
                name,
                "",
                1
        );

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
                "New Project",
                "Create a new crafting or production project"
        );

        int panelTop =
                craftscope$getStandardWindowTop() + 86;

        int panelBottom =
                craftscope$getStandardWindowBottom() - 72;

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