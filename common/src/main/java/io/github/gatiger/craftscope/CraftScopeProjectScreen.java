package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeTargetItemReceiver;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummary;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummarizer;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeResolver;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftScopeProjectScreen extends Screen
        implements CraftScopeTargetItemReceiver {

    private static final int TARGET_SLOT_SIZE = 24;

    private static final int CONTENT_ROW_HEIGHT = 20;
    private static final int TREE_INDENT = 18;

    private static final int CONTENT_SIDE_MARGIN = 20;
    private static final int VIEW_BUTTON_Y = 165;
    private static final int CONTENT_TITLE_Y = 192;
    private static final int CONTENT_VIEWPORT_TOP = 210;
    private static final int CONTENT_BOTTOM_MARGIN = 65;

    private static final int VIEW_BUTTON_WIDTH = 100;
    private static final int VIEW_BUTTON_HEIGHT = 20;
    private static final int VIEW_BUTTON_GAP = 6;

    private static final int SCROLL_AMOUNT = 20;

    private static final long VARIANT_CYCLE_MS = 1000L;

    private final Screen parent;
    private final CraftScopeProject project;

    /*
     * Nodes start collapsed.
     *
     * Expansion state lasts only while this screen instance
     * remains open.
     */
    private final Set<String> expandedNodes =
            new HashSet<>();

    /*
     * Active recipe overrides loaded from the saved project.
     */
    private final Map<String, ResourceLocation> recipeOverrides =
            new HashMap<>();

    /*
     * Stable recipe-choice ordering for the [1/2] selector.
     */
    private final Map<String, List<ResourceLocation>> recipeChoices =
            new HashMap<>();

    private int targetSlotX;
    private int targetSlotY;

    private EditBox quantityField;

    private Button recipeTreeButton;
    private Button totalMaterialsButton;
    private Button processDiagramButton;
    private Button setupButton;

    private ViewMode activeView =
            ViewMode.RECIPE_TREE;

    private double treeScroll;
    private double materialScroll;

    private CraftScopeRecipeTree currentTree;

    private CraftScopeMaterialSummary currentMaterialSummary =
            new CraftScopeMaterialSummary(
                    List.of()
            );

    public CraftScopeProjectScreen(
            Screen parent,
            CraftScopeProject project
    ) {
        super(
                Component.literal(
                        project.getName()
                )
        );

        this.parent =
                parent;

        this.project =
                project;

        loadRecipeOverrides();
    }

    private void loadRecipeOverrides() {
        recipeOverrides.clear();

        for (Map.Entry<String, String> entry :
                project.getRecipeOverrides().entrySet()) {

            ResourceLocation recipeId =
                    ResourceLocation.tryParse(
                            entry.getValue()
                    );

            if (recipeId != null) {

                recipeOverrides.put(
                        entry.getKey(),
                        recipeId
                );
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        targetSlotX =
                width / 2
                        - TARGET_SLOT_SIZE / 2;

        targetSlotY =
                75;

        int centerX =
                width / 2;

        /*
         * -----------------------------------------------------
         * Target quantity
         * -----------------------------------------------------
         */

        quantityField =
                new EditBox(
                        font,
                        centerX - 25,
                        118,
                        50,
                        20,
                        Component.literal(
                                "Quantity"
                        )
                );

        quantityField.setValue(
                Integer.toString(
                        project.getTargetCount()
                )
        );

        quantityField.setFilter(
                value ->
                        value.isEmpty()
                                || value.matches(
                                "\\d+"
                        )
        );

        quantityField.setResponder(
                this::craftscope$quantityChanged
        );

        addRenderableWidget(
                quantityField
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("-"),
                                button ->
                                        craftscope$changeQuantity(
                                                -1
                                        )
                        )
                        .bounds(
                                centerX - 50,
                                118,
                                20,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal("+"),
                                button ->
                                        craftscope$changeQuantity(
                                                1
                                        )
                        )
                        .bounds(
                                centerX + 30,
                                118,
                                20,
                                20
                        )
                        .build()
        );

        /*
         * -----------------------------------------------------
         * Main project tabs
         * -----------------------------------------------------
         */

        int totalViewWidth =
                VIEW_BUTTON_WIDTH
                        * 4
                        + VIEW_BUTTON_GAP
                        * 3;

        int firstViewButtonX =
                centerX
                        - totalViewWidth / 2;

        recipeTreeButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal(
                                                "Recipe Tree"
                                        ),
                                        button ->
                                                setActiveView(
                                                        ViewMode.RECIPE_TREE
                                                )
                                )
                                .bounds(
                                        firstViewButtonX,
                                        VIEW_BUTTON_Y,
                                        VIEW_BUTTON_WIDTH,
                                        VIEW_BUTTON_HEIGHT
                                )
                                .build()
                );

        totalMaterialsButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal(
                                                "Total Materials"
                                        ),
                                        button ->
                                                setActiveView(
                                                        ViewMode.TOTAL_MATERIALS
                                                )
                                )
                                .bounds(
                                        firstViewButtonX
                                                + VIEW_BUTTON_WIDTH
                                                + VIEW_BUTTON_GAP,
                                        VIEW_BUTTON_Y,
                                        VIEW_BUTTON_WIDTH,
                                        VIEW_BUTTON_HEIGHT
                                )
                                .build()
                );

        processDiagramButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal(
                                                "Process Diagram"
                                        ),
                                        button ->
                                                setActiveView(
                                                        ViewMode.PROCESS_DIAGRAM
                                                )
                                )
                                .bounds(
                                        firstViewButtonX
                                                + (
                                                VIEW_BUTTON_WIDTH
                                                        + VIEW_BUTTON_GAP
                                        ) * 2,
                                        VIEW_BUTTON_Y,
                                        VIEW_BUTTON_WIDTH,
                                        VIEW_BUTTON_HEIGHT
                                )
                                .build()
                );

        setupButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal(
                                                "Setup"
                                        ),
                                        button ->
                                                setActiveView(
                                                        ViewMode.SETUP
                                                )
                                )
                                .bounds(
                                        firstViewButtonX
                                                + (
                                                VIEW_BUTTON_WIDTH
                                                        + VIEW_BUTTON_GAP
                                        ) * 3,
                                        VIEW_BUTTON_Y,
                                        VIEW_BUTTON_WIDTH,
                                        VIEW_BUTTON_HEIGHT
                                )
                                .build()
                );

        /*
         * -----------------------------------------------------
         * Top-right controls
         * -----------------------------------------------------
         */

        int topButtonY =
                8;

        int exitWidth =
                45;

        int optionsWidth =
                60;

        int helpWidth =
                50;

        int topGap =
                4;

        int exitX =
                width
                        - CONTENT_SIDE_MARGIN
                        - exitWidth;

        int optionsX =
                exitX
                        - topGap
                        - optionsWidth;

        int helpX =
                optionsX
                        - topGap
                        - helpWidth;

        addRenderableWidget(
                Button.builder(
                                Component.literal(
                                        "Help"
                                ),
                                button ->
                                        minecraft.setScreen(
                                                new CraftScopeGuideScreen(
                                                        this
                                                )
                                        )
                        )
                        .bounds(
                                helpX,
                                topButtonY,
                                helpWidth,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal(
                                        "Options"
                                ),
                                button ->
                                        minecraft.setScreen(
                                                new CraftScopeProjectOptionsScreen(
                                                        this,
                                                        parent,
                                                        project
                                                )
                                        )
                        )
                        .bounds(
                                optionsX,
                                topButtonY,
                                optionsWidth,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.literal(
                                        "Exit"
                                ),
                                button ->
                                        minecraft.setScreen(
                                                parent
                                        )
                        )
                        .bounds(
                                exitX,
                                topButtonY,
                                exitWidth,
                                20
                        )
                        .build()
        );

        treeScroll =
                0;

        materialScroll =
                0;

        updateViewButtons();

        rebuildTree();
    }

    private void setActiveView(
            ViewMode viewMode
    ) {
        activeView =
                viewMode;

        switch (activeView) {

            case RECIPE_TREE ->
                    clampTreeScroll();

            case TOTAL_MATERIALS ->
                    clampMaterialScroll();

            case PROCESS_DIAGRAM,
                 SETUP -> {
            }
        }

        updateViewButtons();
    }

    private void updateViewButtons() {
        if (recipeTreeButton != null) {

            recipeTreeButton.active =
                    activeView
                            != ViewMode.RECIPE_TREE;
        }

        if (totalMaterialsButton != null) {

            totalMaterialsButton.active =
                    activeView
                            != ViewMode.TOTAL_MATERIALS;
        }

        if (processDiagramButton != null) {

            processDiagramButton.active =
                    activeView
                            != ViewMode.PROCESS_DIAGRAM;
        }

        if (setupButton != null) {

            setupButton.active =
                    activeView
                            != ViewMode.SETUP;
        }
    }

    private void rebuildTree() {
        ItemStack target =
                getTargetStack();

        if (target.isEmpty()) {

            currentTree =
                    null;

            currentMaterialSummary =
                    new CraftScopeMaterialSummary(
                            List.of()
                    );

            treeScroll =
                    0;

            materialScroll =
                    0;

            return;
        }

        currentTree =
                CraftScopeRecipeResolver.resolveTree(
                        target,
                        project.getTargetCount(),
                        recipeOverrides
                );

        currentMaterialSummary =
                CraftScopeMaterialSummarizer.summarize(
                        currentTree
                );

        populateRecipeChoices();

        clampTreeScroll();
        clampMaterialScroll();
    }

    private void populateRecipeChoices() {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return;
        }

        populateRecipeChoices(
                currentTree.getRoot(),
                "root"
        );
    }

    private void populateRecipeChoices(
            CraftScopeRecipeNode node,
            String nodePath
    ) {
        if (node.getPreferredRecipeId() != null
                && node.getTotalRecipeCount() > 1
                && !recipeChoices.containsKey(
                nodePath
        )) {

            List<ResourceLocation> choices =
                    new ArrayList<>();

            ResourceLocation selected =
                    node.getPreferredRecipeId();

            String savedOverrideString =
                    project.getRecipeOverride(
                            nodePath
                    );

            ResourceLocation savedOverride =
                    savedOverrideString == null
                            ? null
                            : ResourceLocation.tryParse(
                            savedOverrideString
                    );

            if (savedOverride == null) {

                choices.add(
                        selected
                );

                choices.addAll(
                        node.getAlternativeRecipeIds()
                );

            } else {

                List<ResourceLocation> allRecipes =
                        new ArrayList<>(
                                node.getAlternativeRecipeIds()
                        );

                allRecipes.add(
                        savedOverride
                );

                ResourceLocation defaultRecipe =
                        null;

                for (ResourceLocation id :
                        node.getAlternativeRecipeIds()) {

                    if (!id.equals(
                            savedOverride
                    )) {

                        defaultRecipe =
                                id;

                        break;
                    }
                }

                if (defaultRecipe != null) {

                    choices.add(
                            defaultRecipe
                    );
                }

                for (ResourceLocation id :
                        allRecipes) {

                    if (!choices.contains(
                            id
                    )) {

                        choices.add(
                                id
                        );
                    }
                }
            }

            recipeChoices.put(
                    nodePath,
                    choices
            );
        }

        List<CraftScopeRecipeNode> children =
                node.getChildren();

        for (int i = 0;
             i < children.size();
             i++) {

            CraftScopeRecipeNode child =
                    children.get(i);

            String childPath =
                    buildChildPath(
                            nodePath,
                            i,
                            child
                    );

            populateRecipeChoices(
                    child,
                    childPath
            );
        }
    }

    private void craftscope$changeQuantity(
            int amount
    ) {
        int current =
                craftscope$getQuantityFromField();

        int updated =
                Math.max(
                        1,
                        current + amount
                );

        quantityField.setValue(
                Integer.toString(
                        updated
                )
        );
    }

    private int craftscope$getQuantityFromField() {
        try {

            int value =
                    Integer.parseInt(
                            quantityField.getValue()
                    );

            return Math.max(
                    1,
                    value
            );

        } catch (NumberFormatException e) {

            return 1;
        }
    }

    private void craftscope$quantityChanged(
            String value
    ) {
        if (value == null
                || value.isEmpty()) {

            return;
        }

        try {

            int quantity =
                    Integer.parseInt(
                            value
                    );

            if (quantity < 1) {
                return;
            }

            project.setTargetCount(
                    quantity
            );

            CraftScopeProjectManager.save();

            rebuildTree();

        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        /*
         * Render Minecraft's background and widgets first.
         *
         * CraftScope's custom content is drawn afterward so it
         * stays sharp.
         */
        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );

        /*
         * Permanent CraftScope branding.
         */
        graphics.drawString(
                font,
                "CRAFTSCOPE",
                CONTENT_SIDE_MARGIN,
                12,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                project.getName(),
                width / 2,
                25,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Target Item",
                width / 2,
                55,
                0xCCCCCC
        );

        renderTargetSlot(
                graphics,
                mouseX,
                mouseY
        );

        String targetId =
                project.getTargetItemId();

        if (targetId == null
                || targetId.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "Drop an item here from JEI/EMI",
                    width / 2,
                    105,
                    0x888888
            );
        }

        graphics.drawCenteredString(
                font,
                "Quantity",
                width / 2,
                105,
                0xCCCCCC
        );

        switch (activeView) {

            case RECIPE_TREE ->
                    renderRecipeTree(
                            graphics,
                            mouseX,
                            mouseY
                    );

            case TOTAL_MATERIALS ->
                    renderTotalMaterials(
                            graphics,
                            mouseX,
                            mouseY
                    );

            case PROCESS_DIAGRAM ->
                    renderProcessDiagramPlaceholder(
                            graphics
                    );

            case SETUP ->
                    renderSetupPlaceholder(
                            graphics
                    );
        }
    }

    /*
     * ---------------------------------------------------------
     * Process Diagram placeholder
     * ---------------------------------------------------------
     *
     * This establishes the permanent three-column visual shell:
     *
     * Production Routes
     * Process Diagram
     * Selected Item / Machine
     *
     * The real production-route engine will populate these
     * panels in later steps.
     */
    private void renderProcessDiagramPlaceholder(
            GuiGraphics graphics
    ) {
        int top =
                CONTENT_VIEWPORT_TOP;

        int bottom =
                getViewportBottom();

        int left =
                CONTENT_SIDE_MARGIN;

        int right =
                width
                        - CONTENT_SIDE_MARGIN;

        int gap =
                6;

        int leftPanelWidth =
                Math.min(
                        125,
                        Math.max(
                                90,
                                width / 5
                        )
                );

        int rightPanelWidth =
                Math.min(
                        150,
                        Math.max(
                                105,
                                width / 5
                        )
                );

        int centerLeft =
                left
                        + leftPanelWidth
                        + gap;

        int centerRight =
                right
                        - rightPanelWidth
                        - gap;

        drawPanel(
                graphics,
                left,
                top,
                left + leftPanelWidth,
                bottom
        );

        drawPanel(
                graphics,
                centerLeft,
                top,
                centerRight,
                bottom
        );

        drawPanel(
                graphics,
                centerRight + gap,
                top,
                right,
                bottom
        );

        graphics.drawCenteredString(
                font,
                "Production Routes",
                left
                        + leftPanelWidth / 2,
                top + 10,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Process Diagram",
                centerLeft
                        + (
                        centerRight
                                - centerLeft
                ) / 2,
                top + 10,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Selected Item / Machine",
                centerRight
                        + gap
                        + rightPanelWidth / 2,
                top + 10,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Production routes",
                left
                        + leftPanelWidth / 2,
                top + 34,
                0xAAAAAA
        );

        graphics.drawCenteredString(
                font,
                "will appear here.",
                left
                        + leftPanelWidth / 2,
                top + 49,
                0x888888
        );

        graphics.drawCenteredString(
                font,
                "Actual item and machine icons",
                centerLeft
                        + (
                        centerRight
                                - centerLeft
                ) / 2,
                top + 45,
                0xAAAAAA
        );

        graphics.drawCenteredString(
                font,
                "will form the production flow.",
                centerLeft
                        + (
                        centerRight
                                - centerLeft
                ) / 2,
                top + 60,
                0x888888
        );

        graphics.drawCenteredString(
                font,
                "Click a diagram node",
                centerRight
                        + gap
                        + rightPanelWidth / 2,
                top + 45,
                0xAAAAAA
        );

        graphics.drawCenteredString(
                font,
                "to view details.",
                centerRight
                        + gap
                        + rightPanelWidth / 2,
                top + 60,
                0x888888
        );

        renderProcessSummaryBar(
                graphics
        );
    }

    /*
     * ---------------------------------------------------------
     * Setup placeholder
     * ---------------------------------------------------------
     */
    private void renderSetupPlaceholder(
            GuiGraphics graphics
    ) {
        int left =
                CONTENT_SIDE_MARGIN;

        int right =
                width
                        - CONTENT_SIDE_MARGIN;

        int top =
                CONTENT_VIEWPORT_TOP;

        int bottom =
                getViewportBottom();

        drawPanel(
                graphics,
                left,
                top,
                right,
                bottom
        );

        graphics.drawCenteredString(
                font,
                "Setup",
                width / 2,
                top + 12,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Required machines and supporting infrastructure",
                width / 2,
                top + 42,
                0xAAAAAA
        );

        graphics.drawCenteredString(
                font,
                "will be shown here.",
                width / 2,
                top + 57,
                0x888888
        );

        renderProcessSummaryBar(
                graphics
        );
    }

    /*
     * Bottom information bar used by Process Diagram and Setup.
     */
    private void renderProcessSummaryBar(
            GuiGraphics graphics
    ) {
        int left =
                CONTENT_SIDE_MARGIN;

        int right =
                width
                        - CONTENT_SIDE_MARGIN;

        int top =
                height - 55;

        int bottom =
                height - 20;

        drawPanel(
                graphics,
                left,
                top,
                right,
                bottom
        );

        int sectionWidth =
                (right - left)
                        / 4;

        /*
         * Vertical separators.
         */
        for (int i = 1;
             i < 4;
             i++) {

            int separatorX =
                    left
                            + sectionWidth
                            * i;

            graphics.fill(
                    separatorX,
                    top + 4,
                    separatorX + 1,
                    bottom - 4,
                    0xFF3A3A3A
            );
        }

        graphics.drawCenteredString(
                font,
                "Materials",
                left
                        + sectionWidth / 2,
                top + 13,
                0xCCCCCC
        );

        graphics.drawCenteredString(
                font,
                "Machines",
                left
                        + sectionWidth
                        + sectionWidth / 2,
                top + 13,
                0xCCCCCC
        );

        graphics.drawCenteredString(
                font,
                "Fluids / Chemicals",
                left
                        + sectionWidth * 2
                        + sectionWidth / 2,
                top + 13,
                0xCCCCCC
        );

        graphics.drawCenteredString(
                font,
                "Output",
                left
                        + sectionWidth * 3
                        + sectionWidth / 2,
                top + 13,
                0xCCCCCC
        );
    }

    /*
     * Shared dark CraftScope panel.
     *
     * This is the beginning of the visual language from the
     * UI concept we selected.
     */
    private void drawPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        graphics.fill(
                left,
                top,
                right,
                bottom,
                0xE0181818
        );

        graphics.fill(
                left,
                top,
                right,
                top + 1,
                0xFF555555
        );

        graphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                0xFF333333
        );

        graphics.fill(
                left,
                top,
                left + 1,
                bottom,
                0xFF555555
        );

        graphics.fill(
                right - 1,
                top,
                right,
                bottom,
                0xFF333333
        );
    }

    private void renderTargetSlot(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int left =
                targetSlotX;

        int top =
                targetSlotY;

        int right =
                left
                        + TARGET_SLOT_SIZE;

        int bottom =
                top
                        + TARGET_SLOT_SIZE;

        graphics.fill(
                left,
                top,
                right,
                bottom,
                0xFF202020
        );

        graphics.fill(
                left,
                top,
                right,
                top + 1,
                0xFFAAAAAA
        );

        graphics.fill(
                left,
                bottom - 1,
                right,
                bottom,
                0xFF555555
        );

        graphics.fill(
                left,
                top,
                left + 1,
                bottom,
                0xFFAAAAAA
        );

        graphics.fill(
                right - 1,
                top,
                right,
                bottom,
                0xFF555555
        );

        ItemStack targetStack =
                getTargetStack();

        if (!targetStack.isEmpty()) {

            graphics.renderItem(
                    targetStack,
                    left + 4,
                    top + 4
            );

            if (mouseX >= left
                    && mouseX < right
                    && mouseY >= top
                    && mouseY < bottom) {

                graphics.renderTooltip(
                        font,
                        targetStack,
                        mouseX,
                        mouseY
                );
            }

            graphics.drawCenteredString(
                    font,
                    targetStack.getHoverName(),
                    width / 2,
                    145,
                    0xFFFFFF
            );
        }
    }

    private void renderRecipeTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        graphics.drawCenteredString(
                font,
                "Recipe Tree",
                width / 2,
                CONTENT_TITLE_Y,
                0xFFFFFF
        );

        if (currentTree == null
                || currentTree.getRoot() == null) {

            graphics.drawCenteredString(
                    font,
                    "Select a target item to build a recipe tree.",
                    width / 2,
                    CONTENT_VIEWPORT_TOP + 10,
                    0x888888
            );

            return;
        }

        int viewportTop =
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        int viewportHeight =
                viewportBottom
                        - viewportTop;

        int treeLeft =
                Math.max(
                        CONTENT_SIDE_MARGIN,
                        width / 2 - 140
                );

        graphics.enableScissor(
                CONTENT_SIDE_MARGIN,
                viewportTop,
                width - CONTENT_SIDE_MARGIN,
                viewportBottom
        );

        int rowY =
                viewportTop
                        - (int) treeScroll;

        renderRecipeNode(
                graphics,
                currentTree.getRoot(),
                "root",
                0,
                treeLeft,
                rowY,
                mouseX,
                mouseY,
                viewportTop,
                viewportBottom
        );

        graphics.disableScissor();

        int contentHeight =
                getVisibleNodeCount()
                        * CONTENT_ROW_HEIGHT;

        renderScrollbar(
                graphics,
                viewportTop,
                viewportBottom,
                viewportHeight,
                contentHeight,
                treeScroll
        );
    }

    private int renderRecipeNode(
            GuiGraphics graphics,
            CraftScopeRecipeNode node,
            String nodePath,
            int depth,
            int treeLeft,
            int rowY,
            int mouseX,
            int mouseY,
            int viewportTop,
            int viewportBottom
    ) {
        boolean hasChildren =
                !node.getChildren().isEmpty();

        boolean expanded =
                expandedNodes.contains(
                        nodePath
                );

        int indent =
                depth
                        * TREE_INDENT;

        int arrowX =
                treeLeft
                        + indent;

        int iconX =
                arrowX
                        + 12;

        if (rowY + CONTENT_ROW_HEIGHT >= viewportTop
                && rowY <= viewportBottom) {

            if (hasChildren) {

                graphics.drawString(
                        font,
                        expanded
                                ? "▼"
                                : "▶",
                        arrowX,
                        rowY + 4,
                        0xAAAAAA
                );
            }

            ItemStack displayStack =
                    getDisplayStack(
                            node
                    );

            graphics.renderItem(
                    displayStack,
                    iconX,
                    rowY
            );

            String text =
                    getNodeDisplayName(
                            node
                    )
                            + " x"
                            + node.getRequiredCount();

            graphics.drawString(
                    font,
                    text,
                    iconX + 20,
                    rowY + 4,
                    node.isCraftable()
                            ? 0xFFFFFF
                            : 0xCCCCCC
            );

            renderRecipeSelector(
                    graphics,
                    node,
                    nodePath,
                    iconX,
                    rowY,
                    text
            );

            if (mouseX >= iconX
                    && mouseX < iconX + 16
                    && mouseY >= rowY
                    && mouseY < rowY + 16
                    && mouseY >= viewportTop
                    && mouseY < viewportBottom) {

                graphics.renderTooltip(
                        font,
                        displayStack,
                        mouseX,
                        mouseY
                );
            }
        }

        int nextY =
                rowY
                        + CONTENT_ROW_HEIGHT;

        if (expanded) {

            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0;
                 i < children.size();
                 i++) {

                CraftScopeRecipeNode child =
                        children.get(i);

                String childPath =
                        buildChildPath(
                                nodePath,
                                i,
                                child
                        );

                nextY =
                        renderRecipeNode(
                                graphics,
                                child,
                                childPath,
                                depth + 1,
                                treeLeft,
                                nextY,
                                mouseX,
                                mouseY,
                                viewportTop,
                                viewportBottom
                        );
            }
        }

        return nextY;
    }

    private void renderRecipeSelector(
            GuiGraphics graphics,
            CraftScopeRecipeNode node,
            String nodePath,
            int iconX,
            int rowY,
            String itemText
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(
                        nodePath
                );

        if (choices == null
                || choices.size() <= 1
                || node.getPreferredRecipeId() == null) {

            return;
        }

        int currentIndex =
                getCurrentRecipeIndex(
                        nodePath,
                        node
                );

        String selectorText =
                "["
                        + (currentIndex + 1)
                        + "/"
                        + choices.size()
                        + "]";

        int desiredX =
                iconX
                        + 20
                        + font.width(
                        itemText
                )
                        + 8;

        int maxX =
                width
                        - CONTENT_SIDE_MARGIN
                        - font.width(
                        selectorText
                )
                        - 8;

        int selectorX =
                Math.min(
                        desiredX,
                        maxX
                );

        graphics.drawString(
                font,
                selectorText,
                selectorX,
                rowY + 4,
                0x55FFFF
        );
    }

    private int getCurrentRecipeIndex(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(
                        nodePath
                );

        if (choices == null
                || choices.isEmpty()) {

            return 0;
        }

        ResourceLocation selected =
                node.getPreferredRecipeId();

        for (int i = 0;
             i < choices.size();
             i++) {

            if (choices.get(i)
                    .equals(
                            selected
                    )) {

                return i;
            }
        }

        return 0;
    }

    private void renderTotalMaterials(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        graphics.drawCenteredString(
                font,
                "Total Materials",
                width / 2,
                CONTENT_TITLE_Y,
                0xFFFFFF
        );

        if (currentMaterialSummary == null
                || currentMaterialSummary.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "No materials to display.",
                    width / 2,
                    CONTENT_VIEWPORT_TOP + 10,
                    0x888888
            );

            return;
        }

        int viewportTop =
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        int viewportHeight =
                viewportBottom
                        - viewportTop;

        int listLeft =
                Math.max(
                        CONTENT_SIDE_MARGIN,
                        width / 2 - 120
                );

        graphics.enableScissor(
                CONTENT_SIDE_MARGIN,
                viewportTop,
                width - CONTENT_SIDE_MARGIN,
                viewportBottom
        );

        int rowY =
                viewportTop
                        - (int) materialScroll;

        for (CraftScopeMaterialSummary.Entry entry :
                currentMaterialSummary.getEntries()) {

            if (rowY + CONTENT_ROW_HEIGHT >= viewportTop
                    && rowY <= viewportBottom) {

                renderMaterialEntry(
                        graphics,
                        entry,
                        listLeft,
                        rowY,
                        mouseX,
                        mouseY,
                        viewportTop,
                        viewportBottom
                );
            }

            rowY +=
                    CONTENT_ROW_HEIGHT;
        }

        graphics.disableScissor();

        int contentHeight =
                currentMaterialSummary.size()
                        * CONTENT_ROW_HEIGHT;

        renderScrollbar(
                graphics,
                viewportTop,
                viewportBottom,
                viewportHeight,
                contentHeight,
                materialScroll
        );
    }

    private void renderMaterialEntry(
            GuiGraphics graphics,
            CraftScopeMaterialSummary.Entry entry,
            int listLeft,
            int rowY,
            int mouseX,
            int mouseY,
            int viewportTop,
            int viewportBottom
    ) {
        ItemStack displayStack =
                getMaterialDisplayStack(
                        entry
                );

        graphics.renderItem(
                displayStack,
                listLeft,
                rowY
        );

        String materialName =
                getMaterialDisplayName(
                        entry
                );

        graphics.drawString(
                font,
                materialName,
                listLeft + 20,
                rowY + 4,
                0xFFFFFF
        );

        String quantityText =
                "x"
                        + entry.getRequiredCount();

        int quantityX =
                Math.max(
                        listLeft + 120,
                        width / 2 + 75
                );

        graphics.drawString(
                font,
                quantityText,
                quantityX,
                rowY + 4,
                0xFFFFFF
        );

        if (mouseX >= listLeft
                && mouseX < listLeft + 16
                && mouseY >= rowY
                && mouseY < rowY + 16
                && mouseY >= viewportTop
                && mouseY < viewportBottom) {

            graphics.renderTooltip(
                    font,
                    displayStack,
                    mouseX,
                    mouseY
            );
        }
    }

    private ItemStack getDisplayStack(
            CraftScopeRecipeNode node
    ) {
        return getCyclingStack(
                node.getAcceptedVariants(),
                node.getStack()
        );
    }

    private ItemStack getMaterialDisplayStack(
            CraftScopeMaterialSummary.Entry entry
    ) {
        return getCyclingStack(
                entry.getAcceptedVariants(),
                entry.getStack()
        );
    }

    private ItemStack getCyclingStack(
            List<ItemStack> variants,
            ItemStack fallback
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return fallback.copy();
        }

        if (variants.size() == 1) {

            return variants
                    .getFirst()
                    .copy();
        }

        long cycle =
                System.currentTimeMillis()
                        / VARIANT_CYCLE_MS;

        int index =
                (int) (
                        cycle
                                % variants.size()
                );

        return variants
                .get(index)
                .copy();
    }

    private String getNodeDisplayName(
            CraftScopeRecipeNode node
    ) {
        return getVariantDisplayName(
                node.getAcceptedVariants(),
                node.getStack()
        );
    }

    private String getMaterialDisplayName(
            CraftScopeMaterialSummary.Entry entry
    ) {
        return getVariantDisplayName(
                entry.getAcceptedVariants(),
                entry.getStack()
        );
    }

    private String getVariantDisplayName(
            List<ItemStack> variants,
            ItemStack fallback
    ) {
        if (variants == null
                || variants.size() <= 1) {

            return fallback
                    .getHoverName()
                    .getString();
        }

        String genericName =
                findGenericVariantName(
                        variants
                );

        if (genericName != null) {

            return "Any "
                    + genericName;
        }

        return "Any Valid Ingredient";
    }

    private String findGenericVariantName(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return null;
        }

        boolean allLogs =
                true;

        boolean allPlanks =
                true;

        boolean allWool =
                true;

        for (ItemStack variant :
                variants) {

            if (!variant.is(
                    ItemTags.LOGS
            )) {

                allLogs =
                        false;
            }

            if (!variant.is(
                    ItemTags.PLANKS
            )) {

                allPlanks =
                        false;
            }

            if (!variant.is(
                    ItemTags.WOOL
            )) {

                allWool =
                        false;
            }
        }

        if (allLogs) {
            return "Log";
        }

        if (allPlanks) {
            return "Planks";
        }

        if (allWool) {
            return "Wool";
        }

        return findCommonWordSuffix(
                variants
        );
    }

    private String findCommonWordSuffix(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return null;
        }

        List<String[]> names =
                new ArrayList<>();

        for (ItemStack variant :
                variants) {

            String name =
                    variant
                            .getHoverName()
                            .getString()
                            .trim();

            if (name.isEmpty()) {
                return null;
            }

            names.add(
                    name.split(
                            "\\s+"
                    )
            );
        }

        String[] first =
                names.getFirst();

        int commonWords =
                0;

        for (int offset = 1;
             offset <= first.length;
             offset++) {

            String expected =
                    first[
                            first.length
                                    - offset
                            ];

            boolean matchesAll =
                    true;

            for (int i = 1;
                 i < names.size();
                 i++) {

                String[] words =
                        names.get(i);

                if (words.length < offset
                        || !words[
                        words.length
                                - offset
                        ].equalsIgnoreCase(
                        expected
                )) {

                    matchesAll =
                            false;

                    break;
                }
            }

            if (!matchesAll) {
                break;
            }

            commonWords++;
        }

        if (commonWords == 0) {
            return null;
        }

        StringBuilder result =
                new StringBuilder();

        int start =
                first.length
                        - commonWords;

        for (int i = start;
             i < first.length;
             i++) {

            if (!result.isEmpty()) {

                result.append(
                        " "
                );
            }

            result.append(
                    first[i]
            );
        }

        return result.toString();
    }

    private String extractLastWord(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String trimmed =
                value.trim();

        if (trimmed.isEmpty()) {
            return "";
        }

        int space =
                trimmed.lastIndexOf(
                        ' '
                );

        if (space < 0) {

            return trimmed;
        }

        return trimmed.substring(
                space + 1
        );
    }

    private void renderScrollbar(
            GuiGraphics graphics,
            int viewportTop,
            int viewportBottom,
            int viewportHeight,
            int contentHeight,
            double scroll
    ) {
        if (contentHeight <= viewportHeight) {
            return;
        }

        int barX =
                width
                        - CONTENT_SIDE_MARGIN
                        - 4;

        graphics.fill(
                barX,
                viewportTop,
                barX + 3,
                viewportBottom,
                0xFF303030
        );

        int thumbHeight =
                Math.max(
                        12,
                        viewportHeight
                                * viewportHeight
                                / contentHeight
                );

        int maxScroll =
                Math.max(
                        1,
                        contentHeight
                                - viewportHeight
                );

        int travel =
                viewportHeight
                        - thumbHeight;

        int thumbOffset =
                (int) (
                        (scroll / maxScroll)
                                * travel
                );

        graphics.fill(
                barX,
                viewportTop + thumbOffset,
                barX + 3,
                viewportTop
                        + thumbOffset
                        + thumbHeight,
                0xFFAAAAAA
        );
    }

    private int getVisibleNodeCount() {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return 0;
        }

        return countVisibleNodes(
                currentTree.getRoot(),
                "root"
        );
    }

    private int countVisibleNodes(
            CraftScopeRecipeNode node,
            String nodePath
    ) {
        int count =
                1;

        if (!expandedNodes.contains(
                nodePath
        )) {

            return count;
        }

        List<CraftScopeRecipeNode> children =
                node.getChildren();

        for (int i = 0;
             i < children.size();
             i++) {

            CraftScopeRecipeNode child =
                    children.get(i);

            String childPath =
                    buildChildPath(
                            nodePath,
                            i,
                            child
                    );

            count +=
                    countVisibleNodes(
                            child,
                            childPath
                    );
        }

        return count;
    }

    private int getViewportBottom() {
        return Math.max(
                CONTENT_VIEWPORT_TOP
                        + CONTENT_ROW_HEIGHT,
                height
                        - CONTENT_BOTTOM_MARGIN
        );
    }

    private int getViewportHeight() {
        return getViewportBottom()
                - CONTENT_VIEWPORT_TOP;
    }

    private void clampTreeScroll() {
        int contentHeight =
                getVisibleNodeCount()
                        * CONTENT_ROW_HEIGHT;

        int maxScroll =
                Math.max(
                        0,
                        contentHeight
                                - getViewportHeight()
                );

        treeScroll =
                Math.max(
                        0,
                        Math.min(
                                treeScroll,
                                maxScroll
                        )
                );
    }

    private void clampMaterialScroll() {
        int contentHeight =
                currentMaterialSummary == null
                        ? 0
                        : currentMaterialSummary.size()
                        * CONTENT_ROW_HEIGHT;

        int maxScroll =
                Math.max(
                        0,
                        contentHeight
                                - getViewportHeight()
                );

        materialScroll =
                Math.max(
                        0,
                        Math.min(
                                materialScroll,
                                maxScroll
                        )
                );
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (activeView
                == ViewMode.RECIPE_TREE
                && button == 0
                && handleTreeClick(
                mouseX,
                mouseY
        )) {

            return true;
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }

    private boolean handleTreeClick(
            double mouseX,
            double mouseY
    ) {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return false;
        }

        int viewportTop =
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        if (mouseY < viewportTop
                || mouseY >= viewportBottom) {

            return false;
        }

        int treeLeft =
                Math.max(
                        CONTENT_SIDE_MARGIN,
                        width / 2 - 140
                );

        int rowY =
                viewportTop
                        - (int) treeScroll;

        return handleNodeClick(
                currentTree.getRoot(),
                "root",
                0,
                treeLeft,
                rowY,
                mouseX,
                mouseY
        ).handled();
    }

    private ClickResult handleNodeClick(
            CraftScopeRecipeNode node,
            String nodePath,
            int depth,
            int treeLeft,
            int rowY,
            double mouseX,
            double mouseY
    ) {
        int indent =
                depth
                        * TREE_INDENT;

        int arrowX =
                treeLeft
                        + indent;

        int iconX =
                arrowX
                        + 12;

        String itemText =
                getNodeDisplayName(
                        node
                )
                        + " x"
                        + node.getRequiredCount();

        if (isRecipeSelectorClicked(
                node,
                nodePath,
                iconX,
                rowY,
                itemText,
                mouseX,
                mouseY
        )) {

            cycleRecipe(
                    nodePath,
                    node
            );

            return new ClickResult(
                    true,
                    rowY
                            + CONTENT_ROW_HEIGHT
            );
        }

        boolean hasChildren =
                !node.getChildren().isEmpty();

        if (hasChildren
                && mouseY >= rowY
                && mouseY < rowY
                + CONTENT_ROW_HEIGHT
                && mouseX >= arrowX - 2
                && mouseX < arrowX + 28) {

            if (expandedNodes.contains(
                    nodePath
            )) {

                expandedNodes.remove(
                        nodePath
                );

            } else {

                expandedNodes.add(
                        nodePath
                );
            }

            clampTreeScroll();

            return new ClickResult(
                    true,
                    rowY
                            + CONTENT_ROW_HEIGHT
            );
        }

        int nextY =
                rowY
                        + CONTENT_ROW_HEIGHT;

        if (expandedNodes.contains(
                nodePath
        )) {

            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0;
                 i < children.size();
                 i++) {

                CraftScopeRecipeNode child =
                        children.get(i);

                String childPath =
                        buildChildPath(
                                nodePath,
                                i,
                                child
                        );

                ClickResult result =
                        handleNodeClick(
                                child,
                                childPath,
                                depth + 1,
                                treeLeft,
                                nextY,
                                mouseX,
                                mouseY
                        );

                if (result.handled()) {

                    return result;
                }

                nextY =
                        result.nextY();
            }
        }

        return new ClickResult(
                false,
                nextY
        );
    }

    private boolean isRecipeSelectorClicked(
            CraftScopeRecipeNode node,
            String nodePath,
            int iconX,
            int rowY,
            String itemText,
            double mouseX,
            double mouseY
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(
                        nodePath
                );

        if (choices == null
                || choices.size() <= 1) {

            return false;
        }

        int currentIndex =
                getCurrentRecipeIndex(
                        nodePath,
                        node
                );

        String selectorText =
                "["
                        + (currentIndex + 1)
                        + "/"
                        + choices.size()
                        + "]";

        int desiredX =
                iconX
                        + 20
                        + font.width(
                        itemText
                )
                        + 8;

        int maxX =
                width
                        - CONTENT_SIDE_MARGIN
                        - font.width(
                        selectorText
                )
                        - 8;

        int selectorX =
                Math.min(
                        desiredX,
                        maxX
                );

        return mouseX >= selectorX - 2
                && mouseX
                < selectorX
                + font.width(
                selectorText
        )
                + 2
                && mouseY >= rowY
                && mouseY
                < rowY
                + CONTENT_ROW_HEIGHT;
    }

    private void cycleRecipe(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(
                        nodePath
                );

        if (choices == null
                || choices.size() <= 1) {

            return;
        }

        int currentIndex =
                getCurrentRecipeIndex(
                        nodePath,
                        node
                );

        int nextIndex =
                (currentIndex + 1)
                        % choices.size();

        ResourceLocation nextRecipe =
                choices.get(
                        nextIndex
                );

        if (nextIndex == 0) {

            recipeOverrides.remove(
                    nodePath
            );

            project.removeRecipeOverride(
                    nodePath
            );

        } else {

            recipeOverrides.put(
                    nodePath,
                    nextRecipe
            );

            project.setRecipeOverride(
                    nodePath,
                    nextRecipe.toString()
            );
        }

        clearDescendantRecipeState(
                nodePath
        );

        CraftScopeProjectManager.save();

        rebuildTree();
    }

    private void clearDescendantRecipeState(
            String nodePath
    ) {
        String prefix =
                nodePath
                        + "/";

        recipeOverrides
                .keySet()
                .removeIf(
                        key ->
                                key.startsWith(
                                        prefix
                                )
                );

        recipeChoices
                .keySet()
                .removeIf(
                        key ->
                                key.startsWith(
                                        prefix
                                )
                );

        expandedNodes
                .removeIf(
                        key ->
                                key.startsWith(
                                        prefix
                                )
                );

        List<String> savedDescendants =
                new ArrayList<>();

        for (String key :
                project
                        .getRecipeOverrides()
                        .keySet()) {

            if (key.startsWith(
                    prefix
            )) {

                savedDescendants.add(
                        key
                );
            }
        }

        for (String key :
                savedDescendants) {

            project.removeRecipeOverride(
                    key
            );
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        int viewportTop =
                CONTENT_VIEWPORT_TOP;

        int viewportBottom =
                getViewportBottom();

        if (mouseY >= viewportTop
                && mouseY < viewportBottom) {

            if (activeView
                    == ViewMode.RECIPE_TREE) {

                treeScroll -=
                        scrollY
                                * SCROLL_AMOUNT;

                clampTreeScroll();

                return true;
            }

            if (activeView
                    == ViewMode.TOTAL_MATERIALS) {

                materialScroll -=
                        scrollY
                                * SCROLL_AMOUNT;

                clampMaterialScroll();

                return true;
            }
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    private ItemStack getTargetStack() {
        String itemId =
                project.getTargetItemId();

        if (itemId == null
                || itemId.isEmpty()) {

            return ItemStack.EMPTY;
        }

        ResourceLocation location =
                ResourceLocation.tryParse(
                        itemId
                );

        if (location == null) {

            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        location
                );

        if (item == null) {

            return ItemStack.EMPTY;
        }

        return new ItemStack(
                item
        );
    }

    private String buildChildPath(
            String parentPath,
            int childIndex,
            CraftScopeRecipeNode child
    ) {
        return parentPath
                + "/"
                + childIndex
                + ":"
                + getItemId(
                child.getStack()
        );
    }

    private String getItemId(
            ItemStack stack
    ) {
        return BuiltInRegistries.ITEM
                .getKey(
                        stack.getItem()
                )
                .toString();
    }

    @Override
    public void craftscope$setTargetItem(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            return;
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        project.setTargetItemId(
                itemId.toString()
        );

        /*
         * A different target has a different recipe tree, so
         * recipe overrides from the previous target cannot be
         * reused safely.
         */
        project.clearRecipeOverrides();

        expandedNodes.clear();
        recipeOverrides.clear();
        recipeChoices.clear();

        treeScroll =
                0;

        materialScroll =
                0;

        CraftScopeProjectManager.save();

        rebuildTree();
    }

    @Override
    public int craftscope$getTargetSlotX() {
        return targetSlotX;
    }

    @Override
    public int craftscope$getTargetSlotY() {
        return targetSlotY;
    }

    @Override
    public int craftscope$getTargetSlotWidth() {
        return TARGET_SLOT_SIZE;
    }

    @Override
    public int craftscope$getTargetSlotHeight() {
        return TARGET_SLOT_SIZE;
    }

    public boolean craftscope$isRecipeTreeView() {
        return activeView
                == ViewMode.RECIPE_TREE;
        }

    @Override
    public void onClose() {
        minecraft.setScreen(
                parent
        );
    }

    private enum ViewMode {

        RECIPE_TREE,

        TOTAL_MATERIALS,

        PROCESS_DIAGRAM,

        SETUP
    }

    private record ClickResult(
            boolean handled,
            int nextY
    ) {
    }
}