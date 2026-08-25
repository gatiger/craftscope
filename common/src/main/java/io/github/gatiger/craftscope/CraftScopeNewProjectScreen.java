package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CraftScopeNewProjectScreen extends Screen {

    private final Screen parent;

    private EditBox nameField;

    public CraftScopeNewProjectScreen(Screen parent) {
        super(Component.literal("New CraftScope Project"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;

        nameField = new EditBox(
                font,
                centerX - 100,
                80,
                200,
                20,
                Component.literal("Project Name")
        );

        nameField.setMaxLength(64);
        nameField.setHint(
                Component.literal("Enter project name")
        );

        addRenderableWidget(nameField);

        addRenderableWidget(
                Button.builder(
                                Component.literal("Create"),
                                button -> createProject()
                        )
                        .bounds(
                                centerX - 75,
                                115,
                                70,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("Cancel"),
                                button -> minecraft.setScreen(parent)
                        )
                        .bounds(
                                centerX + 5,
                                115,
                                70,
                                20
                        )
                        .build()
        );

        setInitialFocus(nameField);
    }

    private void createProject() {
        String name = nameField.getValue().trim();

        if (name.isEmpty()) {
            return;
        }

        CraftScopeProjectManager.createProject(
                name,
                "",
                1
        );

        minecraft.setScreen(parent);
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
                "Create Project",
                width / 2,
                35,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Project Name",
                width / 2,
                65,
                0xCCCCCC
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}