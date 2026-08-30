package io.github.gatiger.craftscope.production;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
 * Structural graph view of a CraftScopeProductionRoute.
 *
 * CraftScopeProductionRoute intentionally keeps its existing ordered
 * step list. That is useful for summaries, Setup, persistence, and
 * compatibility with current providers.
 *
 * This class adds a graph interpretation on top of that list.
 *
 * Example:
 *
 *     Step 0 ── Iron Ingot ──┐
 *                            │
 *                            ├─> Step 2
 *                            │
 *     Step 1 ── Stick ───────┘
 *
 * The graph is inferred from actual resource flow:
 *
 *     producer output
 *          ->
 *     later consumer input
 *
 * No provider needs to manually create graph edges.
 */
public final class CraftScopeProductionGraph {

    private final CraftScopeProductionRoute route;

    private final List<StepNode> steps;

    private final List<Edge> edges;

    private final List<ExternalInput> externalInputs;

    private final Map<Integer, List<Edge>> incomingEdges;

    private final Map<Integer, List<Edge>> outgoingEdges;

    private CraftScopeProductionGraph(
            CraftScopeProductionRoute route,
            List<StepNode> steps,
            List<Edge> edges,
            List<ExternalInput> externalInputs
    ) {
        this.route =
                Objects.requireNonNull(
                        route,
                        "route"
                );

        this.steps =
                List.copyOf(
                        steps
                );

        this.edges =
                List.copyOf(
                        edges
                );

        this.externalInputs =
                List.copyOf(
                        externalInputs
                );

        this.incomingEdges =
                buildEdgeMap(
                        this.edges,
                        true
                );

        this.outgoingEdges =
                buildEdgeMap(
                        this.edges,
                        false
                );
    }

    public static CraftScopeProductionGraph fromRoute(
            CraftScopeProductionRoute route
    ) {
        Objects.requireNonNull(
                route,
                "route"
        );

        List<StepNode> nodes =
                new ArrayList<>();

        List<Edge> edges =
                new ArrayList<>();

        List<ExternalInput> externalInputs =
                new ArrayList<>();

        for (int i = 0;
             i < route.steps().size();
             i++) {

            CraftScopeProductionStep step =
                    route.steps().get(
                            i
                    );

            nodes.add(
                    new StepNode(
                            i,
                            step
                    )
            );

            for (int inputIndex = 0;
                 inputIndex < step.inputs().size();
                 inputIndex++) {

                CraftScopeResourceAmount input =
                        step.inputs().get(
                                inputIndex
                        );

                ProducerMatch producer =
                        findNearestProducer(
                                route,
                                i,
                                input
                        );

                if (producer == null) {

                    externalInputs.add(
                            new ExternalInput(
                                    i,
                                    inputIndex,
                                    input
                            )
                    );

                    continue;
                }

                edges.add(
                        new Edge(
                                producer.stepIndex(),
                                i,
                                producer.outputIndex(),
                                inputIndex,
                                producer.output(),
                                input
                        )
                );
            }
        }

        return new CraftScopeProductionGraph(
                route,
                nodes,
                edges,
                externalInputs
        );
    }

    public CraftScopeProductionRoute route() {
        return route;
    }

