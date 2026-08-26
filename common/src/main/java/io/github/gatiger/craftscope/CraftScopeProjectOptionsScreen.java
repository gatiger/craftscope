package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeProjectOptionsScreen extends Screen {

    private final Screen parent;
    private final Screen projectsScreen;
    private final CraftScopeProject project;

    private EditBox nameField;

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
                width / 2;

        nameField =
                new EditBox(
                        font,
                        centerX - 100,
                        75,
                        200,
                        20,
                        Component.literal(
                                "Project Name"
                        )
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
                Button.builder(
                                Component.literal(
                                        "Save Name"
                                ),
                                button ->
                                        saveName()
                        )
                        .bounds(
                                centerX - 75,
                                105,
                                150,
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
                                        minecraft.setScreen(
                                                new CraftScopeDeleteProjectScreen(
                                                        this,
                                                        projectsScreen,
                                                        project
                                                )
                                        )
                        )
                        .bounds(
                                centerX - 75,
                                145,
                                150,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal(
                                        "Back"
                                ),
                                button ->
                                        minecraft.setScreen(
                                                parent
                                        )
                        )
                        .bounds(
                                centerX - 50,
                                height - 40,
                                100,
                                20
                        )
                        .build()
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
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        graphics.drawCenteredString(
                font,
                "Project Options",
                width / 2,
                25,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                project.getName(),
                width / 2,
                43,
                0xAAAAAA
        );

        graphics.drawCenteredString(
                font,
                "Project Name",
                width / 2,
                60,
                0xCCCCCC
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(
                parent
        );
    }
}