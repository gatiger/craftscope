package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeTargetItemReceiver;
import io.github.gatiger.craftscope.integration.CraftScopeRecipeViewer;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummary;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummarizer;
import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteQuery;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeProductionSummary;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.production.CraftScopeResourceKind;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeProductionRecipeTreeBuilder;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeUiTheme;
import io.github.gatiger.craftscope.ui.diagram.CraftScopeProcessDiagramRenderer;
import net.minecraft.client.gui.GuiGraphics;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftScopeProjectScreen
        extends CraftScopeBaseScreen
        implements CraftScopeTargetItemReceiver {

    private static final int WINDOW_MARGIN = 8;
    private static final int JEI_RESERVED_WIDTH = 170;
    private static final int HEADER_HEIGHT = 30;
    private static final int TARGET_ROW_HEIGHT = 34;
    private static final int TAB_BAR_HEIGHT = 26;
    private static final int TARGET_SLOT_SIZE = 24;
    private static final int CONTENT_SIDE_MARGIN = 10;
    private static final int CONTENT_ROW_HEIGHT = 20;
    private static final int TREE_INDENT = 18;
    private static final int VIEW_BUTTON_HEIGHT = 20;
    private static final int VIEW_BUTTON_GAP = 3;
    private static final int VIEW_BUTTON_Y =
            WINDOW_MARGIN + HEADER_HEIGHT + TARGET_ROW_HEIGHT + 2;
    private static final int CONTENT_TITLE_Y =
            VIEW_BUTTON_Y + TAB_BAR_HEIGHT + 5;
    private static final int CONTENT_VIEWPORT_TOP =
            CONTENT_TITLE_Y + 19;
    private static final int CONTENT_BOTTOM_MARGIN = 10;
    private static final int SCROLL_AMOUNT = 20;
    private static final long VARIANT_CYCLE_MS = 1000L;

    private final Screen parent;
    private final CraftScopeProject project;

    private final Set<String> expandedNodes = new HashSet<>();
    private final Map<String, ResourceLocation> recipeOverrides = new HashMap<>();
    private final Map<String, List<ResourceLocation>> recipeChoices = new HashMap<>();

    /*
     * Session-only Process Diagram method selections.
     * Key: route-id | step-id
     */
    private final Map<String, Integer> selectedMethodIndices = new HashMap<>();

    private int targetSlotX;
    private int targetSlotY;

    private EditBox quantityField;

    private CraftScopeFlatButton recipeTreeButton;
    private CraftScopeFlatButton totalMaterialsButton;
    private CraftScopeFlatButton processDiagramButton;
    private CraftScopeFlatButton setupButton;

    private ViewMode activeView = ViewMode.RECIPE_TREE;

    private double treeScroll;
    private double materialScroll;

    private CraftScopeRecipeTree currentTree;
    private CraftScopeMaterialSummary currentMaterialSummary =
            new CraftScopeMaterialSummary(List.of());

    private List<CraftScopeProductionRoute> productionRoutes = List.of();
    private int selectedProductionRouteIndex = -1;
    private int selectedDiagramNodeIndex = -1;

    public CraftScopeProjectScreen(
            Screen parent,
            CraftScopeProject project
    ) {
        super(Component.literal(project.getName()));
        this.parent = parent;
        this.project = project;
        loadRecipeOverrides();
    }

    private void loadRecipeOverrides() {
        recipeOverrides.clear();

        for (Map.Entry<String, String> entry :
                project.getRecipeOverrides().entrySet()) {

            ResourceLocation recipeId =
                    ResourceLocation.tryParse(entry.getValue());

            if (recipeId != null) {
                recipeOverrides.put(entry.getKey(), recipeId);
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        int windowLeft = getWindowLeft();
        int controlRight = getRecipeSafeRight();
        int targetRowTop = WINDOW_MARGIN + HEADER_HEIGHT;

        targetSlotX = windowLeft + 70;
        targetSlotY = targetRowTop + 5;

        int plusX = controlRight - 12 - 20;
        int quantityBoxX = plusX - 3 - 42;
        int minusX = quantityBoxX - 3 - 20;
        int controlY = targetRowTop + 8;

        quantityField = new EditBox(
                font,
                quantityBoxX + 4,
                controlY + 5,
                34,
                9,
                Component.literal("Quantity")
        );

        quantityField.setBordered(false);
        quantityField.setValue(Integer.toString(project.getTargetCount()));
        quantityField.setFilter(
                value -> value.isEmpty() || value.matches("\\d+")
        );
        quantityField.setResponder(this::craftscope$quantityChanged);

        addRenderableWidget(quantityField);

        addRenderableWidget(
                new CraftScopeFlatButton(
                        minusX,
                        controlY,
                        20,
                        18,
                        Component.literal("-"),
                        () -> craftscope$changeQuantity(-1)
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        plusX,
                        controlY,
                        20,
                        18,
                        Component.literal("+"),
                        () -> craftscope$changeQuantity(1)
                )
        );

        int tabX = windowLeft + 8;

        int recipeWidth = 82;
        int materialsWidth = 96;
        int diagramWidth = 108;
        int setupWidth = 58;

        recipeTreeButton = addRenderableWidget(
                new CraftScopeFlatButton(
                        tabX,
                        VIEW_BUTTON_Y,
                        recipeWidth,
                        VIEW_BUTTON_HEIGHT,
                        Component.literal("Recipe Tree"),
                        () -> setActiveView(ViewMode.RECIPE_TREE)
                )
        );

        tabX += recipeWidth + VIEW_BUTTON_GAP;

        totalMaterialsButton = addRenderableWidget(
                new CraftScopeFlatButton(
                        tabX,
                        VIEW_BUTTON_Y,
                        materialsWidth,
                        VIEW_BUTTON_HEIGHT,
                        Component.literal("Total Materials"),
                        () -> setActiveView(ViewMode.TOTAL_MATERIALS)
                )
        );

        tabX += materialsWidth + VIEW_BUTTON_GAP;

        processDiagramButton = addRenderableWidget(
                new CraftScopeFlatButton(
                        tabX,
                        VIEW_BUTTON_Y,
                        diagramWidth,
                        VIEW_BUTTON_HEIGHT,
                        Component.literal("Process Diagram"),
                        () -> setActiveView(ViewMode.PROCESS_DIAGRAM)
                )
        );

        tabX += diagramWidth + VIEW_BUTTON_GAP;

        setupButton = addRenderableWidget(
                new CraftScopeFlatButton(
                        tabX,
                        VIEW_BUTTON_Y,
                        setupWidth,
                        VIEW_BUTTON_HEIGHT,
                        Component.literal("Setup"),
                        () -> setActiveView(ViewMode.SETUP)
                )
        );

        int headerButtonY = WINDOW_MARGIN + 6;
        int exitWidth = 38;
        int optionsWidth = 52;
        int helpWidth = 42;
        int gap = 3;

        int exitX = controlRight - 8 - exitWidth;
        int optionsX = exitX - gap - optionsWidth;
        int helpX = optionsX - gap - helpWidth;

        addRenderableWidget(
                new CraftScopeFlatButton(
                        helpX,
                        headerButtonY,
                        helpWidth,
                        18,
                        Component.literal("Help"),
                        () -> minecraft.setScreen(
                                new CraftScopeGuideScreen(this)
                        )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        optionsX,
                        headerButtonY,
                        optionsWidth,
                        18,
                        Component.literal("Options"),
                        () -> minecraft.setScreen(
                                new CraftScopeProjectOptionsScreen(
                                        this,
                                        parent,
                                        project
                                )
                        )
                )
        );

        addRenderableWidget(
                new CraftScopeFlatButton(
                        exitX,
                        headerButtonY,
                        exitWidth,
                        18,
                        Component.literal("Exit"),
                        () -> minecraft.setScreen(parent)
                )
        );

        treeScroll = 0;
        materialScroll = 0;

        updateViewButtons();
        rebuildTree();
    }

    private void setActiveView(ViewMode viewMode) {
        activeView = viewMode;

        switch (activeView) {
            case RECIPE_TREE -> clampTreeScroll();
            case TOTAL_MATERIALS -> clampMaterialScroll();
            case PROCESS_DIAGRAM, SETUP -> {
            }
        }

        updateViewButtons();
    }

    private void updateViewButtons() {
        if (recipeTreeButton != null) {
            recipeTreeButton.setSelected(activeView == ViewMode.RECIPE_TREE);
        }

        if (totalMaterialsButton != null) {
            totalMaterialsButton.setSelected(
                    activeView == ViewMode.TOTAL_MATERIALS
            );
        }

        if (processDiagramButton != null) {
            processDiagramButton.setSelected(
                    activeView == ViewMode.PROCESS_DIAGRAM
            );
        }

        if (setupButton != null) {
            setupButton.setSelected(activeView == ViewMode.SETUP);
        }
    }

    private int getWindowLeft() {
        return WINDOW_MARGIN;
    }

    private int getWindowTop() {
        return WINDOW_MARGIN;
    }

    private int getWindowBottom() {
        return height - WINDOW_MARGIN;
    }

    private int getRecipeSafeRight() {
        int fullRight = width - WINDOW_MARGIN;
        int desiredRight = fullRight - JEI_RESERVED_WIDTH;
        int minimumRight = getWindowLeft() + 420;

        return Math.min(
                fullRight,
                Math.max(minimumRight, desiredRight)
        );
    }

    private int getWindowRight() {
        if (activeView == ViewMode.RECIPE_TREE) {
            return getRecipeSafeRight();
        }

        return width - WINDOW_MARGIN;
    }

    private int getContentLeft() {
        return getWindowLeft() + CONTENT_SIDE_MARGIN;
    }

    private int getContentRight() {
        return getWindowRight() - CONTENT_SIDE_MARGIN;
    }

    private int getContentCenterX() {
        return (getContentLeft() + getContentRight()) / 2;
    }

    public int craftscope$getWindowLeft() {
        return getWindowLeft();
    }

    public int craftscope$getWindowTop() {
        return getWindowTop();
    }

    public int craftscope$getWindowRight() {
        return getWindowRight();
    }

    public int craftscope$getWindowBottom() {
        return getWindowBottom();
    }

    private void rebuildTree() {
        ItemStack target = getTargetStack();

        if (target.isEmpty()) {
            currentTree = null;
            currentMaterialSummary =
                    new CraftScopeMaterialSummary(List.of());

            productionRoutes = List.of();
            selectedProductionRouteIndex = -1;
            selectedDiagramNodeIndex = -1;

            treeScroll = 0;
            materialScroll = 0;
            return;
        }

        /*
         * Recipe Tree and Process Diagram now share the same
         * production-provider source of truth. This is what lets
         * Create recipes participate in the tree instead of
         * appearing as an unexplained leaf item.
         */
        currentTree = CraftScopeProductionRecipeTreeBuilder.resolveTree(
                target,
                project.getTargetCount(),
                recipeOverrides
        );

        currentMaterialSummary =
                CraftScopeMaterialSummarizer.summarize(currentTree);

        rebuildProductionRoutes(target);
        populateRecipeChoices();

        clampTreeScroll();
        clampMaterialScroll();
    }

    private void rebuildProductionRoutes(ItemStack target) {
        ResourceLocation previousRouteId =
                selectedProductionRouteIndex >= 0
                        && selectedProductionRouteIndex
                        < productionRoutes.size()
                        ? productionRoutes
                        .get(selectedProductionRouteIndex)
                        .id()
                        : null;

        /*
         * The selected Recipe Tree is authoritative.
         *
         * Process Diagram receives the tree so its root route and
         * every automatically expanded intermediate step use the
         * same recipes the player selected in Recipe Tree.
         */
        List<CraftScopeProductionRoute> routes =
                CraftScopeProductionRouteQuery.findRoutes(
                        target,
                        currentTree
                );

        productionRoutes =
                routes == null
                        ? List.of()
                        : List.copyOf(routes);

        if (productionRoutes.isEmpty()) {
            selectedProductionRouteIndex = -1;
            selectedDiagramNodeIndex = -1;
            return;
        }

        int preservedIndex = -1;

        if (previousRouteId != null) {
            for (int i = 0;
                 i < productionRoutes.size();
                 i++) {

                if (productionRoutes
                        .get(i)
                        .id()
                        .equals(previousRouteId)) {

                    preservedIndex = i;
                    break;
                }
            }
        }

        if (preservedIndex >= 0) {
            selectedProductionRouteIndex = preservedIndex;
        } else {
            /*
             * Expanded synchronized route sorts first when one is
             * available. This makes Process Diagram open on the
             * complete breakdown that matches Recipe Tree.
             */
            selectedProductionRouteIndex = 0;
        }

        selectedDiagramNodeIndex = -1;
    }

    private void populateRecipeChoices() {
        if (currentTree == null || currentTree.getRoot() == null) {
            return;
        }

        populateRecipeChoices(currentTree.getRoot(), "root");
    }

    private void populateRecipeChoices(
            CraftScopeRecipeNode node,
            String nodePath
    ) {
        if (node.getPreferredRecipeId() != null
                && node.getTotalRecipeCount() > 1
                && !recipeChoices.containsKey(nodePath)) {

            List<ResourceLocation> choices = new ArrayList<>();

            ResourceLocation selected = node.getPreferredRecipeId();

            String savedOverrideString =
                    project.getRecipeOverride(nodePath);

            ResourceLocation savedOverride =
                    savedOverrideString == null
                            ? null
                            : ResourceLocation.tryParse(
                            savedOverrideString
                    );

            if (savedOverride == null) {
                choices.add(selected);
                choices.addAll(node.getAlternativeRecipeIds());
            } else {
                List<ResourceLocation> allRecipes =
                        new ArrayList<>(
                                node.getAlternativeRecipeIds()
                        );

                allRecipes.add(savedOverride);

                ResourceLocation defaultRecipe = null;

                for (ResourceLocation id :
                        node.getAlternativeRecipeIds()) {

                    if (!id.equals(savedOverride)) {
                        defaultRecipe = id;
                        break;
                    }
                }

                if (defaultRecipe != null) {
                    choices.add(defaultRecipe);
                }

                for (ResourceLocation id : allRecipes) {
                    if (!choices.contains(id)) {
                        choices.add(id);
                    }
                }
            }

            recipeChoices.put(nodePath, choices);
        }

        List<CraftScopeRecipeNode> children = node.getChildren();

        for (int i = 0; i < children.size(); i++) {
            CraftScopeRecipeNode child = children.get(i);

            String childPath =
                    buildChildPath(nodePath, i, child);

            populateRecipeChoices(child, childPath);
        }
    }

    private void craftscope$changeQuantity(int amount) {
        int current = craftscope$getQuantityFromField();
        int updated = Math.max(1, current + amount);

        quantityField.setValue(Integer.toString(updated));
    }

    private int craftscope$getQuantityFromField() {
        try {
            int value =
                    Integer.parseInt(quantityField.getValue());

            return Math.max(1, value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void craftscope$quantityChanged(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        try {
            int quantity = Integer.parseInt(value);

            if (quantity < 1) {
                return;
            }

            project.setTargetCount(quantity);
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
        int windowLeft = getWindowLeft();
        int windowTop = getWindowTop();
        int windowRight = getWindowRight();
        int windowBottom = getWindowBottom();

        CraftScopeUiTheme.drawBackdrop(
                graphics,
                width,
                height
        );

        CraftScopeUiTheme.drawWindow(
                graphics,
                windowLeft,
                windowTop,
                windowRight,
                windowBottom
        );

        CraftScopeUiTheme.drawHeader(
                graphics,
                windowLeft,
                windowTop,
                windowRight,
                windowTop + HEADER_HEIGHT
        );

        renderHeader(graphics);
        renderTargetRow(graphics, mouseX, mouseY);

        graphics.fill(
                windowLeft + 1,
                VIEW_BUTTON_Y - 3,
                windowRight - 1,
                VIEW_BUTTON_Y + TAB_BAR_HEIGHT,
                CraftScopeUiTheme.TAB_BAR_BACKGROUND
        );

        graphics.fill(
                windowLeft + 1,
                VIEW_BUTTON_Y + TAB_BAR_HEIGHT - 1,
                windowRight - 1,
                VIEW_BUTTON_Y + TAB_BAR_HEIGHT,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        switch (activeView) {
            case RECIPE_TREE ->
                    renderRecipeTree(graphics, mouseX, mouseY);

            case TOTAL_MATERIALS ->
                    renderTotalMaterials(graphics, mouseX, mouseY);

            case PROCESS_DIAGRAM ->
                    renderProcessDiagram(graphics);

            case SETUP ->
                    renderSetup(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        int windowLeft = getWindowLeft();
        int windowTop = getWindowTop();

        CraftScopeUiTheme.drawPlaceholderLogo(
                graphics,
                windowLeft + 7,
                windowTop + 6,
                18
        );

        graphics.drawString(
                font,
                "CraftScope",
                windowLeft + 31,
                windowTop + 10,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        graphics.drawString(
                font,
                "Project: " + project.getName(),
                windowLeft + 105,
                windowTop + 10,
                CraftScopeUiTheme.TEXT_SECONDARY
        );
    }

    private void renderTargetRow(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int windowLeft = getWindowLeft();
        int windowRight = getWindowRight();
        int targetRowTop =
                getWindowTop() + HEADER_HEIGHT;

        graphics.fill(
                windowLeft + 1,
                targetRowTop,
                windowRight - 1,
                targetRowTop + TARGET_ROW_HEIGHT,
                CraftScopeUiTheme.PANEL_BACKGROUND_ALT
        );

        graphics.fill(
                windowLeft + 1,
                targetRowTop + TARGET_ROW_HEIGHT - 1,
                windowRight - 1,
                targetRowTop + TARGET_ROW_HEIGHT,
                CraftScopeUiTheme.BORDER_SUBTLE
        );

        graphics.drawString(
                font,
                "Target",
                windowLeft + 12,
                targetRowTop + 13,
                CraftScopeUiTheme.TEXT_MUTED
        );

        renderTargetSlot(graphics, mouseX, mouseY);

        ItemStack targetStack = getTargetStack();

        if (targetStack.isEmpty()) {
            graphics.drawString(
                    font,
                    "Drop an item here from JEI/EMI",
                    targetSlotX + TARGET_SLOT_SIZE + 8,
                    targetRowTop + 13,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        } else {
            graphics.drawString(
                    font,
                    targetStack.getHoverName().getString(),
                    targetSlotX + TARGET_SLOT_SIZE + 8,
                    targetRowTop + 13,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        int controlRight = getRecipeSafeRight();
        int plusX = controlRight - 12 - 20;
        int quantityBoxX = plusX - 3 - 42;
        int minusX = quantityBoxX - 3 - 20;
        int quantityBoxY = targetRowTop + 8;

        graphics.fill(
                quantityBoxX,
                quantityBoxY,
                quantityBoxX + 42,
                quantityBoxY + 18,
                CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                quantityBoxX,
                quantityBoxY,
                quantityBoxX + 42,
                quantityBoxY + 18,
                CraftScopeUiTheme.BORDER
        );

        int quantityLabelX =
                minusX - 8 - font.width("Quantity");

        graphics.drawString(
                font,
                "Quantity",
                quantityLabelX,
                targetRowTop + 13,
                CraftScopeUiTheme.TEXT_MUTED
        );
    }

    private void renderTargetSlot(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int left = targetSlotX;
        int top = targetSlotY;
        int right = left + TARGET_SLOT_SIZE;
        int bottom = top + TARGET_SLOT_SIZE;

        graphics.fill(
                left,
                top,
                right,
                bottom,
                CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                left,
                top,
                right,
                bottom,
                CraftScopeUiTheme.BORDER_HOVER
        );

        ItemStack targetStack = getTargetStack();

        if (targetStack.isEmpty()) {
            return;
        }

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
    }

    private void renderRecipeTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        int panelLeft = getContentLeft();
        int panelRight = getContentRight();
        int panelTop = CONTENT_TITLE_Y - 4;
        int panelBottom =
                getWindowBottom() - CONTENT_BOTTOM_MARGIN;

        drawPanel(
                graphics,
                panelLeft,
                panelTop,
                panelRight,
                panelBottom
        );

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                panelLeft + 1,
                panelTop + 1,
                panelRight - 1,
                CONTENT_VIEWPORT_TOP - 2
        );

        graphics.drawCenteredString(
                font,
                "Recipe Tree",
                getContentCenterX(),
                CONTENT_TITLE_Y + 3,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        if (currentTree == null
                || currentTree.getRoot() == null) {

            graphics.drawCenteredString(
                    font,
                    "Select a target item to build a recipe tree.",
                    getContentCenterX(),
                    CONTENT_VIEWPORT_TOP + 12,
                    CraftScopeUiTheme.TEXT_MUTED
            );
            return;
        }

        int viewportTop = CONTENT_VIEWPORT_TOP;
        int viewportBottom = getViewportBottom();
        int viewportHeight =
                viewportBottom - viewportTop;

        int treeLeft =
                Math.max(
                        getContentLeft() + 12,
                        getContentCenterX() - 140
                );

        graphics.enableScissor(
                getContentLeft() + 1,
                viewportTop,
                getContentRight() - 1,
                viewportBottom
        );

        int rowY =
                viewportTop - (int) treeScroll;

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
                expandedNodes.contains(nodePath);

        int indent = depth * TREE_INDENT;
        int arrowX = treeLeft + indent;
        int iconX = arrowX + 12;

        if (rowY + CONTENT_ROW_HEIGHT >= viewportTop
                && rowY <= viewportBottom) {

            if (hasChildren) {
                graphics.drawString(
                        font,
                        expanded ? "▼" : "▶",
                        arrowX,
                        rowY + 4,
                        CraftScopeUiTheme.TEXT_SECONDARY
                );
            }

            ItemStack displayStack =
                    getDisplayStack(node);

            graphics.renderItem(
                    displayStack,
                    iconX,
                    rowY
            );

            String text =
                    getNodeDisplayName(node)
                            + " x"
                            + node.getRequiredCount();

            graphics.drawString(
                    font,
                    text,
                    iconX + 20,
                    rowY + 4,
                    node.isCraftable()
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
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
                rowY + CONTENT_ROW_HEIGHT;

        if (expanded) {
            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0; i < children.size(); i++) {
                CraftScopeRecipeNode child =
                        children.get(i);

                String childPath =
                        buildChildPath(
                                nodePath,
                                i,
                                child
                        );

                nextY = renderRecipeNode(
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
                recipeChoices.get(nodePath);

        if (choices == null
                || choices.size() <= 1
                || node.getPreferredRecipeId() == null) {

            return;
        }

        int currentIndex =
                getCurrentRecipeIndex(nodePath, node);

        String selectorText =
                "["
                        + (currentIndex + 1)
                        + "/"
                        + choices.size()
                        + "]";

        int desiredX =
                iconX
                        + 20
                        + font.width(itemText)
                        + 8;

        int maxX =
                getContentRight()
                        - font.width(selectorText)
                        - 8;

        int selectorX =
                Math.min(desiredX, maxX);

        graphics.drawString(
                font,
                selectorText,
                selectorX,
                rowY + 4,
                0xFF55FFFF
        );
    }

    private int getCurrentRecipeIndex(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(nodePath);

        if (choices == null || choices.isEmpty()) {
            return 0;
        }

        ResourceLocation selected =
                node.getPreferredRecipeId();

        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).equals(selected)) {
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
        int panelLeft = getContentLeft();
        int panelRight = getContentRight();
        int panelTop = CONTENT_TITLE_Y - 4;
        int panelBottom =
                getWindowBottom() - CONTENT_BOTTOM_MARGIN;

        drawPanel(
                graphics,
                panelLeft,
                panelTop,
                panelRight,
                panelBottom
        );

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                panelLeft + 1,
                panelTop + 1,
                panelRight - 1,
                CONTENT_VIEWPORT_TOP - 2
        );

        graphics.drawCenteredString(
                font,
                "Total Materials",
                getContentCenterX(),
                CONTENT_TITLE_Y + 3,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        if (currentMaterialSummary == null
                || currentMaterialSummary.isEmpty()) {

            graphics.drawCenteredString(
                    font,
                    "No materials to display.",
                    getContentCenterX(),
                    CONTENT_VIEWPORT_TOP + 12,
                    CraftScopeUiTheme.TEXT_MUTED
            );
            return;
        }

        int viewportTop = CONTENT_VIEWPORT_TOP;
        int viewportBottom = getViewportBottom();
        int viewportHeight =
                viewportBottom - viewportTop;

        int listLeft =
                Math.max(
                        getContentLeft() + 12,
                        getContentCenterX() - 120
                );

        graphics.enableScissor(
                getContentLeft() + 1,
                viewportTop,
                getContentRight() - 1,
                viewportBottom
        );

        int rowY =
                viewportTop - (int) materialScroll;

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

            rowY += CONTENT_ROW_HEIGHT;
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
                getMaterialDisplayStack(entry);

        graphics.renderItem(
                displayStack,
                listLeft,
                rowY
        );

        String materialName =
                getMaterialDisplayName(entry);

        graphics.drawString(
                font,
                materialName,
                listLeft + 20,
                rowY + 4,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        String quantityText =
                "x" + entry.getRequiredCount();

        int quantityX =
                Math.max(
                        listLeft + 120,
                        getContentCenterX() + 75
                );

        quantityX =
                Math.min(
                        quantityX,
                        getContentRight()
                                - font.width(quantityText)
                                - 10
                );

        graphics.drawString(
                font,
                quantityText,
                quantityX,
                rowY + 4,
                CraftScopeUiTheme.TEXT_PRIMARY
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

    private void renderProcessDiagram(
            GuiGraphics graphics
    ) {
        ProcessLayout layout =
                getProcessLayout();

        drawPanel(
                graphics,
                layout.left(),
                layout.top(),
                layout.left() + layout.leftColumnWidth(),
                layout.routesBottom()
        );

        drawPanel(
                graphics,
                layout.left(),
                layout.resourcesTop(),
                layout.left() + layout.leftColumnWidth(),
                layout.resourcesBottom()
        );

        drawPanel(
                graphics,
                layout.left(),
                layout.legendTop(),
                layout.left() + layout.leftColumnWidth(),
                layout.mainBottom()
        );

        drawSectionTitle(
                graphics,
                layout.left(),
                layout.top(),
                layout.left() + layout.leftColumnWidth(),
                "Production Routes"
        );

        drawSectionTitle(
                graphics,
                layout.left(),
                layout.resourcesTop(),
                layout.left() + layout.leftColumnWidth(),
                "Production Resources"
        );

        drawSectionTitle(
                graphics,
                layout.left(),
                layout.legendTop(),
                layout.left() + layout.leftColumnWidth(),
                "Legend"
        );

        renderProductionRoutes(
                graphics,
                layout.left(),
                layout.top(),
                layout.left() + layout.leftColumnWidth(),
                layout.routesBottom()
        );

        graphics.drawString(
                font,
                "+ Fluids",
                layout.left() + 10,
                layout.resourcesTop() + 28,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "+ Chemicals",
                layout.left() + 10,
                layout.resourcesTop() + 43,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "+ Gases",
                layout.left() + 10,
                layout.resourcesTop() + 58,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "→ Item",
                layout.left() + 10,
                layout.legendTop() + 28,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        graphics.drawString(
                font,
                "→ Fluid",
                layout.left() + 10,
                layout.legendTop() + 43,
                0xFF4B9CFF
        );

        drawPanel(
                graphics,
                layout.centerLeft(),
                layout.top(),
                layout.centerRight(),
                layout.mainBottom()
        );

        drawSectionTitle(
                graphics,
                layout.centerLeft(),
                layout.top(),
                layout.centerRight(),
                "Process Diagram"
        );

        drawPanel(
                graphics,
                layout.detailsLeft(),
                layout.top(),
                layout.right(),
                layout.mainBottom()
        );

        drawSectionTitle(
                graphics,
                layout.detailsLeft(),
                layout.top(),
                layout.right(),
                getProcessDetailsTitle()
        );

        renderSelectedProductionRoute(
                graphics,
                layout.centerLeft(),
                layout.centerRight(),
                layout.detailsLeft(),
                layout.right(),
                layout.top(),
                layout.mainBottom()
        );

        renderProcessSummaryBar(
                graphics,
                layout.summaryTop(),
                layout.summaryHeight()
        );
    }

    private ProcessLayout getProcessLayout() {
        int left = getContentLeft();
        int right = getContentRight();
        int top = CONTENT_TITLE_Y - 4;

        int summaryHeight = 74;

        int summaryTop =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN
                        - summaryHeight;

        int mainBottom =
                summaryTop - 6;

        int availableWidth =
                right - left;

        int gap = 6;

        int leftColumnWidth =
                Math.min(
                        145,
                        Math.max(
                                110,
                                availableWidth / 6
                        )
                );

        int rightColumnWidth =
                Math.min(
                        165,
                        Math.max(
                                130,
                                availableWidth / 6
                        )
                );

        int centerLeft =
                left + leftColumnWidth + gap;

        int centerRight =
                right - rightColumnWidth - gap;

        int detailsLeft =
                centerRight + gap;

        int leftHeight =
                mainBottom - top;

        int routesBottom =
                top
                        + Math.max(
                        76,
                        leftHeight * 43 / 100
                );

        int resourcesTop =
                routesBottom + gap;

        int resourcesBottom =
                resourcesTop
                        + Math.max(
                        62,
                        leftHeight * 32 / 100
                );

        resourcesBottom =
                Math.min(
                        resourcesBottom,
                        mainBottom - 52
                );

        int legendTop =
                resourcesBottom + gap;

        return new ProcessLayout(
                left,
                right,
                top,
                mainBottom,
                summaryTop,
                summaryHeight,
                gap,
                leftColumnWidth,
                rightColumnWidth,
                centerLeft,
                centerRight,
                detailsLeft,
                routesBottom,
                resourcesTop,
                resourcesBottom,
                legendTop
        );
    }

    private void renderProductionRoutes(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int rowTop = top + 25;
        int rowHeight = 22;

        int availableHeight =
                bottom - rowTop - 4;

        int maxVisible =
                Math.max(
                        0,
                        availableHeight / rowHeight
                );

        ItemStack target =
                getTargetStack();

        if (target.isEmpty()) {
            graphics.drawString(
                    font,
                    "Select a target item.",
                    left + 8,
                    rowTop + 6,
                    CraftScopeUiTheme.TEXT_MUTED
            );
            return;
        }

        if (productionRoutes.isEmpty()) {
            graphics.drawString(
                    font,
                    "No production routes",
                    left + 8,
                    rowTop + 6,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            graphics.drawString(
                    font,
                    "were found.",
                    left + 8,
                    rowTop + 20,
                    CraftScopeUiTheme.TEXT_MUTED
            );
            return;
        }

        int visibleCount =
                Math.min(
                        productionRoutes.size(),
                        maxVisible
                );

        for (int i = 0; i < visibleCount; i++) {
            CraftScopeProductionRoute route =
                    productionRoutes.get(i);

            int rowY =
                    rowTop + i * rowHeight;

            boolean selected =
                    i == selectedProductionRouteIndex;

            if (selected) {
                graphics.fill(
                        left + 4,
                        rowY,
                        right - 4,
                        rowY + rowHeight - 2,
                        CraftScopeUiTheme.ACCENT_BACKGROUND
                );

                CraftScopeUiTheme.drawBorder(
                        graphics,
                        left + 4,
                        rowY,
                        right - 4,
                        rowY + rowHeight - 2,
                        CraftScopeUiTheme.ACCENT
                );
            }

            String label =
                    fitText(
                            getProductionRouteLabel(route),
                            right - left - 18
                    );

            graphics.drawString(
                    font,
                    label,
                    left + 9,
                    rowY + 6,
                    selected
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
            );
        }

        if (productionRoutes.size() > visibleCount) {
            int hiddenCount =
                    productionRoutes.size() - visibleCount;

            graphics.drawString(
                    font,
                    "+" + hiddenCount + " more",
                    left + 9,
                    bottom - 13,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }
    }

    private String getProcessDetailsTitle() {
        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        CraftScopeProcessDiagramRenderer.Selection selection =
                CraftScopeProcessDiagramRenderer.getSelection(
                        route,
                        project.getTargetCount(),
                        selectedDiagramNodeIndex
                );

        if (selection == null) {
            return "Selected Route";
        }

        if (selection.isResource()) {
            return "Selected Resource";
        }

        return "Selected Process";
    }

    private void renderSelectedProductionRoute(
            GuiGraphics graphics,
            int centerLeft,
            int centerRight,
            int detailsLeft,
            int detailsRight,
            int top,
            int bottom
    ) {
        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        if (route == null) {
            graphics.drawCenteredString(
                    font,
                    "Select a production route.",
                    centerLeft + (centerRight - centerLeft) / 2,
                    top + 45,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            graphics.drawCenteredString(
                    font,
                    "No route selected",
                    detailsLeft + (detailsRight - detailsLeft) / 2,
                    top + 45,
                    CraftScopeUiTheme.TEXT_MUTED
            );
            return;
        }

        CraftScopeProductionRoute displayRoute =
                getDisplayProductionRoute(route);

        CraftScopeProcessDiagramRenderer.render(
                graphics,
                font,
                displayRoute,
                centerLeft + 6,
                top + 25,
                centerRight - 6,
                bottom - 6,
                project.getTargetCount(),
                selectedDiagramNodeIndex
        );

        CraftScopeProcessDiagramRenderer.Selection selection =
                CraftScopeProcessDiagramRenderer.getSelection(
                        route,
                        project.getTargetCount(),
                        selectedDiagramNodeIndex
                );

        if (selection == null) {
            renderRouteDetails(
                    graphics,
                    route,
                    detailsLeft,
                    detailsRight,
                    top,
                    bottom
            );
            return;
        }

        if (selection.isResource()) {
            renderResourceSelectionDetails(
                    graphics,
                    selection,
                    detailsLeft,
                    detailsRight,
                    top,
                    bottom
            );
        } else {
            renderProcessSelectionDetails(
                    graphics,
                    selection,
                    detailsLeft,
                    detailsRight,
                    top,
                    bottom
            );
        }
    }

    private void renderRouteDetails(
            GuiGraphics graphics,
            CraftScopeProductionRoute route,
            int detailsLeft,
            int detailsRight,
            int top,
            int bottom
    ) {
        int textX = detailsLeft + 9;
        int y = top + 31;

        graphics.drawString(
                font,
                "Source",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y += 14;

        graphics.drawString(
                font,
                fitText(
                        route.sourceModName().getString(),
                        detailsRight - textX - 8
                ),
                textX,
                y,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        y += 22;

        graphics.drawString(
                font,
                "Route",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y += 14;

        graphics.drawString(
                font,
                fitText(
                        route.displayName().getString(),
                        detailsRight - textX - 8
                ),
                textX,
                y,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        y += 22;

        graphics.drawString(
                font,
                "Steps: " + route.getStepCount(),
                textX,
                y,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        y += 22;

        if (y <= bottom - 28) {
            graphics.drawString(
                    font,
                    "Click an item or process",
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            y += 14;

            graphics.drawString(
                    font,
                    "in the diagram for details.",
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }
    }

    private void renderResourceSelectionDetails(
            GuiGraphics graphics,
            CraftScopeProcessDiagramRenderer.Selection selection,
            int detailsLeft,
            int detailsRight,
            int top,
            int bottom
    ) {
        CraftScopeResourceAmount resource =
                selection.resource();

        int centerX =
                (detailsLeft + detailsRight) / 2;

        ItemStack displayStack =
                CraftScopeProcessDiagramRenderer
                        .getSelectionDisplayStack(selection);

        if (!displayStack.isEmpty()) {
            renderDetailItem(
                    graphics,
                    displayStack,
                    centerX,
                    top + 31
            );
        }

        String displayName =
                CraftScopeProcessDiagramRenderer
                        .getSelectionDisplayName(selection);

        graphics.drawCenteredString(
                font,
                fitText(
                        displayName,
                        detailsRight - detailsLeft - 18
                ),
                centerX,
                top + 67,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        int textX = detailsLeft + 9;
        int y = top + 88;

        graphics.drawString(
                font,
                "Required",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y += 14;

        String amountText =
                resource.hasUnit()
                        ? selection.amount()
                        + " "
                        + resource.unit()
                        : "x" + selection.amount();

        graphics.drawString(
                font,
                amountText,
                textX,
                y,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        y += 22;

        graphics.drawString(
                font,
                "Type",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y += 14;

        graphics.drawString(
                font,
                formatResourceKind(resource),
                textX,
                y,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        y += 22;

        graphics.drawString(
                font,
                "Consumed: "
                        + (resource.consumed()
                        ? "Yes"
                        : "No"),
                textX,
                y,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        y += 18;

        if (resource.hasVariants()
                && y <= bottom - 14) {

            graphics.drawString(
                    font,
                    "Variants: "
                            + resource
                            .acceptedVariantIds()
                            .size(),
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            y += 15;

            int shown = 0;

            for (ResourceLocation variant :
                    resource.acceptedVariantIds()) {

                if (shown >= 3 || y > bottom - 14) {
                    break;
                }

                graphics.drawString(
                        font,
                        fitText(
                                "• " + variant.getPath(),
                                detailsRight - textX - 8
                        ),
                        textX,
                        y,
                        CraftScopeUiTheme.TEXT_MUTED
                );

                y += 13;
                shown++;
            }
        }
    }

    private void renderProcessSelectionDetails(
            GuiGraphics graphics,
            CraftScopeProcessDiagramRenderer.Selection selection,
            int detailsLeft,
            int detailsRight,
            int top,
            int bottom
    ) {
        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        CraftScopeProductionStep step =
                selection.step();

        if (route == null || step == null) {
            return;
        }

        int centerX =
                (detailsLeft + detailsRight) / 2;

        CraftScopeProductionMethod selectedMethod =
                getSelectedMethod(route, step);

        boolean hasViewRecipe =
                selectedMethod != null
                        && selectedMethod.hasRecipes();

        int contentBottom =
                hasViewRecipe
                        ? bottom - 34
                        : bottom;

        CraftScopeProductionStep displayStep = step;

        if (selectedMethod != null) {
            displayStep = new CraftScopeProductionStep(
                    step.id(),
                    step.displayName(),
                    step.inputs(),
                    step.outputs(),
                    List.of(selectedMethod)
            );
        }

        CraftScopeProcessDiagramRenderer.Selection displaySelection =
                new CraftScopeProcessDiagramRenderer.Selection(
                        selection.nodeIndex(),
                        selection.kind(),
                        selection.resource(),
                        displayStep,
                        selection.amount(),
                        selection.extraCount()
                );

        ItemStack displayStack =
                CraftScopeProcessDiagramRenderer
                        .getSelectionDisplayStack(displaySelection);

        if (!displayStack.isEmpty()) {
            renderDetailItem(
                    graphics,
                    displayStack,
                    centerX,
                    top + 31
            );
        }

        graphics.drawCenteredString(
                font,
                fitText(
                        step.displayName().getString(),
                        detailsRight - detailsLeft - 18
                ),
                centerX,
                top + 67,
                CraftScopeUiTheme.TEXT_PRIMARY
        );

        int textX = detailsLeft + 9;
        int methodWidth =
                detailsRight - detailsLeft - 18;

        int y = top + 88;

        graphics.drawString(
                font,
                "Method",
                textX,
                y,
                CraftScopeUiTheme.TEXT_MUTED
        );

        y += 14;

        int methodRowHeight = 18;

        int selectedMethodIndex =
                getSelectedMethodIndex(route, step);

        for (int i = 0; i < step.methods().size(); i++) {
            if (y + methodRowHeight > contentBottom - 6) {
                break;
            }

            CraftScopeProductionMethod method =
                    step.methods().get(i);

            boolean selected =
                    i == selectedMethodIndex;

            if (selected) {
                graphics.fill(
                        textX,
                        y,
                        textX + methodWidth,
                        y + methodRowHeight,
                        CraftScopeUiTheme.ACCENT_BACKGROUND
                );

                CraftScopeUiTheme.drawBorder(
                        graphics,
                        textX,
                        y,
                        textX + methodWidth,
                        y + methodRowHeight,
                        CraftScopeUiTheme.ACCENT
                );
            }

            graphics.drawString(
                    font,
                    fitText(
                            method.displayName().getString(),
                            methodWidth - 12
                    ),
                    textX + 6,
                    y + 5,
                    selected
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
            );

            y += methodRowHeight + 3;
        }

        y += 4;

        if (selectedMethod != null
                && y <= contentBottom - 14) {

            graphics.drawString(
                    font,
                    "Machine",
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            y += 14;

            boolean foundMachine = false;

            for (CraftScopeProcessRequirement requirement :
                    selectedMethod.requirements()) {

                if (requirement.kind()
                        != CraftScopeRequirementKind.MACHINE) {

                    continue;
                }

                foundMachine = true;

                if (y > contentBottom - 14) {
                    break;
                }

                graphics.drawString(
                        font,
                        fitText(
                                "• "
                                        + requirement
                                        .displayName()
                                        .getString(),
                                detailsRight - textX - 8
                        ),
                        textX,
                        y,
                        CraftScopeUiTheme.TEXT_SECONDARY
                );

                y += 14;
            }

            if (!foundMachine
                    && y <= contentBottom - 14) {

                graphics.drawString(
                        font,
                        "None required",
                        textX,
                        y,
                        CraftScopeUiTheme.TEXT_MUTED
                );

                y += 14;
            }
        }

        y += 5;

        if (y <= contentBottom - 14) {
            graphics.drawString(
                    font,
                    "Inputs: " + step.inputs().size(),
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            y += 14;
        }

        if (y <= contentBottom - 14) {
            graphics.drawString(
                    font,
                    "Outputs: " + step.outputs().size(),
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            y += 14;
        }

        if (selectedMethod != null
                && selectedMethod.hasRecipes()
                && y <= contentBottom - 14) {

            graphics.drawString(
                    font,
                    "Recipes: "
                            + selectedMethod
                            .recipeIds()
                            .size(),
                    textX,
                    y,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );
        }

        if (hasViewRecipe) {
            renderViewRecipeButton(
                    graphics,
                    selectedMethod,
                    detailsLeft,
                    detailsRight,
                    bottom
            );
        }
    }

    private void renderViewRecipeButton(
            GuiGraphics graphics,
            CraftScopeProductionMethod method,
            int detailsLeft,
            int detailsRight,
            int bottom
    ) {
        if (method == null || !method.hasRecipes()) {
            return;
        }

        int buttonLeft = detailsLeft + 9;
        int buttonRight = detailsRight - 9;
        int buttonTop = bottom - 27;
        int buttonBottom = buttonTop + 18;

        boolean available =
                CraftScopeRecipeViewer.isAvailable();

        graphics.fill(
                buttonLeft,
                buttonTop,
                buttonRight,
                buttonBottom,
                available
                        ? CraftScopeUiTheme.ACCENT_BACKGROUND
                        : CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                buttonLeft,
                buttonTop,
                buttonRight,
                buttonBottom,
                available
                        ? CraftScopeUiTheme.ACCENT
                        : CraftScopeUiTheme.BORDER
        );

        graphics.drawCenteredString(
                font,
                "View Recipe",
                (buttonLeft + buttonRight) / 2,
                buttonTop + 5,
                available
                        ? CraftScopeUiTheme.TEXT_PRIMARY
                        : CraftScopeUiTheme.TEXT_MUTED
        );
    }

    private String getMethodSelectionKey(
            CraftScopeProductionRoute route,
            CraftScopeProductionStep step
    ) {
        return route.id() + "|" + step.id();
    }

    private int getSelectedMethodIndex(
            CraftScopeProductionRoute route,
            CraftScopeProductionStep step
    ) {
        if (step.methods().isEmpty()) {
            return -1;
        }

        String key =
                getMethodSelectionKey(route, step);

        int selected =
                selectedMethodIndices.getOrDefault(
                        key,
                        0
                );

        if (selected < 0
                || selected >= step.methods().size()) {

            selected = 0;
        }

        return selected;
    }

    private CraftScopeProductionMethod getSelectedMethod(
            CraftScopeProductionRoute route,
            CraftScopeProductionStep step
    ) {
        int index =
                getSelectedMethodIndex(route, step);

        if (index < 0) {
            return null;
        }

        return step.methods().get(index);
    }

    private void selectMethod(
            CraftScopeProductionRoute route,
            CraftScopeProductionStep step,
            int methodIndex
    ) {
        if (methodIndex < 0
                || methodIndex >= step.methods().size()) {

            return;
        }

        selectedMethodIndices.put(
                getMethodSelectionKey(route, step),
                methodIndex
        );
    }

    private CraftScopeProductionRoute getDisplayProductionRoute(
            CraftScopeProductionRoute route
    ) {
        if (route == null) {
            return null;
        }

        List<CraftScopeProductionStep> displaySteps =
                new ArrayList<>();

        for (CraftScopeProductionStep step :
                route.steps()) {

            if (step.methods().isEmpty()) {
                displaySteps.add(step);
                continue;
            }

            CraftScopeProductionMethod selectedMethod =
                    getSelectedMethod(route, step);

            List<CraftScopeProductionMethod> displayMethods =
                    selectedMethod == null
                            ? step.methods()
                            : List.of(selectedMethod);

            displaySteps.add(
                    new CraftScopeProductionStep(
                            step.id(),
                            step.displayName(),
                            step.inputs(),
                            step.outputs(),
                            displayMethods
                    )
            );
        }

        return new CraftScopeProductionRoute(
                route.id(),
                route.sourceModId(),
                route.sourceModName(),
                route.displayName(),
                route.targetOutput(),
                displaySteps,
                route.priority()
        );
    }

    private String formatResourceKind(
            CraftScopeResourceAmount resource
    ) {
        return switch (resource.kind()) {
            case ITEM -> "Item";
            case FLUID -> "Fluid";
            case CHEMICAL -> "Chemical";
            case OTHER -> "Other";
        };
    }

    private void renderDetailItem(
            GuiGraphics graphics,
            ItemStack stack,
            int centerX,
            int y
    ) {
        graphics.pose().pushPose();

        graphics.pose().translate(
                centerX - 16.0F,
                y,
                0.0F
        );

        graphics.pose().scale(
                2.0F,
                2.0F,
                1.0F
        );

        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private CraftScopeProductionRoute getSelectedProductionRoute() {
        if (selectedProductionRouteIndex < 0
                || selectedProductionRouteIndex
                >= productionRoutes.size()) {

            return null;
        }

        return productionRoutes.get(
                selectedProductionRouteIndex
        );
    }

    private String getProductionRouteLabel(
            CraftScopeProductionRoute route
    ) {
        String source =
                route.sourceModName().getString();

        String routeName =
                route.displayName().getString();

        if (source == null || source.isBlank()) {
            return routeName;
        }

        return source + ": " + routeName;
    }

    private String fitText(
            String text,
            int maxWidth
    ) {
        if (text == null || maxWidth <= 0) {
            return "";
        }

        if (font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";

        int availableWidth =
                Math.max(
                        0,
                        maxWidth - font.width(ellipsis)
                );

        String result = text;

        while (!result.isEmpty()
                && font.width(result) > availableWidth) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result + ellipsis;
    }

    /*
     * ---------------------------------------------------------
     * Setup
     * ---------------------------------------------------------
     */

    private void renderSetup(
            GuiGraphics graphics
    ) {
        int left =
                getContentLeft();

        int right =
                getContentRight();

        int top =
                CONTENT_TITLE_Y - 4;

        int summaryHeight =
                74;

        int summaryTop =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN
                        - summaryHeight;

        int mainBottom =
                summaryTop - 6;

        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        /*
         * Keep the Setup page useful even before a route exists.
         */
        if (route == null) {

            drawPanel(
                    graphics,
                    left,
                    top,
                    right,
                    mainBottom
            );

            drawSectionTitle(
                    graphics,
                    left,
                    top,
                    right,
                    "Setup"
            );

            graphics.drawCenteredString(
                    font,
                    "Select a production route from Process Diagram.",
                    getContentCenterX(),
                    top + 46,
                    CraftScopeUiTheme.TEXT_SECONDARY
            );

            renderProcessSummaryBar(
                    graphics,
                    summaryTop,
                    summaryHeight
            );

            return;
        }

        /*
         * The display route contains only the selected method for
         * each process step. This keeps Setup synchronized with
         * Smelting/Blasting and future modded method choices.
         */
        CraftScopeProductionRoute displayRoute =
                getDisplayProductionRoute(
                        route
                );

        CraftScopeProductionSummary summary =
                CraftScopeProductionSummary.summarize(
                        displayRoute,
                        project.getTargetCount()
                );

        List<CraftScopeProcessRequirement> operatingRequirements =
                getSetupOperatingRequirements(
                        displayRoute
                );

        /*
         * -----------------------------------------------------
         * Route banner
         * -----------------------------------------------------
         */

        int bannerBottom =
                top + 60;

        drawPanel(
                graphics,
                left,
                top,
                right,
                bannerBottom
        );

        drawSectionTitle(
                graphics,
                left,
                top,
                right,
                "Setup"
        );

        graphics.drawCenteredString(
                font,
                fitText(
                        getProductionRouteLabel(
                                route
                        ),
                        right - left - 30
                ),
                getContentCenterX(),
                top + 29,
                CraftScopeUiTheme.SUCCESS
        );

        String routeInfo =
                route.getStepCount()
                        + (
                        route.getStepCount() == 1
                                ? " production step"
                                : " production steps"
                )
                        + "  •  "
                        + summary.runs()
                        + (
                        summary.runs() == 1
                                ? " run"
                                : " runs"
                );

        graphics.drawCenteredString(
                font,
                fitText(
                        routeInfo,
                        right - left - 30
                ),
                getContentCenterX(),
                top + 44,
                CraftScopeUiTheme.TEXT_SECONDARY
        );

        /*
         * -----------------------------------------------------
         * Main Setup columns
         * -----------------------------------------------------
         */

        int gap =
                6;

        int panelsTop =
                bannerBottom + gap;

        int availableWidth =
                right - left - gap * 2;

        int machinesWidth =
                availableWidth / 3;

        int requirementsWidth =
                availableWidth / 3;

        int methodsWidth =
                availableWidth
                        - machinesWidth
                        - requirementsWidth;

        int machinesLeft =
                left;

        int machinesRight =
                machinesLeft + machinesWidth;

        int requirementsLeft =
                machinesRight + gap;

        int requirementsRight =
                requirementsLeft + requirementsWidth;

        int methodsLeft =
                requirementsRight + gap;

        int methodsRight =
                methodsLeft + methodsWidth;

        drawPanel(
                graphics,
                machinesLeft,
                panelsTop,
                machinesRight,
                mainBottom
        );

        drawPanel(
                graphics,
                requirementsLeft,
                panelsTop,
                requirementsRight,
                mainBottom
        );

        drawPanel(
                graphics,
                methodsLeft,
                panelsTop,
                methodsRight,
                mainBottom
        );

        drawSectionTitle(
                graphics,
                machinesLeft,
                panelsTop,
                machinesRight,
                "Required Machines"
        );

        drawSectionTitle(
                graphics,
                requirementsLeft,
                panelsTop,
                requirementsRight,
                "Operating Requirements"
        );

        drawSectionTitle(
                graphics,
                methodsLeft,
                panelsTop,
                methodsRight,
                "Selected Methods"
        );

        renderSetupMachines(
                graphics,
                summary.machines(),
                machinesLeft,
                machinesRight,
                panelsTop,
                mainBottom
        );

        renderSetupOperatingRequirements(
                graphics,
                operatingRequirements,
                requirementsLeft,
                requirementsRight,
                panelsTop,
                mainBottom
        );

        renderSetupSelectedMethods(
                graphics,
                displayRoute,
                methodsLeft,
                methodsRight,
                panelsTop,
                mainBottom
        );

        /*
         * Keep the Process Diagram and Setup totals identical.
         */
        renderProcessSummaryBar(
                graphics,
                summaryTop,
                summaryHeight
        );
    }

    private void renderSetupMachines(
            GuiGraphics graphics,
            List<CraftScopeProcessRequirement> machines,
            int left,
            int right,
            int top,
            int bottom
    ) {
        if (machines == null
                || machines.isEmpty()) {

            renderSetupEmptyMessage(
                    graphics,
                    left,
                    right,
                    top,
                    "No machine required"
            );

            return;
        }

        int rowTop =
                top + 28;

        int rowHeight =
                20;

        int maxRows =
                Math.max(
                        1,
                        (bottom - rowTop - 6)
                                / rowHeight
                );

        int visible =
                Math.min(
                        machines.size(),
                        maxRows
                );

        for (int i = 0;
             i < visible;
             i++) {

            CraftScopeProcessRequirement requirement =
                    machines.get(i);

            int y =
                    rowTop
                            + i * rowHeight;

            ItemStack stack =
                    getSummaryRequirementStack(
                            requirement
                    );

            int iconX =
                    left + 9;

            int textX =
                    iconX;

            if (!stack.isEmpty()) {

                graphics.renderItem(
                        stack,
                        iconX,
                        y
                );

                textX +=
                        20;
            }

            String amountText =
                    formatSummaryRequirementAmount(
                            requirement
                    );

            String label =
                    requirement
                            .displayName()
                            .getString();

            if (!amountText.isEmpty()) {

                label +=
                        " "
                                + amountText;
            }

            graphics.drawString(
                    font,
                    fitText(
                            label,
                            right - textX - 9
                    ),
                    textX,
                    y + 4,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        renderSetupMoreCount(
                graphics,
                machines.size(),
                visible,
                right,
                bottom
        );
    }

    private void renderSetupOperatingRequirements(
            GuiGraphics graphics,
            List<CraftScopeProcessRequirement> requirements,
            int left,
            int right,
            int top,
            int bottom
    ) {
        if (requirements == null
                || requirements.isEmpty()) {

            renderSetupEmptyMessage(
                    graphics,
                    left,
                    right,
                    top,
                    "No special requirements"
            );

            return;
        }

        int rowTop =
                top + 28;

        int rowHeight =
                28;

        int maxRows =
                Math.max(
                        1,
                        (bottom - rowTop - 6)
                                / rowHeight
                );

        int visible =
                Math.min(
                        requirements.size(),
                        maxRows
                );

        for (int i = 0;
             i < visible;
             i++) {

            CraftScopeProcessRequirement requirement =
                    requirements.get(i);

            int y =
                    rowTop
                            + i * rowHeight;

            graphics.drawString(
                    font,
                    fitText(
                            formatSetupRequirementKind(
                                    requirement.kind()
                            ),
                            right - left - 18
                    ),
                    left + 9,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            String amount =
                    formatSummaryRequirementAmount(
                            requirement
                    );

            String label =
                    requirement
                            .displayName()
                            .getString();

            if (!amount.isEmpty()) {

                label +=
                        " "
                                + amount;
            }

            graphics.drawString(
                    font,
                    fitText(
                            label,
                            right - left - 18
                    ),
                    left + 9,
                    y + 13,
                    CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        renderSetupMoreCount(
                graphics,
                requirements.size(),
                visible,
                right,
                bottom
        );
    }

    private void renderSetupSelectedMethods(
            GuiGraphics graphics,
            CraftScopeProductionRoute route,
            int left,
            int right,
            int top,
            int bottom
    ) {
        if (route == null
                || route.steps().isEmpty()) {

            renderSetupEmptyMessage(
                    graphics,
                    left,
                    right,
                    top,
                    "No production steps"
            );

            return;
        }

        int rowTop =
                top + 28;

        int rowHeight =
                28;

        int maxRows =
                Math.max(
                        1,
                        (bottom - rowTop - 6)
                                / rowHeight
                );

        int visible =
                Math.min(
                        route.steps().size(),
                        maxRows
                );

        for (int i = 0;
             i < visible;
             i++) {

            CraftScopeProductionStep step =
                    route.steps().get(i);

            CraftScopeProductionMethod method =
                    step.getPrimaryMethod();

            int y =
                    rowTop
                            + i * rowHeight;

            graphics.drawString(
                    font,
                    fitText(
                            step.displayName()
                                    .getString(),
                            right - left - 18
                    ),
                    left + 9,
                    y,
                    CraftScopeUiTheme.TEXT_MUTED
            );

            String methodName =
                    method == null
                            ? "No method"
                            : method
                            .displayName()
                            .getString();

            graphics.drawString(
                    font,
                    fitText(
                            methodName,
                            right - left - 18
                    ),
                    left + 9,
                    y + 13,
                    method == null
                            ? CraftScopeUiTheme.TEXT_MUTED
                            : CraftScopeUiTheme.TEXT_PRIMARY
            );
        }

        renderSetupMoreCount(
                graphics,
                route.steps().size(),
                visible,
                right,
                bottom
        );
    }

    private List<CraftScopeProcessRequirement>
    getSetupOperatingRequirements(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().isEmpty()) {

            return List.of();
        }

        Map<String, CraftScopeProcessRequirement> requirements =
                new LinkedHashMap<>();

        for (CraftScopeProductionStep step :
                route.steps()) {

            CraftScopeProductionMethod method =
                    step.getPrimaryMethod();

            if (method == null) {
                continue;
            }

            for (CraftScopeProcessRequirement requirement :
                    method.requirements()) {

                if (requirement.kind()
                        == CraftScopeRequirementKind.MACHINE) {

                    continue;
                }

                String key =
                        buildSetupRequirementKey(
                                requirement
                        );

                CraftScopeProcessRequirement existing =
                        requirements.get(
                                key
                        );

                /*
                 * These requirements are normally capacities or
                 * conditions, so repeated identical requirements
                 * use the greatest requirement rather than being
                 * multiplied by the number of recipe runs.
                 */
                if (existing == null
                        || requirement.amount()
                        > existing.amount()) {

                    requirements.put(
                            key,
                            requirement
                    );
                }
            }
        }

        return List.copyOf(
                requirements.values()
        );
    }

    private String buildSetupRequirementKey(
            CraftScopeProcessRequirement requirement
    ) {
        String id =
                requirement.id() == null
                        ? ""
                        : requirement
                        .id()
                        .toString();

        return requirement.kind()
                + "|"
                + id
                + "|"
                + requirement
                .displayName()
                .getString()
                + "|"
                + requirement.unit();
    }

    private String formatSetupRequirementKind(
            CraftScopeRequirementKind kind
    ) {
        return switch (kind) {

            case MACHINE ->
                    "Machine";

            case ENERGY ->
                    "Energy";

            case HEAT ->
                    "Heat";

            case MECHANICAL_POWER ->
                    "Mechanical Power";

            case ENVIRONMENT ->
                    "Environment";

            case TOOL ->
                    "Tool / Equipment";

            case OTHER ->
                    "Other";
        };
    }

    private void renderSetupEmptyMessage(
            GuiGraphics graphics,
            int left,
            int right,
            int top,
            String text
    ) {
        graphics.drawCenteredString(
                font,
                fitText(
                        text,
                        right - left - 18
                ),
                (left + right) / 2,
                top + 38,
                CraftScopeUiTheme.TEXT_MUTED
        );
    }

    private void renderSetupMoreCount(
            GuiGraphics graphics,
            int total,
            int visible,
            int right,
            int bottom
    ) {
        if (total <= visible) {
            return;
        }

        String text =
                "+"
                        + (total - visible)
                        + " more";

        graphics.drawString(
                font,
                text,
                right
                        - font.width(text)
                        - 9,
                bottom - 12,
                CraftScopeUiTheme.TEXT_MUTED
        );
    }

    /*
     * ---------------------------------------------------------
     * Real route summary
     * ---------------------------------------------------------
     */

    private void renderProcessSummaryBar(
            GuiGraphics graphics,
            int top,
            int height
    ) {
        int left = getContentLeft();
        int right = getContentRight();
        int bottom = top + height;
        int gap = 6;

        int totalWidth =
                right - left - gap * 2;

        int machinesWidth =
                totalWidth * 36 / 100;

        int resourcesWidth =
                totalWidth * 36 / 100;

        int outputsWidth =
                totalWidth
                        - machinesWidth
                        - resourcesWidth;

        int machinesLeft = left;
        int machinesRight =
                machinesLeft + machinesWidth;

        int resourcesLeft =
                machinesRight + gap;

        int resourcesRight =
                resourcesLeft + resourcesWidth;

        int outputsLeft =
                resourcesRight + gap;

        int outputsRight =
                outputsLeft + outputsWidth;

        drawPanel(
                graphics,
                machinesLeft,
                top,
                machinesRight,
                bottom
        );

        drawPanel(
                graphics,
                resourcesLeft,
                top,
                resourcesRight,
                bottom
        );

        drawPanel(
                graphics,
                outputsLeft,
                top,
                outputsRight,
                bottom
        );

        drawSectionTitle(
                graphics,
                machinesLeft,
                top,
                machinesRight,
                "Required Machines"
        );

        drawSectionTitle(
                graphics,
                resourcesLeft,
                top,
                resourcesRight,
                "Required Resources (Total)"
        );

        drawSectionTitle(
                graphics,
                outputsLeft,
                top,
                outputsRight,
                "Outputs"
        );

        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        if (route == null) {
            renderSummaryEmptyText(
                    graphics,
                    machinesLeft,
                    machinesRight,
                    top,
                    "Select a route"
            );

            renderSummaryEmptyText(
                    graphics,
                    resourcesLeft,
                    resourcesRight,
                    top,
                    "Select a route"
            );

            renderSummaryEmptyText(
                    graphics,
                    outputsLeft,
                    outputsRight,
                    top,
                    "Select a route"
            );

            return;
        }

        CraftScopeProductionRoute displayRoute =
                getDisplayProductionRoute(route);

        CraftScopeProductionSummary summary =
                CraftScopeProductionSummary.summarize(
                        displayRoute,
                        project.getTargetCount()
                );

        renderProductionSummaryMachines(
                graphics,
                summary,
                machinesLeft,
                machinesRight,
                top,
                bottom
        );

        renderProductionSummaryResources(
                graphics,
                summary,
                resourcesLeft,
                resourcesRight,
                top,
                bottom
        );

        renderProductionSummaryOutputs(
                graphics,
                summary,
                outputsLeft,
                outputsRight,
                top,
                bottom
        );
    }

    private void renderProductionSummaryMachines(
            GuiGraphics graphics,
            CraftScopeProductionSummary summary,
            int left,
            int right,
            int top,
            int bottom
    ) {
        List<CraftScopeProcessRequirement> machines =
                summary.machines();

        if (machines.isEmpty()) {
            renderSummaryEmptyText(
                    graphics,
                    left,
                    right,
                    top,
                    "No machine required"
            );
            return;
        }

        int bodyTop = top + 27;
        int rowHeight = 18;

        int maxRows =
                Math.max(
                        1,
                        Math.min(
                                2,
                                (bottom - bodyTop - 4)
                                        / rowHeight
                        )
                );

        int visible =
                Math.min(
                        machines.size(),
                        maxRows
                );

        for (int i = 0; i < visible; i++) {
            renderSummaryRequirementRow(
                    graphics,
                    machines.get(i),
                    left,
                    right,
                    bodyTop + i * rowHeight
            );
        }

        renderSummaryMoreCount(
                graphics,
                machines.size(),
                visible,
                right,
                bottom
        );
    }

    private void renderProductionSummaryResources(
            GuiGraphics graphics,
            CraftScopeProductionSummary summary,
            int left,
            int right,
            int top,
            int bottom
    ) {
        List<CraftScopeResourceAmount> resources =
                summary.resources();

        if (resources.isEmpty()) {
            renderSummaryEmptyText(
                    graphics,
                    left,
                    right,
                    top,
                    "No external resources"
            );
            return;
        }

        int bodyTop = top + 27;
        int rowHeight = 18;

        int maxRows =
                Math.max(
                        1,
                        Math.min(
                                2,
                                (bottom - bodyTop - 4)
                                        / rowHeight
                        )
                );

        int visible =
                Math.min(
                        resources.size(),
                        maxRows
                );

        for (int i = 0; i < visible; i++) {
            renderSummaryResourceRow(
                    graphics,
                    resources.get(i),
                    left,
                    right,
                    bodyTop + i * rowHeight
            );
        }

        renderSummaryMoreCount(
                graphics,
                resources.size(),
                visible,
                right,
                bottom
        );
    }

    private void renderProductionSummaryOutputs(
            GuiGraphics graphics,
            CraftScopeProductionSummary summary,
            int left,
            int right,
            int top,
            int bottom
    ) {
        List<CraftScopeResourceAmount> outputs =
                summary.outputs();

        if (outputs.isEmpty()) {
            renderSummaryEmptyText(
                    graphics,
                    left,
                    right,
                    top,
                    "No output data"
            );
            return;
        }

        int bodyTop = top + 27;
        int rowHeight = 18;

        int maxRows =
                Math.max(
                        1,
                        Math.min(
                                2,
                                (bottom - bodyTop - 4)
                                        / rowHeight
                        )
                );

        int visible =
                Math.min(
                        outputs.size(),
                        maxRows
                );

        for (int i = 0; i < visible; i++) {
            renderSummaryResourceRow(
                    graphics,
                    outputs.get(i),
                    left,
                    right,
                    bodyTop + i * rowHeight
            );
        }

        renderSummaryMoreCount(
                graphics,
                outputs.size(),
                visible,
                right,
                bottom
        );
    }

    private void renderSummaryRequirementRow(
            GuiGraphics graphics,
            CraftScopeProcessRequirement requirement,
            int left,
            int right,
            int y
    ) {
        int iconX = left + 8;
        int textX = iconX;

        ItemStack stack =
                getSummaryRequirementStack(requirement);

        if (!stack.isEmpty()) {
            graphics.renderItem(
                    stack,
                    iconX,
                    y
            );

            textX += 20;
        }

        String amount =
                formatSummaryRequirementAmount(requirement);

        String label =
                requirement
                        .displayName()
                        .getString();

        if (!amount.isEmpty()) {
            label += " " + amount;
        }

        graphics.drawString(
                font,
                fitText(
                        label,
                        right - textX - 8
                ),
                textX,
                y + 4,
                CraftScopeUiTheme.TEXT_PRIMARY
        );
    }

    private void renderSummaryResourceRow(
            GuiGraphics graphics,
            CraftScopeResourceAmount resource,
            int left,
            int right,
            int y
    ) {
        int iconX = left + 8;
        int textX = iconX + 20;

        ItemStack stack =
                getSummaryResourceStack(resource);

        if (!stack.isEmpty()) {
            graphics.renderItem(
                    stack,
                    iconX,
                    y
            );
        } else {
            renderSummaryResourcePlaceholder(
                    graphics,
                    resource,
                    iconX,
                    y
            );
        }

        String label =
                getSummaryResourceDisplayName(resource)
                        + " "
                        + formatSummaryResourceAmount(resource);

        graphics.drawString(
                font,
                fitText(
                        label,
                        right - textX - 8
                ),
                textX,
                y + 4,
                CraftScopeUiTheme.TEXT_PRIMARY
        );
    }

    private void renderSummaryResourcePlaceholder(
            GuiGraphics graphics,
            CraftScopeResourceAmount resource,
            int x,
            int y
    ) {
        graphics.fill(
                x,
                y,
                x + 16,
                y + 16,
                CraftScopeUiTheme.BUTTON_BACKGROUND
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                x,
                y,
                x + 16,
                y + 16,
                CraftScopeUiTheme.BORDER_HOVER
        );

        String letter =
                switch (resource.kind()) {
                    case ITEM -> "I";
                    case FLUID -> "F";
                    case CHEMICAL -> "C";
                    case OTHER -> "?";
                };

        graphics.drawCenteredString(
                font,
                letter,
                x + 8,
                y + 4,
                CraftScopeUiTheme.TEXT_SECONDARY
        );
    }

    private ItemStack getSummaryRequirementStack(
            CraftScopeProcessRequirement requirement
    ) {
        ResourceLocation id = requirement.id();

        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(id);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);

        return stack.isEmpty()
                ? ItemStack.EMPTY
                : stack;
    }

    private ItemStack getSummaryResourceStack(
            CraftScopeResourceAmount resource
    ) {
        if (resource.kind()
                != CraftScopeResourceKind.ITEM) {

            return ItemStack.EMPTY;
        }

        List<ResourceLocation> variants =
                resource.acceptedVariantIds();

        ResourceLocation id = resource.id();

        if (variants != null && !variants.isEmpty()) {
            if (variants.size() == 1) {
                id = variants.getFirst();
            } else {
                long cycle =
                        System.currentTimeMillis()
                                / VARIANT_CYCLE_MS;

                int index =
                        (int) (cycle % variants.size());

                id = variants.get(index);
            }
        }

        Item item =
                BuiltInRegistries.ITEM.get(id);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);

        return stack.isEmpty()
                ? ItemStack.EMPTY
                : stack;
    }

    private String getSummaryResourceDisplayName(
            CraftScopeResourceAmount resource
    ) {
        if (!resource.hasVariants()) {
            return resource
                    .displayName()
                    .getString();
        }

        List<ItemStack> variants =
                new ArrayList<>();

        for (ResourceLocation id :
                resource.acceptedVariantIds()) {

            Item item =
                    BuiltInRegistries.ITEM.get(id);

            if (item == null) {
                continue;
            }

            ItemStack stack = new ItemStack(item);

            if (!stack.isEmpty()) {
                variants.add(stack);
            }
        }

        String generic =
                findGenericVariantName(variants);

        if (generic != null && !generic.isBlank()) {
            return "Any " + generic;
        }

        return "Any "
                + resource
                .displayName()
                .getString();
    }

    private String formatSummaryResourceAmount(
            CraftScopeResourceAmount resource
    ) {
        if (resource.hasUnit()) {
            return resource.amount()
                    + " "
                    + resource.unit();
        }

        return "x" + resource.amount();
    }

    private String formatSummaryRequirementAmount(
            CraftScopeProcessRequirement requirement
    ) {
        if (requirement.hasUnit()) {
            return requirement.amount()
                    + " "
                    + requirement.unit();
        }

        if (requirement.amount() <= 1) {
            return "";
        }

        return "x" + requirement.amount();
    }

    private void renderSummaryEmptyText(
            GuiGraphics graphics,
            int left,
            int right,
            int top,
            String text
    ) {
        graphics.drawCenteredString(
                font,
                fitText(
                        text,
                        right - left - 16
                ),
                (left + right) / 2,
                top + 39,
                CraftScopeUiTheme.TEXT_MUTED
        );
    }

    private void renderSummaryMoreCount(
            GuiGraphics graphics,
            int total,
            int visible,
            int right,
            int bottom
    ) {
        if (total <= visible) {
            return;
        }

        String text =
                "+"
                        + (total - visible)
                        + " more";

        graphics.drawString(
                font,
                text,
                right - font.width(text) - 8,
                bottom - 11,
                CraftScopeUiTheme.TEXT_MUTED
        );
    }

    private void drawSectionTitle(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            String title
    ) {
        int headerBottom = top + 22;

        CraftScopeUiTheme.drawSectionHeader(
                graphics,
                left + 1,
                top + 1,
                right - 1,
                headerBottom
        );

        graphics.drawString(
                font,
                title,
                left + 8,
                top + 7,
                CraftScopeUiTheme.TEXT_PRIMARY
        );
    }

    private void drawPanel(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        CraftScopeUiTheme.drawPanel(
                graphics,
                left,
                top,
                right,
                bottom
        );
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
        if (variants == null || variants.isEmpty()) {
            return fallback.copy();
        }

        if (variants.size() == 1) {
            return variants.getFirst().copy();
        }

        long cycle =
                System.currentTimeMillis()
                        / VARIANT_CYCLE_MS;

        int index =
                (int) (cycle % variants.size());

        return variants.get(index).copy();
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
        if (variants == null || variants.size() <= 1) {
            return fallback
                    .getHoverName()
                    .getString();
        }

        String genericName =
                findGenericVariantName(variants);

        if (genericName != null) {
            return "Any " + genericName;
        }

        return "Any Valid Ingredient";
    }

    private String findGenericVariantName(
            List<ItemStack> variants
    ) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        boolean allLogs = true;
        boolean allPlanks = true;
        boolean allWool = true;

        for (ItemStack variant : variants) {
            if (!variant.is(ItemTags.LOGS)) {
                allLogs = false;
            }

            if (!variant.is(ItemTags.PLANKS)) {
                allPlanks = false;
            }

            if (!variant.is(ItemTags.WOOL)) {
                allWool = false;
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

        return findCommonWordSuffix(variants);
    }

    private String findCommonWordSuffix(
            List<ItemStack> variants
    ) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        List<String[]> names = new ArrayList<>();

        for (ItemStack variant : variants) {
            String name =
                    variant
                            .getHoverName()
                            .getString()
                            .trim();

            if (name.isEmpty()) {
                return null;
            }

            names.add(name.split("\\s+"));
        }

        String[] first = names.getFirst();
        int commonWords = 0;

        for (int offset = 1;
             offset <= first.length;
             offset++) {

            String expected =
                    first[first.length - offset];

            boolean matchesAll = true;

            for (int i = 1; i < names.size(); i++) {
                String[] words = names.get(i);

                if (words.length < offset
                        || !words[
                        words.length - offset
                        ].equalsIgnoreCase(expected)) {

                    matchesAll = false;
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
                first.length - commonWords;

        for (int i = start; i < first.length; i++) {
            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(first[i]);
        }

        return result.toString();
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

        int barX = getContentRight() - 5;

        graphics.fill(
                barX,
                viewportTop,
                barX + 3,
                viewportBottom,
                CraftScopeUiTheme.BORDER_SUBTLE
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
                        contentHeight - viewportHeight
                );

        int travel =
                viewportHeight - thumbHeight;

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
                CraftScopeUiTheme.TEXT_MUTED
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
        int count = 1;

        if (!expandedNodes.contains(nodePath)) {
            return count;
        }

        List<CraftScopeRecipeNode> children =
                node.getChildren();

        for (int i = 0; i < children.size(); i++) {
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
        int bottom =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN;

        if (activeView == ViewMode.PROCESS_DIAGRAM
                || activeView == ViewMode.SETUP) {

            bottom -= 80;
        }

        return Math.max(
                CONTENT_VIEWPORT_TOP
                        + CONTENT_ROW_HEIGHT,
                bottom
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
                        contentHeight - getViewportHeight()
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
                        contentHeight - getViewportHeight()
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
        if (activeView == ViewMode.PROCESS_DIAGRAM
                && button == 0
                && handleViewRecipeClick(
                mouseX,
                mouseY
        )) {
            return true;
        }

        if (activeView == ViewMode.PROCESS_DIAGRAM
                && button == 0
                && handleProcessMethodClick(
                mouseX,
                mouseY
        )) {
            return true;
        }

        if (activeView == ViewMode.PROCESS_DIAGRAM
                && button == 0
                && handleProcessDiagramNodeClick(
                mouseX,
                mouseY
        )) {
            return true;
        }

        if (activeView == ViewMode.PROCESS_DIAGRAM
                && button == 0
                && handleProductionRouteClick(
                mouseX,
                mouseY
        )) {
            return true;
        }

        if (activeView == ViewMode.RECIPE_TREE
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

    private boolean handleViewRecipeClick(
            double mouseX,
            double mouseY
    ) {
        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        if (route == null) {
            return false;
        }

        CraftScopeProcessDiagramRenderer.Selection selection =
                CraftScopeProcessDiagramRenderer.getSelection(
                        route,
                        project.getTargetCount(),
                        selectedDiagramNodeIndex
                );

        if (selection == null
                || !selection.isProcess()
                || selection.step() == null) {

            return false;
        }

        CraftScopeProductionMethod selectedMethod =
                getSelectedMethod(
                        route,
                        selection.step()
                );

        if (selectedMethod == null
                || !selectedMethod.hasRecipes()) {

            return false;
        }

        ProcessLayout layout =
                getProcessLayout();

        int buttonLeft =
                layout.detailsLeft() + 9;

        int buttonRight =
                layout.right() - 9;

        int buttonTop =
                layout.mainBottom() - 27;

        int buttonBottom =
                buttonTop + 18;

        if (mouseX < buttonLeft
                || mouseX >= buttonRight
                || mouseY < buttonTop
                || mouseY >= buttonBottom) {

            return false;
        }

        if (CraftScopeRecipeViewer.isAvailable()) {
            CraftScopeRecipeViewer.openRecipe(
                    selectedMethod.processId(),
                    selectedMethod.recipeIds()
            );
        }

        return true;
    }

    private boolean handleProcessMethodClick(
            double mouseX,
            double mouseY
    ) {
        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        if (route == null) {
            return false;
        }

        CraftScopeProcessDiagramRenderer.Selection selection =
                CraftScopeProcessDiagramRenderer.getSelection(
                        route,
                        project.getTargetCount(),
                        selectedDiagramNodeIndex
                );

        if (selection == null
                || !selection.isProcess()
                || selection.step() == null) {

            return false;
        }

        CraftScopeProductionStep step =
                selection.step();

        if (step.methods().size() <= 1) {
            return false;
        }

        ProcessLayout layout =
                getProcessLayout();

        int left = layout.detailsLeft() + 9;
        int right = layout.right() - 9;
        int rowY = layout.top() + 102;
        int rowHeight = 18;

        for (int i = 0; i < step.methods().size(); i++) {
            if (mouseX >= left
                    && mouseX < right
                    && mouseY >= rowY
                    && mouseY < rowY + rowHeight) {

                selectMethod(route, step, i);
                return true;
            }

            rowY += rowHeight + 3;
        }

        return false;
    }

    private boolean handleProcessDiagramNodeClick(
            double mouseX,
            double mouseY
    ) {
        CraftScopeProductionRoute route =
                getSelectedProductionRoute();

        if (route == null) {
            return false;
        }

        ProcessLayout layout =
                getProcessLayout();

        CraftScopeProcessDiagramRenderer.Selection selection =
                CraftScopeProcessDiagramRenderer.hitTest(
                        route,
                        layout.centerLeft() + 6,
                        layout.top() + 25,
                        layout.centerRight() - 6,
                        layout.mainBottom() - 6,
                        project.getTargetCount(),
                        mouseX,
                        mouseY
                );

        if (selection == null) {
            return false;
        }

        selectedDiagramNodeIndex =
                selection.nodeIndex();

        return true;
    }

    private boolean handleProductionRouteClick(
            double mouseX,
            double mouseY
    ) {
        if (productionRoutes.isEmpty()) {
            return false;
        }

        ProcessLayout layout =
                getProcessLayout();

        int rowTop = layout.top() + 25;
        int rowHeight = 22;

        int availableHeight =
                layout.routesBottom()
                        - rowTop
                        - 4;

        int maxVisible =
                Math.max(
                        0,
                        availableHeight / rowHeight
                );

        int visibleCount =
                Math.min(
                        productionRoutes.size(),
                        maxVisible
                );

        if (mouseX < layout.left() + 4
                || mouseX
                >= layout.left()
                + layout.leftColumnWidth()
                - 4
                || mouseY < rowTop
                || mouseY
                >= rowTop
                + visibleCount
                * rowHeight) {

            return false;
        }

        int clickedIndex =
                (int) (
                        (mouseY - rowTop)
                                / rowHeight
                );

        if (clickedIndex < 0
                || clickedIndex >= visibleCount) {

            return false;
        }

        selectedProductionRouteIndex =
                clickedIndex;

        selectedDiagramNodeIndex = -1;
        return true;
    }

    private boolean handleTreeClick(
            double mouseX,
            double mouseY
    ) {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return false;
        }

        int viewportTop = CONTENT_VIEWPORT_TOP;
        int viewportBottom = getViewportBottom();

        if (mouseY < viewportTop
                || mouseY >= viewportBottom) {

            return false;
        }

        int treeLeft =
                Math.max(
                        getContentLeft() + 12,
                        getContentCenterX() - 140
                );

        int rowY =
                viewportTop - (int) treeScroll;

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
        int indent = depth * TREE_INDENT;
        int arrowX = treeLeft + indent;
        int iconX = arrowX + 12;

        String itemText =
                getNodeDisplayName(node)
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
            cycleRecipe(nodePath, node);

            return new ClickResult(
                    true,
                    rowY + CONTENT_ROW_HEIGHT
            );
        }

        boolean hasChildren =
                !node.getChildren().isEmpty();

        if (hasChildren
                && mouseY >= rowY
                && mouseY < rowY + CONTENT_ROW_HEIGHT
                && mouseX >= arrowX - 2
                && mouseX < arrowX + 28) {

            if (expandedNodes.contains(nodePath)) {
                expandedNodes.remove(nodePath);
            } else {
                expandedNodes.add(nodePath);
            }

            clampTreeScroll();

            return new ClickResult(
                    true,
                    rowY + CONTENT_ROW_HEIGHT
            );
        }

        int nextY =
                rowY + CONTENT_ROW_HEIGHT;

        if (expandedNodes.contains(nodePath)) {
            List<CraftScopeRecipeNode> children =
                    node.getChildren();

            for (int i = 0; i < children.size(); i++) {
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

                nextY = result.nextY();
            }
        }

        return new ClickResult(false, nextY);
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
                recipeChoices.get(nodePath);

        if (choices == null || choices.size() <= 1) {
            return false;
        }

        int currentIndex =
                getCurrentRecipeIndex(nodePath, node);

        String selectorText =
                "["
                        + (currentIndex + 1)
                        + "/"
                        + choices.size()
                        + "]";

        int desiredX =
                iconX
                        + 20
                        + font.width(itemText)
                        + 8;

        int maxX =
                getContentRight()
                        - font.width(selectorText)
                        - 8;

        int selectorX =
                Math.min(desiredX, maxX);

        return mouseX >= selectorX - 2
                && mouseX
                < selectorX
                + font.width(selectorText)
                + 2
                && mouseY >= rowY
                && mouseY < rowY + CONTENT_ROW_HEIGHT;
    }

    private void cycleRecipe(
            String nodePath,
            CraftScopeRecipeNode node
    ) {
        List<ResourceLocation> choices =
                recipeChoices.get(nodePath);

        if (choices == null || choices.size() <= 1) {
            return;
        }

        int currentIndex =
                getCurrentRecipeIndex(nodePath, node);

        int nextIndex =
                (currentIndex + 1)
                        % choices.size();

        ResourceLocation nextRecipe =
                choices.get(nextIndex);

        if (nextIndex == 0) {
            recipeOverrides.remove(nodePath);
            project.removeRecipeOverride(nodePath);
        } else {
            recipeOverrides.put(nodePath, nextRecipe);
            project.setRecipeOverride(
                    nodePath,
                    nextRecipe.toString()
            );
        }

        clearDescendantRecipeState(nodePath);

        CraftScopeProjectManager.save();
        rebuildTree();
    }

    private void clearDescendantRecipeState(
            String nodePath
    ) {
        String prefix = nodePath + "/";

        recipeOverrides
                .keySet()
                .removeIf(
                        key -> key.startsWith(prefix)
                );

        recipeChoices
                .keySet()
                .removeIf(
                        key -> key.startsWith(prefix)
                );

        expandedNodes.removeIf(
                key -> key.startsWith(prefix)
        );

        List<String> savedDescendants =
                new ArrayList<>();

        for (String key :
                project
                        .getRecipeOverrides()
                        .keySet()) {

            if (key.startsWith(prefix)) {
                savedDescendants.add(key);
            }
        }

        for (String key : savedDescendants) {
            project.removeRecipeOverride(key);
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        int viewportTop = CONTENT_VIEWPORT_TOP;
        int viewportBottom = getViewportBottom();

        if (mouseY >= viewportTop
                && mouseY < viewportBottom) {

            if (activeView == ViewMode.RECIPE_TREE) {
                treeScroll -=
                        scrollY * SCROLL_AMOUNT;

                clampTreeScroll();
                return true;
            }

            if (activeView == ViewMode.TOTAL_MATERIALS) {
                materialScroll -=
                        scrollY * SCROLL_AMOUNT;

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

        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation location =
                ResourceLocation.tryParse(itemId);

        if (location == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                BuiltInRegistries.ITEM.get(location);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
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
                + getItemId(child.getStack());
    }

    private String getItemId(ItemStack stack) {
        return BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .toString();
    }

    @Override
    public void craftscope$setTargetItem(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        project.setTargetItemId(itemId.toString());
        project.clearRecipeOverrides();

        expandedNodes.clear();
        recipeOverrides.clear();
        recipeChoices.clear();
        selectedMethodIndices.clear();

        selectedProductionRouteIndex = -1;
        selectedDiagramNodeIndex = -1;

        treeScroll = 0;
        materialScroll = 0;

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
        return activeView == ViewMode.RECIPE_TREE;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
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

    private record ProcessLayout(
            int left,
            int right,
            int top,
            int mainBottom,
            int summaryTop,
            int summaryHeight,
            int gap,
            int leftColumnWidth,
            int rightColumnWidth,
            int centerLeft,
            int centerRight,
            int detailsLeft,
            int routesBottom,
            int resourcesTop,
            int resourcesBottom,
            int legendTop
    ) {
    }
}