    public List<StepNode> steps() {
        return steps;
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<ExternalInput> externalInputs() {
        return externalInputs;
    }

    public List<Edge> incomingEdges(
            int stepIndex
    ) {
        return incomingEdges.getOrDefault(
                stepIndex,
                List.of()
        );
    }

    public List<Edge> outgoingEdges(
            int stepIndex
    ) {
        return outgoingEdges.getOrDefault(
                stepIndex,
                List.of()
        );
    }

    /*
     * True when one production step receives produced resources from
     * two or more different upstream steps.
     */
    public boolean hasBranchingInputs() {
        for (int i = 0;
             i < steps.size();
             i++) {

            Set<Integer> producers =
                    new LinkedHashSet<>();

            for (Edge edge :
                    incomingEdges(
                            i
                    )) {

                producers.add(
                        edge.producerStepIndex()
                );
            }

            if (producers.size() > 1) {
                return true;
            }
        }

        return false;
    }

    /*
     * Steps with no produced-resource dependency.
     *
     * They may still have external inputs.
     */
    public List<StepNode> rootSteps() {
        List<StepNode> result =
                new ArrayList<>();

        for (StepNode node :
                steps) {

            if (incomingEdges(
                    node.index()
            ).isEmpty()) {

                result.add(
                        node
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    /*
     * Steps whose outputs do not feed another production step.
     */
    public List<StepNode> terminalSteps() {
        List<StepNode> result =
                new ArrayList<>();

        for (StepNode node :
                steps) {

            if (outgoingEdges(
                    node.index()
            ).isEmpty()) {

                result.add(
                        node
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    private static ProducerMatch findNearestProducer(
            CraftScopeProductionRoute route,
            int consumerStepIndex,
            CraftScopeResourceAmount input
    ) {
        if (input == null
                || consumerStepIndex <= 0) {

            return null;
        }

        /*
         * Search backward so that if the same resource is transformed
         * several times, the consumer connects to the nearest previous
         * producer rather than an older unrelated step.
         */
        for (int stepIndex =
                     consumerStepIndex - 1;
             stepIndex >= 0;
             stepIndex--) {

            CraftScopeProductionStep producerStep =
                    route.steps().get(
                            stepIndex
                    );

            for (int outputIndex = 0;
                 outputIndex < producerStep.outputs().size();
                 outputIndex++) {

                CraftScopeResourceAmount output =
                        producerStep.outputs().get(
                                outputIndex
                        );

                if (!resourcesMatch(
                        output,
                        input
                )) {

                    continue;
                }

                return new ProducerMatch(
                        stepIndex,
                        outputIndex,
                        output
                );
            }
        }

        return null;
    }

    /*
     * Resource-flow identity matching.
     *
     * Probability, range, expected yield and consumed state do not
     * define resource identity. They describe HOW MUCH of the resource
     * is involved.
     *
     * Component-bearing items remain exact identities.
     */
    public static boolean resourcesMatch(
            CraftScopeResourceAmount produced,
            CraftScopeResourceAmount consumed
    ) {
        if (produced == null
                || consumed == null
                || produced.kind()
                != consumed.kind()) {

            return false;
        }

        if (!Objects.equals(
                produced.unit(),
                consumed.unit()
        )) {

            return false;
        }

        if (produced.kind()
                == CraftScopeResourceKind.ITEM
                && (
                produced.hasCustomItemComponents()
                        || consumed.hasCustomItemComponents()
        )) {

            return produced.itemIdentity() != null
                    && consumed.itemIdentity() != null
                    && produced
                    .itemIdentity()
                    .equals(
                            consumed.itemIdentity()
                    );
        }

        for (ResourceLocation producedId :
                produced.acceptedVariantIds()) {

            if (producedId == null) {
                continue;
            }

            if (consumed
                    .acceptedVariantIds()
                    .contains(
                            producedId
                    )) {

                return true;
            }
        }

        /*
         * Defensive fallback for a provider whose accepted variants
         * do not include its representative ID.
         */
        return produced.id() != null
                && consumed.id() != null
                && produced.id().equals(
                consumed.id()
        );
    }

    private static Map<Integer, List<Edge>> buildEdgeMap(
            List<Edge> edges,
            boolean incoming
    ) {
        Map<Integer, List<Edge>> mutable =
                new LinkedHashMap<>();

        for (Edge edge :
                edges) {

            int key =
                    incoming
                            ? edge.consumerStepIndex()
                            : edge.producerStepIndex();

            mutable
                    .computeIfAbsent(
                            key,
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            edge
                    );
        }

        Map<Integer, List<Edge>> result =
                new LinkedHashMap<>();

        for (Map.Entry<Integer, List<Edge>> entry :
                mutable.entrySet()) {

            result.put(
                    entry.getKey(),
                    List.copyOf(
                            entry.getValue()
                    )
            );
        }

        return Collections.unmodifiableMap(
                result
        );
    }

    private record ProducerMatch(
            int stepIndex,
            int outputIndex,
            CraftScopeResourceAmount output
    ) {
    }

    public record StepNode(
            int index,
            CraftScopeProductionStep step
    ) {
        public StepNode {
            if (index < 0) {

                throw new IllegalArgumentException(
                        "Step index cannot be negative"
                );
            }

            Objects.requireNonNull(
                    step,
                    "step"
            );
        }
    }

    public record Edge(
            int producerStepIndex,
            int consumerStepIndex,
            int producerOutputIndex,
            int consumerInputIndex,
            CraftScopeResourceAmount producedResource,
            CraftScopeResourceAmount consumedResource
    ) {
        public Edge {
            if (producerStepIndex < 0
                    || consumerStepIndex < 0
                    || producerOutputIndex < 0
                    || consumerInputIndex < 0) {

                throw new IllegalArgumentException(
                        "Production graph indexes cannot be negative"
                );
            }

            if (producerStepIndex >= consumerStepIndex) {

                throw new IllegalArgumentException(
                        "Production graph must be topologically ordered"
                );
            }

            Objects.requireNonNull(
                    producedResource,
                    "producedResource"
            );

            Objects.requireNonNull(
                    consumedResource,
                    "consumedResource"
            );
        }
    }

    public record ExternalInput(
            int consumerStepIndex,
            int consumerInputIndex,
            CraftScopeResourceAmount resource
    ) {
        public ExternalInput {
            if (consumerStepIndex < 0
                    || consumerInputIndex < 0) {

                throw new IllegalArgumentException(
                        "External-input indexes cannot be negative"
                );
            }

            Objects.requireNonNull(
                    resource,
                    "resource"
            );
        }
    }
}