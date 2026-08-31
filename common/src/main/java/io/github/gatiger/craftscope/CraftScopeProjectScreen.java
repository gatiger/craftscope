package io.github.gatiger.craftscope;

import io.github.gatiger.craftscope.client.CraftScopeTargetItemReceiver;
import io.github.gatiger.craftscope.integration.CraftScopeRecipeViewer;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummary;
import io.github.gatiger.craftscope.material.CraftScopeMaterialSummarizer;
import io.github.gatiger.craftscope.production.CraftScopeProcessRequirement;
import io.github.gatiger.craftscope.production.CraftScopeProductionMethod;
import io.github.gatiger.craftscope.production.CraftScopeProductionDisplayPolicy;
import io.github.gatiger.craftscope.production.CraftScopeProductionRoute;
import io.github.gatiger.craftscope.production.CraftScopeProductionRouteQuery;
import io.github.gatiger.craftscope.production.CraftScopeProductionStep;
import io.github.gatiger.craftscope.production.CraftScopeProductionSummary;
import io.github.gatiger.craftscope.production.CraftScopeRequirementKind;
import io.github.gatiger.craftscope.production.CraftScopeResourceAmount;
import io.github.gatiger.craftscope.production.CraftScopeResourceKind;
import io.github.gatiger.craftscope.project.CraftScopeItemStackPersistence;
import io.github.gatiger.craftscope.project.CraftScopeProject;
import io.github.gatiger.craftscope.project.CraftScopeProjectManager;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeNode;
import io.github.gatiger.craftscope.recipe.CraftScopeProductionRecipeTreeBuilder;
import io.github.gatiger.craftscope.recipe.CraftScopeRecipeTree;
import io.github.gatiger.craftscope.ui.CraftScopeBaseScreen;
import io.github.gatiger.craftscope.ui.CraftScopeFlatButton;
import io.github.gatiger.craftscope.ui.CraftScopeProductionRouteTreeModel;
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
import java.util.LinkedHashSet;
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

    /*
     * Shared between Recipe Tree and Process Diagram.
     *
     * Source groups start collapsed except for the group containing
     * the current selected route.
     */
    private final Set<String> expandedProductionRouteSources =
            new HashSet<>();

    /*
     * Recipe Tree has a full-height route panel, so it owns a small
     * independent scroll value. Process Diagram keeps using the
     * existing overflow mixin scroll state.
     */
    private double recipeProductionRouteScroll;
    private int recipeProductionRouteMaxScroll;

    /*
     * Deduplicated process catalog for the entire reachable
     * production plan.
     */
    private List<CraftScopeProductionRouteTreeModel.ProcessOption>
            productionProcessOptions =
            List.of();

    /*
     * Shared process selection context for Recipe Tree and Process
     * Diagram. A later pass uses this exact context to filter recipe
     * alternatives.
     */
    private String selectedProductionProcessSourceId;
    private ResourceLocation selectedProductionProcessId;

    /*
     * Recipe Tree ingredient-alternative popup.
     *
     * The choice list is an overlay: all alternatives are visible
     * while choosing, then it collapses after selection so only the
     * active material remains in the production plan.
     */
    private String ingredientChoicePopupPath;
    private CraftScopeRecipeNode ingredientChoicePopupNode;
    private boolean ingredientChoicePopupAnchorVisible;
    private int ingredientChoicePopupAnchorX;
    private int ingredientChoicePopupAnchorY;
    private int ingredientChoicePopupLeft;
    private int ingredientChoicePopupTop;
    private int ingredientChoicePopupRight;
    private int ingredientChoicePopupBottom;
    private int ingredientChoicePopupRowHeight = 22;

    public CraftScopeProjectScreen(
            Screen parent,
            CraftScopeProject project
    ) {
        super(Component.literal(project.getName()));
        this.parent = parent;
        this.project = project;
        loadRecipeOverrides();
        loadProductionProcessSelection();
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

    private void loadProductionProcessSelection() {
        selectedProductionProcessSourceId = null;
        selectedProductionProcessId = null;

        String savedSourceId =
                project.getProductionProcessSourceId();

        String savedProcessId =
                project.getProductionProcessId();

        if (savedSourceId == null
                || savedSourceId.isBlank()
                || savedProcessId == null
                || savedProcessId.isBlank()) {

            return;
        }

        ResourceLocation processId =
                ResourceLocation.tryParse(
                        savedProcessId
                );

        if (processId == null) {
            return;
        }

        selectedProductionProcessSourceId =
                savedSourceId;

        selectedProductionProcessId =
                processId;
    }
    /*
     * Convert persisted ingredient selections into ResourceLocations
     * for the Recipe Tree builder.
     *
     * This used to live in the legacy Recipe Source mixin. Production
     * Routes is now the only source/process selector, so the screen
     * owns this conversion directly.
     */
    private Map<String, ResourceLocation>
    getIngredientVariantOverrides() {

        Map<String, ResourceLocation> result =
                new LinkedHashMap<>();

        for (Map.Entry<String, String> entry :
                project
                        .getIngredientVariantOverrides()
                        .entrySet()) {

            ResourceLocation id =
                    ResourceLocation.tryParse(
                            entry.getValue()
                    );

            if (id != null) {
                result.put(
                        entry.getKey(),
                        id
                );
            }
        }

        return Map.copyOf(
                result
        );
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
        ingredientChoicePopupPath = null;
        ingredientChoicePopupNode = null;
        ingredientChoicePopupAnchorVisible = false;

        ItemStack target = getTargetStack();

        if (target.isEmpty()) {
            currentTree = null;
            currentMaterialSummary =
                    new CraftScopeMaterialSummary(List.of());

            productionRoutes = List.of();
            productionProcessOptions = List.of();
            selectedProductionRouteIndex = -1;
            selectedDiagramNodeIndex = -1;
            selectedProductionProcessSourceId = null;
            selectedProductionProcessId = null;

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
                recipeOverrides,
                getIngredientVariantOverrides(),
                null,
                selectedProductionProcessSourceId,
                selectedProductionProcessId
        );

        currentMaterialSummary =
                CraftScopeMaterialSummarizer.summarize(currentTree);

        rebuildProductionRoutes(target);
        populateRecipeChoices();
        rebuildProductionProcessOptions(target);

        /*
         * Recipe Tree opens fully expanded by default.
         *
         * Once the player starts collapsing nodes, ordinary rebuilds
         * preserve that session state rather than forcing everything
         * open again.
         */
        if (expandedNodes.isEmpty()) {
            expandAllRecipeNodes();
        }

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

        ensureSelectedProductionRouteSourceExpanded();
        clampRecipeProductionRouteScroll();
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

    private int getRecipeRoutePanelWidth() {
        int availableWidth =
                getContentRight()
                        - getContentLeft();

        return Math.min(
                155,
                Math.max(
                        120,
                        availableWidth / 5
                )
        );
    }

    private int getRecipeTreePanelLeft() {
        return getContentLeft()
                + getRecipeRoutePanelWidth()
                + 6;
    }

    private void renderRecipeTree(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        ingredientChoicePopupAnchorVisible = false;
        ingredientChoicePopupNode = null;

        int routesLeft =
                getContentLeft();

        int routeWidth =
                getRecipeRoutePanelWidth();

        int routesRight =
                routesLeft
                        + routeWidth;

        int treePanelLeft =
                routesRight + 6;

        int treePanelRight =
                getContentRight();

        int panelTop =
                CONTENT_TITLE_Y - 4;

        int panelBottom =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN;

        /*
         * -----------------------------------------------------
         * Production Routes
         * -----------------------------------------------------
         *
         * Recipe Tree now exposes the same root production-route
         * choice as Process Diagram. Selecting a row here changes
         * the shared selectedProductionRouteIndex. The existing
         * route-selection synchronization mixin then updates the
         * root Recipe Tree override whenever the chosen process
         * belongs to a different material route.
         */
        drawPanel(
                graphics,
                routesLeft,
                panelTop,
                routesRight,
                panelBottom
        );

        drawSectionTitle(
                graphics,
                routesLeft,
                panelTop,
                routesRight,
                "Production Routes"
        );

        renderRecipeProductionRoutes(
                graphics,
                routesLeft,
                panelTop,
                routesRight,
                panelBottom
        );

        /*
         * -----------------------------------------------------
         * Recipe Tree
         * -----------------------------------------------------
         */
        drawPanel(
                graphics,
                treePanelLeft,
                panelTop,
                treePanelRight,
                panelBottom
        );

        drawSectionTitle(
                graphics,
                treePanelLeft,
                panelTop,
                treePanelRight,
                "Recipe Tree"
        );

        int treeCenterX =
                treePanelLeft
                        + (
                        treePanelRight
                                - treePanelLeft
                ) / 2;

        if (currentTree == null
                || currentTree.getRoot() == null) {

            graphics.drawCenteredString(
                    font,
                    "Select a target item to build a recipe tree.",
                    treeCenterX,
                    CONTENT_VIEWPORT_TOP + 12,
                    CraftScopeUiTheme.TEXT_MUTED
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
                        treePanelLeft + 12,
                        treeCenterX - 140
                );

        graphics.enableScissor(
                treePanelLeft + 1,
                viewportTop,
                treePanelRight - 1,
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

        renderIngredientChoicePopup(
                graphics,
                mouseX,
                mouseY
        );
    }

    private List<CraftScopeProductionRouteTreeModel.Row>
    getProductionRouteTreeRows() {

        return CraftScopeProductionRouteTreeModel.buildRows(
                productionProcessOptions,
                expandedProductionRouteSources,
                selectedProductionProcessSourceId,
                selectedProductionProcessId
        );
    }

    private void rebuildProductionProcessOptions(
            ItemStack target
    ) {
        if (target == null
                || target.isEmpty()) {

            productionProcessOptions =
                    List.of();

            selectedProductionProcessSourceId =
                    null;

            selectedProductionProcessId =
                    null;

            return;
        }

        Map<String, CraftScopeProductionRouteTreeModel.ProcessOption>
                options =
                new LinkedHashMap<>();

        /*
         * First add the concrete target-level UI routes so matching
         * process rows retain a routeIndex and can still drive the
         * existing Process Diagram route selection.
         */
        for (int routeIndex = 0;
             routeIndex < productionRoutes.size();
             routeIndex++) {

            collectProductionProcessOptionsFromRoute(
                    productionRoutes.get(
                            routeIndex
                    ),
                    routeIndex,
                    options
            );
        }

        /*
         * Then recursively inspect every reachable material route,
         * including alternate recipes, but store only unique
         * source+process pairs.
         *
         * This is why a Lectern can expose Create -> Crafting when
         * Create supplies an alternate Book recipe without ever
         * showing Book/Cardboard/Leather rows in this panel.
         */
        collectProductionProcessOptionsForItem(
                target,
                options,
                new HashSet<>(),
                0
        );

        productionProcessOptions =
                List.copyOf(
                        options.values()
                );

        preserveOrChooseProductionProcessSelection();
        ensureSelectedProductionRouteSourceExpanded();
        clampRecipeProductionRouteScroll();
    }

    private void collectProductionProcessOptionsForItem(
            ItemStack stack,
            Map<String, CraftScopeProductionRouteTreeModel.ProcessOption>
                    options,
            Set<String> visitedItems,
            int depth
    ) {
        if (stack == null
                || stack.isEmpty()
                || options == null
                || visitedItems == null
                || depth >= 8) {

            return;
        }

        String itemId =
                getItemId(
                        stack
                );

        if (!visitedItems.add(
                itemId
        )) {

            return;
        }

        List<CraftScopeProductionRoute> routes =
                CraftScopeProductionRouteQuery.findDirectRoutes(
                        stack
                );

        for (CraftScopeProductionRoute route :
                routes) {

            collectProductionProcessOptionsFromRoute(
                    route,
                    -1,
                    options
            );

            for (CraftScopeProductionStep step :
                    route.steps()) {

                for (CraftScopeResourceAmount input :
                        step.inputs()) {

                    if (input.kind()
                            != CraftScopeResourceKind.ITEM
                            || !input.consumed()) {

                        continue;
                    }

                    int variantCount =
                            0;

                    for (ResourceLocation inputId :
                            input.acceptedVariantIds()) {

                        if (inputId == null) {
                            continue;
                        }

                        Item inputItem =
                                BuiltInRegistries.ITEM.get(
                                        inputId
                                );

                        if (inputItem == null) {
                            continue;
                        }

                        ItemStack inputStack =
                                new ItemStack(
                                        inputItem
                                );

                        if (!inputStack.isEmpty()) {
                            collectProductionProcessOptionsForItem(
                                    inputStack,
                                    options,
                                    visitedItems,
                                    depth + 1
                            );
                        }

                        /*
                         * Broad tags such as logs can contain many
                         * equivalent variants. We only need enough
                         * representatives to discover process types.
                         */
                        variantCount++;

                        if (variantCount >= 8) {
                            break;
                        }
                    }
                }
            }
        }
    }

    private void collectProductionProcessOptionsFromRoute(
            CraftScopeProductionRoute route,
            int routeIndex,
            Map<String, CraftScopeProductionRouteTreeModel.ProcessOption>
                    options
    ) {
        if (route == null
                || options == null) {

            return;
        }

        /*
         * Full Production Chain is a CraftScope UI route rather than
         * one ordinary machine/process method. Keep it as its own
         * compact process choice.
         */
        if ("craftscope".equals(
                route.sourceModId()
        )) {

            ResourceLocation processId =
                    ResourceLocation.tryParse(
                            "craftscope:full_production_chain"
                    );

            if (processId != null) {
                putProductionProcessOption(
                        options,
                        new CraftScopeProductionRouteTreeModel.ProcessOption(
                                "craftscope",
                                "CraftScope",
                                processId,
                                route
                                        .displayName()
                                        .getString(),
                                routeIndex
                        )
                );
            }

            return;
        }

        /*
         * Production Routes is intentionally a PROCESS selector,
         * not an acquisition-method selector.
         *
         * Keep:
         *   Crafting
         *   Smelting / Blasting
         *   Create Crushing / Milling / Mixing / Washing / etc.
         *
         * Exclude:
         *   Kill Cow / Kill Hoglin / ...
         *   Farming Bamboo / Farming Sugar Cane / ...
         *   Mining / Digging / Breaking
         *   other world-acquisition routes
         *
         * Those acquisition methods remain fully available in Recipe
         * Tree and the complete production plan.
         */
        if (!isProductionProcessCatalogRoute(
                route
        )) {

            return;
        }

        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeProductionMethod method :
                    step.methods()) {

                Set<String> sourceIds =
                        getProductionProcessSourceIds(
                                method
                        );

                for (String sourceId :
                        sourceIds) {

                    String sourceName =
                            resolveProductionProcessSourceName(
                                    route,
                                    sourceId
                            );

                    putProductionProcessOption(
                            options,
                            new CraftScopeProductionRouteTreeModel.ProcessOption(
                                    sourceId,
                                    sourceName,
                                    method.processId(),
                                    method
                                            .displayName()
                                            .getString(),
                                    routeIndex
                            )
                    );
                }
            }
        }
    }

    private boolean isProductionProcessCatalogRoute(
            CraftScopeProductionRoute route
    ) {
        if (route == null
                || route.steps().isEmpty()) {

            return false;
        }

        /*
         * CraftScope acquisition providers use acquisition/* route
         * IDs for mining, digging, breaking, farming, and similar
         * world-gathering operations.
         *
         * These remain valid in Recipe Tree/full planning but do not
         * belong in the Production Routes process selector.
         */
        ResourceLocation routeId =
                route.id();

        if (routeId != null
                && "craftscope".equals(
                routeId.getNamespace()
        )
                && routeId
                .getPath()
                .startsWith(
                        "acquisition/"
                )) {

            return false;
        }

        /*
         * Mob drops, loot, passive gathering, and other pure
         * acquisition routes have outputs without a consumed process
         * input. Do not list them as processing methods.
         *
         * Real transformationsâ€”crafting, smelting, blasting, Create
         * processing, future fluid/chemical transformations, etc.â€”
         * consume at least one input resource.
         */
        for (CraftScopeProductionStep step :
                route.steps()) {

            for (CraftScopeResourceAmount input :
                    step.inputs()) {

                if (input != null
                        && input.consumed()) {

                    return true;
                }
            }
        }

        return false;
    }
    private Set<String> getProductionProcessSourceIds(
            CraftScopeProductionMethod method
    ) {
        if (method == null) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        String methodSource =
                method.sourceModId();

        /*
         * A genuinely modded processing method belongs to that mod
         * even if it reuses a minecraft:* recipe definition.
         *
         * Example:
         *     Create Bulk Blasting -> Create
         */
        if (methodSource != null
                && !methodSource.isBlank()
                && !"minecraft".equals(
                methodSource
        )) {

            result.add(
                    methodSource
            );

            return Set.copyOf(
                    result
            );
        }

        /*
         * Generic Minecraft crafting/smelting can execute recipes
         * owned by another mod. In that case the recipe namespace is
         * the useful source group.
         *
         * Example:
         *     create:book_from_cardboard -> Create -> Crafting
         */
        for (ResourceLocation recipeId :
                method.recipeIds()) {

            if (recipeId != null
                    && recipeId.getNamespace() != null
                    && !recipeId
                    .getNamespace()
                    .isBlank()) {

                result.add(
                        recipeId.getNamespace()
                );
            }
        }

        if (result.isEmpty()) {
            result.add(
                    methodSource == null
                            || methodSource.isBlank()
                            ? "minecraft"
                            : methodSource
            );
        }

        return Set.copyOf(
                result
        );
    }

    private String resolveProductionProcessSourceName(
            CraftScopeProductionRoute route,
            String sourceId
    ) {
        if (route != null
                && sourceId != null
                && sourceId.equals(
                route.sourceModId()
        )
                && route.sourceModName() != null) {

            String name =
                    route
                            .sourceModName()
                            .getString();

            if (name != null
                    && !name.isBlank()) {

                return name;
            }
        }

        return CraftScopeProductionRouteTreeModel
                .formatSourceName(
                        sourceId
                );
    }

    private void putProductionProcessOption(
            Map<String, CraftScopeProductionRouteTreeModel.ProcessOption>
                    options,
            CraftScopeProductionRouteTreeModel.ProcessOption option
    ) {
        if (options == null
                || option == null
                || option.sourceId() == null
                || option.sourceId().isBlank()
                || option.processId() == null) {

            return;
        }

        String key =
                option.sourceId()
                        + "|"
                        + option.processId();

        CraftScopeProductionRouteTreeModel.ProcessOption existing =
                options.get(
                        key
                );

        /*
         * Prefer the copy tied to a concrete target-level route so a
         * click can immediately drive Process Diagram when possible.
         */
        if (existing == null
                || (
                existing.routeIndex() < 0
                        && option.routeIndex() >= 0
        )) {

            options.put(
                    key,
                    option
            );
        }
    }

    private void preserveOrChooseProductionProcessSelection() {
        CraftScopeProductionRouteTreeModel.ProcessOption selected =
                findProductionProcessOption(
                        selectedProductionProcessSourceId,
                        selectedProductionProcessId
                );

        if (selected != null) {
            return;
        }

        CraftScopeProductionRouteTreeModel.ProcessOption routeMatch =
                null;

        for (CraftScopeProductionRouteTreeModel.ProcessOption option :
                productionProcessOptions) {

            if (option.routeIndex()
                    == selectedProductionRouteIndex) {

                routeMatch =
                        option;

                break;
            }
        }

        if (routeMatch == null
                && !productionProcessOptions.isEmpty()) {

            routeMatch =
                    productionProcessOptions.getFirst();
        }

        if (routeMatch == null) {
            selectedProductionProcessSourceId =
                    null;

            selectedProductionProcessId =
                    null;

            return;
        }

        selectedProductionProcessSourceId =
                routeMatch.sourceId();

        selectedProductionProcessId =
                routeMatch.processId();
    }

    private CraftScopeProductionRouteTreeModel.ProcessOption
    findProductionProcessOption(
            String sourceId,
            ResourceLocation processId
    ) {
        if (sourceId == null
                || processId == null) {

            return null;
        }

        for (CraftScopeProductionRouteTreeModel.ProcessOption option :
                productionProcessOptions) {

            if (sourceId.equals(
                    option.sourceId()
            )
                    && processId.equals(
                    option.processId()
            )) {

                return option;
            }
        }

        return null;
    }

    private void selectProductionProcessContext(
            String sourceId,
            ResourceLocation processId,
            int routeIndex
    ) {
        if (sourceId == null
                || sourceId.isBlank()
                || processId == null) {

            return;
        }

        boolean processChanged =
                !sourceId.equals(
                        selectedProductionProcessSourceId
                )
                        || !processId.equals(
                        selectedProductionProcessId
                );

        selectedProductionProcessSourceId =
                sourceId;

        selectedProductionProcessId =
                processId;

        expandedProductionRouteSources.add(
                sourceId
        );

        /*
         * A process discovered on the target itself still maps to a
         * concrete Process Diagram route.
         *
         * Nested process choices may have routeIndex == -1. They
         * still rebuild Recipe Tree using the shared source/process
         * context below.
         */
        if (routeIndex >= 0
                && routeIndex < productionRoutes.size()) {

            selectedProductionRouteIndex =
                    routeIndex;

            selectedDiagramNodeIndex =
                    -1;

            /*
             * Preserve the existing root-route synchronization.
             */
            getSelectedProductionRoute();
        }

        if (processChanged) {
            project.setProductionProcessSelection(
                    sourceId,
                    processId.toString()
            );

            CraftScopeProjectManager.save();

            /*
             * Recipe choices are derived from the filtered candidates
             * generated by CraftScopeProductionRecipeTreeBuilder.
             *
             * Clear only the session list; saved explicit recipe
             * overrides remain available and are honored whenever
             * they are compatible with the selected process.
             */
            recipeChoices.clear();

            rebuildTree();

            /*
             * Recipe Tree defaults open after a process change so the
             * player can immediately see which branch changed.
             */
            expandAllRecipeNodes();
            clampTreeScroll();
        }

        clampRecipeProductionRouteScroll();
    }
    private void ensureSelectedProductionRouteSourceExpanded() {
        if (selectedProductionProcessSourceId != null
                && !selectedProductionProcessSourceId.isBlank()) {

            expandedProductionRouteSources.add(
                    selectedProductionProcessSourceId
            );

            return;
        }

        if (selectedProductionRouteIndex < 0
                || selectedProductionRouteIndex
                >= productionRoutes.size()) {

            return;
        }

        CraftScopeProductionRoute route =
                productionRoutes.get(
                        selectedProductionRouteIndex
                );

        String sourceId =
                route.sourceModId();

        if (sourceId != null
                && !sourceId.isBlank()) {

            expandedProductionRouteSources.add(
                    sourceId
            );
        }
    }
    private void toggleProductionRouteSource(
            String sourceId
    ) {
        if (sourceId == null
                || sourceId.isBlank()) {

            return;
        }

        if (expandedProductionRouteSources.contains(
                sourceId
        )) {

            expandedProductionRouteSources.remove(
                    sourceId
            );

        } else {

            expandedProductionRouteSources.add(
                    sourceId
            );
        }

        clampRecipeProductionRouteScroll();
    }

    private void clampRecipeProductionRouteScroll() {
        recipeProductionRouteScroll =
                Math.max(
                        0.0D,
                        Math.min(
                                recipeProductionRouteScroll,
                                recipeProductionRouteMaxScroll
                        )
                );
    }

    private boolean isMouseOverRecipeProductionRoutePanel(
            double mouseX,
            double mouseY
    ) {
        int left =
                getContentLeft();

        int right =
                left
                        + getRecipeRoutePanelWidth();

        int top =
                CONTENT_VIEWPORT_TOP;

        int bottom =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN
                        - 4;

        return mouseX >= left + 2
                && mouseX < right - 2
                && mouseY >= top
                && mouseY < bottom;
    }

    private String getProductionRouteTreeChildLabel(
            CraftScopeProductionRouteTreeModel.Row row
    ) {
        if (row == null
                || !row.isRoute()) {

            return "";
        }

        String label =
                row.displayName();

        return label == null
                ? ""
                : label;
    }
    private void renderRecipeProductionRoutes(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int rowTop =
                top + 25;

        int rowHeight =
                22;

        int viewportBottom =
                Math.max(
                        rowTop,
                        bottom - 4
                );

        int viewportHeight =
                Math.max(
                        0,
                        viewportBottom - rowTop
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

            recipeProductionRouteScroll =
                    0.0D;

            recipeProductionRouteMaxScroll =
                    0;

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

            recipeProductionRouteScroll =
                    0.0D;

            recipeProductionRouteMaxScroll =
                    0;

            return;
        }

        List<CraftScopeProductionRouteTreeModel.Row> rows =
                getProductionRouteTreeRows();

        int contentHeight =
                rows.size()
                        * rowHeight;

        recipeProductionRouteMaxScroll =
                Math.max(
                        0,
                        contentHeight
                                - viewportHeight
                );

        clampRecipeProductionRouteScroll();

        graphics.enableScissor(
                left + 2,
                rowTop,
                right - 2,
                viewportBottom
        );

        int y =
                rowTop
                        - (int) recipeProductionRouteScroll;

        for (int rowIndex = 0;
             rowIndex < rows.size();
             rowIndex++) {

            CraftScopeProductionRouteTreeModel.Row row =
                    rows.get(
                            rowIndex
                    );

            int rowY =
                    y
                            + rowIndex
                            * rowHeight;

            if (rowY + rowHeight < rowTop
                    || rowY > viewportBottom) {

                continue;
            }

            if (row.isSource()) {
                String arrow =
                        row.expanded()
                                ? "-"
                                : "+";

                graphics.drawString(
                        font,
                        arrow,
                        left + 8,
                        rowY + 6,
                        row.containsSelected()
                                ? CraftScopeUiTheme.ACCENT
                                : CraftScopeUiTheme.TEXT_SECONDARY
                );

                String groupLabel =
                        row.sourceName()
                                + " ("
                                + row.processCount()
                                + ")";

                graphics.drawString(
                        font,
                        fitText(
                                groupLabel,
                                right - left - 28
                        ),
                        left + 20,
                        rowY + 6,
                        row.containsSelected()
                                ? CraftScopeUiTheme.TEXT_PRIMARY
                                : CraftScopeUiTheme.TEXT_SECONDARY
                );

                continue;
            }

            boolean selected =
                    row.selected();

            if (selected) {
                graphics.fill(
                        left + 12,
                        rowY,
                        right - 5,
                        rowY + rowHeight - 2,
                        CraftScopeUiTheme.ACCENT_BACKGROUND
                );

                CraftScopeUiTheme.drawBorder(
                        graphics,
                        left + 12,
                        rowY,
                        right - 5,
                        rowY + rowHeight - 2,
                        CraftScopeUiTheme.ACCENT
                );
            }

            String label =
                    fitText(
                            getProductionRouteTreeChildLabel(
                                    row
                            ),
                            right - left - 34
                    );

            graphics.drawString(
                    font,
                    label,
                    left + 20,
                    rowY + 6,
                    selected
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
            );
        }

        graphics.disableScissor();

        if (recipeProductionRouteMaxScroll > 0) {
            int barX =
                    right - 5;

            graphics.fill(
                    barX,
                    rowTop,
                    barX + 3,
                    viewportBottom,
                    CraftScopeUiTheme.BORDER_SUBTLE
            );

            int thumbHeight =
                    Math.max(
                            12,
                            viewportHeight
                                    * viewportHeight
                                    / Math.max(
                                    1,
                                    contentHeight
                            )
                    );

            int travel =
                    Math.max(
                            0,
                            viewportHeight
                                    - thumbHeight
                    );

            int thumbOffset =
                    recipeProductionRouteMaxScroll <= 0
                            ? 0
                            : (int) (
                            (
                                    recipeProductionRouteScroll
                                            / recipeProductionRouteMaxScroll
                            )
                                    * travel
                    );

            graphics.fill(
                    barX,
                    rowTop + thumbOffset,
                    barX + 3,
                    rowTop
                            + thumbOffset
                            + thumbHeight,
                    CraftScopeUiTheme.TEXT_MUTED
            );
        }
    }    private int renderRecipeNode(
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

            renderIngredientVariantSelector(
                    graphics,
                    node,
                    nodePath,
                    iconX,
                    rowY,
                    text
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
                        + 8
                        + getIngredientVariantSelectorAdvance(
                        node
                );

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
                layout.summaryHeight(),
                true
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
                        + CraftScopeProductionDisplayPolicy
                        .formatExecutionCount(
                                displayRoute,
                                summary.runs()
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
        renderProcessSummaryBar(
                graphics,
                top,
                height,
                false
        );
    }

    private void renderProcessSummaryBar(
            GuiGraphics graphics,
            int top,
            int height,
            boolean perExecution
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

        boolean singleExecution =
                perExecution
                        && CraftScopeProductionDisplayPolicy
                        .isSingleExecutionRoute(
                                displayRoute
                        );

        CraftScopeProductionSummary summary =
                CraftScopeProductionSummary.summarize(
                        displayRoute,
                        project.getTargetCount(),
                        singleExecution
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

        /*
         * Component-aware resources must render their exact ItemStack
         * rather than being reconstructed from the registry ID.
         *
         * This preserves potion contents, Ominous Bottle amplifier,
         * and future component-bearing modded items in the Process
         * Diagram.
         */
        if (resource.hasItemIdentity()) {

            ItemStack identityStack =
                    resource.createDisplayStack();

            if (!identityStack.isEmpty()) {

                return identityStack;
            }
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
        if (node.hasSelectableIngredientAlternatives()
                && node.hasExplicitIngredientVariantSelection()) {

            return node.getStack();
        }

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
        if (node.hasSelectableIngredientAlternatives()) {

            if (node.hasExplicitIngredientVariantSelection()) {

                return node
                        .getStack()
                        .getHoverName()
                        .getString();
            }

            return getIngredientAlternativeSummary(
                    node.getAcceptedVariants()
            );
        }

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

    private void expandAllRecipeNodes() {
        if (currentTree == null
                || currentTree.getRoot() == null) {

            return;
        }

        expandedNodes.clear();

        expandAllRecipeNodes(
                currentTree.getRoot(),
                "root"
        );
    }

    private void expandAllRecipeNodes(
            CraftScopeRecipeNode node,
            String nodePath
    ) {
        if (node == null
                || node.getChildren().isEmpty()) {

            return;
        }

        expandedNodes.add(
                nodePath
        );

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

            expandAllRecipeNodes(
                    child,
                    childPath
            );
        }
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
                && handleIngredientChoicePopupClick(
                mouseX,
                mouseY
        )) {
            return true;
        }

        if (activeView == ViewMode.RECIPE_TREE
                && button == 0
                && handleRecipeProductionRouteClick(
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

    private boolean handleRecipeProductionRouteClick(
            double mouseX,
            double mouseY
    ) {
        if (productionRoutes.isEmpty()) {
            return false;
        }

        int left =
                getContentLeft();

        int right =
                left
                        + getRecipeRoutePanelWidth();

        int top =
                CONTENT_TITLE_Y - 4;

        int bottom =
                getWindowBottom()
                        - CONTENT_BOTTOM_MARGIN;

        int rowTop =
                top + 25;

        int rowHeight =
                22;

        int viewportBottom =
                Math.max(
                        rowTop,
                        bottom - 4
                );

        if (mouseX < left + 4
                || mouseX >= right - 4
                || mouseY < rowTop
                || mouseY >= viewportBottom) {

            return false;
        }

        List<CraftScopeProductionRouteTreeModel.Row> rows =
                getProductionRouteTreeRows();

        int clickedRowIndex =
                (int) (
                        (
                                mouseY
                                        - rowTop
                                        + recipeProductionRouteScroll
                        )
                                / rowHeight
                );

        if (clickedRowIndex < 0
                || clickedRowIndex
                >= rows.size()) {

            return false;
        }

        CraftScopeProductionRouteTreeModel.Row row =
                rows.get(
                        clickedRowIndex
                );

        if (row.isSource()) {
            toggleProductionRouteSource(
                    row.sourceId()
            );

            return true;
        }


        if (!row.isRoute()
                || row.processId() == null) {

            return false;
        }

        selectProductionProcessContext(
                row.sourceId(),
                row.processId(),
                row.routeIndex()
        );

        return true;
    }    private boolean handleTreeClick(
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

        int treePanelLeft =
                getRecipeTreePanelLeft();

        int treeCenterX =
                treePanelLeft
                        + (
                        getContentRight()
                                - treePanelLeft
                ) / 2;

        int treeLeft =
                Math.max(
                        treePanelLeft + 12,
                        treeCenterX - 140
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
        int indent = depth * TREE_INDENT;
        int arrowX = treeLeft + indent;
        int iconX = arrowX + 12;

        String itemText =
                getNodeDisplayName(node)
                        + " x"
                        + node.getRequiredCount();

        if (isIngredientVariantSelectorClicked(
                node,
                nodePath,
                iconX,
                rowY,
                itemText,
                mouseX,
                mouseY
        )) {

            if (nodePath.equals(
                    ingredientChoicePopupPath
            )) {

                ingredientChoicePopupPath =
                        null;

            } else {

                ingredientChoicePopupPath =
                        nodePath;
            }

            return new ClickResult(
                    true,
                    rowY + CONTENT_ROW_HEIGHT
            );
        }

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

    private void renderIngredientVariantSelector(
            GuiGraphics graphics,
            CraftScopeRecipeNode node,
            String nodePath,
            int iconX,
            int rowY,
            String itemText
    ) {
        if (node == null
                || !node.hasSelectableIngredientAlternatives()) {

            return;
        }

        String selectorText =
                node.hasExplicitIngredientVariantSelection()
                        ? "[Change]"
                        : "[Choose]";

        int selectorX =
                getIngredientVariantSelectorX(
                        node,
                        nodePath,
                        iconX,
                        itemText,
                        selectorText
                );

        graphics.drawString(
                font,
                selectorText,
                selectorX,
                rowY + 4,
                CraftScopeUiTheme.ACCENT
        );

        if (nodePath.equals(
                ingredientChoicePopupPath
        )) {

            ingredientChoicePopupNode =
                    node;

            ingredientChoicePopupAnchorVisible =
                    true;

            ingredientChoicePopupAnchorX =
                    selectorX;

            ingredientChoicePopupAnchorY =
                    rowY + CONTENT_ROW_HEIGHT;
        }
    }

    private int getIngredientVariantSelectorAdvance(
            CraftScopeRecipeNode node
    ) {
        if (node == null
                || !node.hasSelectableIngredientAlternatives()) {

            return 0;
        }

        String text =
                node.hasExplicitIngredientVariantSelection()
                        ? "[Change]"
                        : "[Choose]";

        return font.width(
                text
        ) + 6;
    }

    private int getIngredientVariantSelectorX(
            CraftScopeRecipeNode node,
            String nodePath,
            int iconX,
            String itemText,
            String selectorText
    ) {
        int desiredX =
                iconX
                        + 20
                        + font.width(itemText)
                        + 8;

        int reserve =
                0;

        List<ResourceLocation> recipes =
                recipeChoices.get(
                        nodePath
                );

        if (recipes != null
                && recipes.size() > 1
                && node.getPreferredRecipeId() != null) {

            int currentIndex =
                    getCurrentRecipeIndex(
                            nodePath,
                            node
                    );

            String recipeText =
                    "["
                            + (currentIndex + 1)
                            + "/"
                            + recipes.size()
                            + "]";

            reserve =
                    font.width(
                            recipeText
                    ) + 6;
        }

        int maxX =
                getContentRight()
                        - font.width(
                        selectorText
                )
                        - reserve
                        - 8;

        return Math.min(
                desiredX,
                maxX
        );
    }

    private boolean isIngredientVariantSelectorClicked(
            CraftScopeRecipeNode node,
            String nodePath,
            int iconX,
            int rowY,
            String itemText,
            double mouseX,
            double mouseY
    ) {
        if (node == null
                || !node.hasSelectableIngredientAlternatives()) {

            return false;
        }

        String selectorText =
                node.hasExplicitIngredientVariantSelection()
                        ? "[Change]"
                        : "[Choose]";

        int selectorX =
                getIngredientVariantSelectorX(
                        node,
                        nodePath,
                        iconX,
                        itemText,
                        selectorText
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
                < rowY + CONTENT_ROW_HEIGHT;
    }

    private void renderIngredientChoicePopup(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        if (ingredientChoicePopupPath == null
                || ingredientChoicePopupNode == null
                || !ingredientChoicePopupAnchorVisible) {

            ingredientChoicePopupLeft =
                    0;

            ingredientChoicePopupTop =
                    0;

            ingredientChoicePopupRight =
                    0;

            ingredientChoicePopupBottom =
                    0;

            return;
        }

        List<ItemStack> variants =
                ingredientChoicePopupNode
                        .getAcceptedVariants();

        if (variants.isEmpty()) {
            return;
        }

        int popupWidth =
                120;

        for (ItemStack variant :
                variants) {

            popupWidth =
                    Math.max(
                            popupWidth,
                            font.width(
                                    variant
                                            .getHoverName()
                                            .getString()
                            )
                                    + 38
                    );
        }

        popupWidth =
                Math.min(
                        230,
                        popupWidth
                );

        int popupHeight =
                variants.size()
                        * ingredientChoicePopupRowHeight
                        + 4;

        int treeLeft =
                getRecipeTreePanelLeft()
                        + 4;

        int treeRight =
                getContentRight()
                        - 4;

        int popupLeft =
                Math.max(
                        treeLeft,
                        Math.min(
                                ingredientChoicePopupAnchorX,
                                treeRight - popupWidth
                        )
                );

        int popupTop =
                ingredientChoicePopupAnchorY;

        if (popupTop + popupHeight
                > getViewportBottom()) {

            popupTop =
                    ingredientChoicePopupAnchorY
                            - CONTENT_ROW_HEIGHT
                            - popupHeight;
        }

        popupTop =
                Math.max(
                        CONTENT_VIEWPORT_TOP,
                        popupTop
                );

        int popupRight =
                popupLeft + popupWidth;

        int popupBottom =
                popupTop + popupHeight;

        ingredientChoicePopupLeft =
                popupLeft;

        ingredientChoicePopupTop =
                popupTop;

        ingredientChoicePopupRight =
                popupRight;

        ingredientChoicePopupBottom =
                popupBottom;

        CraftScopeUiTheme.drawPanel(
                graphics,
                popupLeft,
                popupTop,
                popupRight,
                popupBottom
        );

        CraftScopeUiTheme.drawBorder(
                graphics,
                popupLeft,
                popupTop,
                popupRight,
                popupBottom,
                CraftScopeUiTheme.BORDER_HOVER
        );

        ItemStack selectedStack =
                ingredientChoicePopupNode
                        .getStack();

        int rowTop =
                popupTop + 2;

        for (int i = 0;
             i < variants.size();
             i++) {

            ItemStack variant =
                    variants.get(i);

            int optionTop =
                    rowTop
                            + i
                            * ingredientChoicePopupRowHeight;

            int optionBottom =
                    optionTop
                            + ingredientChoicePopupRowHeight;

            boolean selected =
                    ingredientChoicePopupNode
                            .hasExplicitIngredientVariantSelection()
                            && ItemStack.isSameItemSameComponents(
                            variant,
                            selectedStack
                    );

            boolean hovered =
                    mouseX >= popupLeft + 2
                            && mouseX < popupRight - 2
                            && mouseY >= optionTop
                            && mouseY < optionBottom;

            if (selected) {
                graphics.fill(
                        popupLeft + 2,
                        optionTop,
                        popupRight - 2,
                        optionBottom,
                        CraftScopeUiTheme.ACCENT_BACKGROUND
                );

            } else if (hovered) {

                graphics.fill(
                        popupLeft + 2,
                        optionTop,
                        popupRight - 2,
                        optionBottom,
                        CraftScopeUiTheme.BUTTON_HOVER
                );
            }

            graphics.renderItem(
                    variant,
                    popupLeft + 6,
                    optionTop + 3
            );

            graphics.drawString(
                    font,
                    fitText(
                            variant
                                    .getHoverName()
                                    .getString(),
                            popupRight
                                    - popupLeft
                                    - 34
                    ),
                    popupLeft + 28,
                    optionTop + 7,
                    selected
                            ? CraftScopeUiTheme.TEXT_PRIMARY
                            : CraftScopeUiTheme.TEXT_SECONDARY
            );
        }
    }

    private boolean handleIngredientChoicePopupClick(
            double mouseX,
            double mouseY
    ) {
        if (ingredientChoicePopupPath == null
                || ingredientChoicePopupNode == null
                || ingredientChoicePopupRight
                <= ingredientChoicePopupLeft
                || ingredientChoicePopupBottom
                <= ingredientChoicePopupTop) {

            return false;
        }

        if (mouseX < ingredientChoicePopupLeft
                || mouseX >= ingredientChoicePopupRight
                || mouseY < ingredientChoicePopupTop
                || mouseY >= ingredientChoicePopupBottom) {

            /*
             * Close the popup but allow the ordinary Recipe Tree
             * click handler to process the click underneath.
             */
            ingredientChoicePopupPath =
                    null;

            ingredientChoicePopupNode =
                    null;

            return false;
        }

        int index =
                (int) (
                        (
                                mouseY
                                        - ingredientChoicePopupTop
                                        - 2
                        )
                                / ingredientChoicePopupRowHeight
                );

        List<ItemStack> variants =
                ingredientChoicePopupNode
                        .getAcceptedVariants();

        if (index < 0
                || index >= variants.size()) {

            return true;
        }

        ItemStack selected =
                variants.get(
                        index
                );

        ResourceLocation selectedId =
                BuiltInRegistries.ITEM.getKey(
                        selected.getItem()
                );

        String selectedPath =
                ingredientChoicePopupPath;

        project.setIngredientVariantOverride(
                selectedPath,
                selectedId.toString()
        );

        /*
         * A different ingredient strategy invalidates every recipe
         * choice below this node, but not the selected ingredient
         * itself.
         */
        clearDescendantRecipeState(
                selectedPath
        );

        expandedNodes.add(
                selectedPath
        );

        ingredientChoicePopupPath =
                null;

        ingredientChoicePopupNode =
                null;

        CraftScopeProjectManager.save();

        rebuildTree();

        return true;
    }

    private String getIngredientAlternativeSummary(
            List<ItemStack> variants
    ) {
        if (variants == null
                || variants.isEmpty()) {

            return "Choose Ingredient";
        }

        if (variants.size() == 1) {

            return variants
                    .getFirst()
                    .getHoverName()
                    .getString();
        }

        if (variants.size() == 2) {

            return variants
                    .get(0)
                    .getHoverName()
                    .getString()
                    + " OR "
                    + variants
                    .get(1)
                    .getHoverName()
                    .getString();
        }

        return "Choose Ingredient ("
                + variants.size()
                + " options)";
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

        /*
         * A recipe choice can replace an entire downstream branch.
         * Re-open the rebuilt tree so the player can immediately see
         * what changed (for example Cardboard -> Leather).
         */
        expandAllRecipeNodes();
        clampTreeScroll();
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

        List<String> savedIngredientDescendants =
                new ArrayList<>();

        for (String key :
                project
                        .getIngredientVariantOverrides()
                        .keySet()) {

            if (key.startsWith(
                    prefix
            )) {

                savedIngredientDescendants.add(
                        key
                );
            }
        }

        for (String key :
                savedIngredientDescendants) {

            project.removeIngredientVariantOverride(
                    key
            );
        }

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
        if (activeView == ViewMode.RECIPE_TREE
                && isMouseOverRecipeProductionRoutePanel(
                mouseX,
                mouseY
        )) {

            if (recipeProductionRouteMaxScroll > 0) {
                recipeProductionRouteScroll -=
                        scrollY
                                * SCROLL_AMOUNT;

                clampRecipeProductionRouteScroll();
            }

            return true;
        }

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

        /*
         * Prefer the complete serialized ItemStack so data-component
         * identity survives project save/load.
         *
         * Old projects have no serialized stack and fall through to
         * the original ResourceLocation-only behavior below.
         */
        if (minecraft != null
                && minecraft.level != null) {

            ItemStack storedStack =
                    CraftScopeItemStackPersistence.decode(
                            project.getTargetItemStackJson(),
                            minecraft
                                    .level
                                    .registryAccess()
                    );

            if (!storedStack.isEmpty()) {

                ResourceLocation storedId =
                        BuiltInRegistries.ITEM.getKey(
                                storedStack.getItem()
                        );

                if (itemId.equals(
                        storedId.toString()
                )) {

                    return storedStack;
                }
            }
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
        ItemStack pathStack =
                child.getStack();

        List<ItemStack> accepted =
                child.getAcceptedVariants();

        /*
         * Keep node paths stable when a selectable ingredient changes
         * from Cardboard to Leather.
         */
        if (accepted != null
                && !accepted.isEmpty()) {

            pathStack =
                    accepted
                            .getFirst()
                            .copy();
        }

        return parentPath
                + "/"
                + childIndex
                + ":"
                + getItemId(
                pathStack
        );
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

        project.setTargetItemId(
                itemId.toString()
        );

        if (minecraft != null
                && minecraft.level != null) {

            project.setTargetItemStackJson(
                    CraftScopeItemStackPersistence.encode(
                            stack,
                            minecraft
                                    .level
                                    .registryAccess()
                    )
            );
        }

        project.clearRecipeOverrides();
        project.clearIngredientVariantOverrides();
        project.clearProductionProcessSelection();

        expandedNodes.clear();
        recipeOverrides.clear();
        recipeChoices.clear();
        selectedMethodIndices.clear();

        selectedProductionRouteIndex = -1;
        selectedDiagramNodeIndex = -1;

        expandedProductionRouteSources.clear();
        recipeProductionRouteScroll = 0.0D;
        recipeProductionRouteMaxScroll = 0;

        selectedProductionProcessSourceId = null;
        selectedProductionProcessId = null;
        productionProcessOptions = List.of();

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
