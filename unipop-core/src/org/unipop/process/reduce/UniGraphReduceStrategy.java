package org.unipop.process.reduce;

import com.google.common.collect.Sets;
import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.ChooseStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.LocalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.DedupGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.RangeGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.WhereTraversalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.*;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.StoreStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.EmptyStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.ReducingBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.DefaultTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.unipop.process.edge.EdgeStepsStrategy;
import org.unipop.process.properties.PropertyFetcher;
import org.unipop.process.properties.UniGraphPropertiesStrategy;
import org.unipop.process.graph.UniGraphStep;
import org.unipop.process.graph.UniGraphStepStrategy;
import org.unipop.process.vertex.UniGraphVertexStep;
import org.unipop.query.aggregation.ReduceQuery;
import org.unipop.query.aggregation.ReduceVertexQuery;
import org.unipop.query.search.SearchQuery;
import org.unipop.query.search.SearchVertexQuery;
import org.unipop.structure.UniGraph;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by sbarzilay on 8/29/16.
 */
public class UniGraphReduceStrategy extends AbstractTraversalStrategy<TraversalStrategy.ProviderOptimizationStrategy> implements TraversalStrategy.ProviderOptimizationStrategy {
    @Override
    public void apply(Traversal.Admin<?, ?> traversal) {
        // TODO: when vertex step return vertex before the ReducingBarrierStep should do something
        if (TraversalHelper.hasStepOfAssignableClass(RangeGlobalStep.class, traversal)) return;
        if (traversal.getParent().asStep() instanceof DedupGlobalStep) return;
        if (traversal.getParent().asStep() instanceof LocalStep) return;
        if (traversal.getParent().asStep() instanceof ChooseStep) return;
        if (traversal.getParent().asStep() instanceof OrderGlobalStep) return;
        if (traversal.getParent().asStep() instanceof ProjectStep) return;
        if (traversal.getParent().asStep() instanceof GroupStep) return;
        if (traversal.getParent().asStep() instanceof GroupCountStep) return;
        if (traversal.getParent().asStep() instanceof StoreStep) return;
        // TODO: remove the above conditions once LocalStrategy is implemented
        boolean reduced = applyForVertexStep(traversal);
        if (!reduced)
            applyForGraphStep(traversal);
    }

    private boolean applyForVertexStep(Traversal.Admin<?, ?> traversal) {
        boolean[] change = {false};
        TraversalHelper.getStepsOfAssignableClass(CountGlobalStep.class, traversal).forEach(countGlobalStep -> {
            String reduceOn = getReduceOn(countGlobalStep, traversal);
            change[0] = setUpReduceVertexStep(countGlobalStep, ReduceQuery.ReduceOperator.Count, reduceOn, traversal);
        });

        TraversalHelper.getStepsOfAssignableClass(SumGlobalStep.class, traversal).forEach(sumGlobalStep -> {
            String reduceOn = getReduceOn(sumGlobalStep, traversal);
            if (reduceOn != null)
                change[0] = setUpReduceVertexStep(sumGlobalStep, ReduceQuery.ReduceOperator.Sum, reduceOn, traversal);

        });

        TraversalHelper.getStepsOfAssignableClass(MaxGlobalStep.class, traversal).forEach(maxGlobalStep -> {
            String reduceOn = getReduceOn(maxGlobalStep, traversal);
            if (reduceOn != null)
                change[0] = setUpReduceVertexStep(maxGlobalStep, ReduceQuery.ReduceOperator.Max, reduceOn, traversal);
        });

        TraversalHelper.getStepsOfAssignableClass(MinGlobalStep.class, traversal).forEach(minGlobalStep -> {
            String reduceOn = getReduceOn(minGlobalStep, traversal);
            if (reduceOn != null)
                change[0] = setUpReduceVertexStep(minGlobalStep, ReduceQuery.ReduceOperator.Min, reduceOn, traversal);
        });

        TraversalHelper.getStepsOfAssignableClass(MeanGlobalStep.class, traversal).forEach(meanGlobalStep -> {
            String reduceOn = getReduceOn(meanGlobalStep, traversal);
            if (reduceOn != null)
                change[0] = setUpReduceVertexStep(meanGlobalStep, ReduceQuery.ReduceOperator.Mean, reduceOn, traversal);
        });
        return change[0];
    }

    private void applyForGraphStep(Traversal.Admin traversal) {
        TraversalHelper.getStepsOfAssignableClass(CountGlobalStep.class, traversal).forEach(countGlobalStep -> {
            String reduceOn = getReduceOn(countGlobalStep, traversal);
            setUpReduceStep(countGlobalStep, ReduceQuery.ReduceOperator.Count, reduceOn, traversal);
        });

        TraversalHelper.getStepsOfAssignableClass(SumGlobalStep.class, traversal).forEach(sumGlobalStep -> {
            String reduceOn = getReduceOn(sumGlobalStep, traversal);
            if (reduceOn != null)
                setUpReduceStep(sumGlobalStep, ReduceQuery.ReduceOperator.Sum, reduceOn, traversal);
        });

        TraversalHelper.getStepsOfAssignableClass(MaxGlobalStep.class, traversal).forEach(maxGlobalStep -> {
            String reduceOn = getReduceOn(maxGlobalStep, traversal);
            if (reduceOn != null)
                setUpReduceStep(maxGlobalStep, ReduceQuery.ReduceOperator.Max, reduceOn, traversal);
        });

        TraversalHelper.getStepsOfAssignableClass(MinGlobalStep.class, traversal).forEach(minGlobalStep -> {
            String reduceOn = getReduceOn(minGlobalStep, traversal);
            if (reduceOn != null)
                setUpReduceStep(minGlobalStep, ReduceQuery.ReduceOperator.Min, reduceOn, traversal);
        });

        // TODO: think on how to implement mean with bulks
//        TraversalHelper.getStepsOfAssignableClass(MeanGlobalStep.class, traversal).forEach(meanGlobalStep -> {
//            String reduceOn = getReduceOn(meanGlobalStep, traversal);
//            if (reduceOn != null)
//                setUpReduceStep(meanGlobalStep, ReduceQuery.ReduceOperator.Mean, reduceOn, traversal);
//        });
    }

    private String getReduceOn(ReducingBarrierStep reduceStep, Traversal.Admin traversal) {
        Collection<PropertiesStep> propertiesStepStepOf = getPropertiesStepStepOf(reduceStep, traversal);
        if (propertiesStepStepOf != null && propertiesStepStepOf.size() == 1) {
            PropertiesStep propertiesStep = propertiesStepStepOf.iterator().next();
            String[] propertyKeys = propertiesStep.getPropertyKeys();
            if (propertyKeys.length == 1)
                return propertyKeys[0];
        }
        return null;
    }

    private boolean setUpReduceVertexStep(ReducingBarrierStep reduceStep, ReduceQuery.ReduceOperator op, String reduceOn, Traversal.Admin traversal) {
        Collection<UniGraphVertexStep> vertexStepStepOf = getVertexStepStepOf(reduceStep, traversal);
        if (vertexStepStepOf != null && vertexStepStepOf.size() == 1) {
            UniGraphVertexStep uniGraphVertexStep = vertexStepStepOf.iterator().next();
            if (uniGraphVertexStep.isReturnsVertex()) return true;
            int index = TraversalHelper.stepIndex(uniGraphVertexStep, traversal);
            if (index == -1) return false;
            DefaultTraversal reduceTraversal = new DefaultTraversal<>();
            TraversalHelper.removeToTraversal(uniGraphVertexStep, reduceStep, reduceTraversal);
            UniGraph uniGraph = (UniGraph) traversal.getGraph().get();
            List<ReduceVertexQuery.ReduceVertexController> reduceControllers = uniGraph.getControllerManager()
                    .getControllers(ReduceVertexQuery.ReduceVertexController.class);
            reduceTraversal.asAdmin().addStep(reduceStep);

            traversal.removeStep(TraversalHelper.stepIndex(reduceStep, traversal));

            uniGraphVertexStep.setControllers(uniGraph.getControllerManager()
                    .getControllers(SearchVertexQuery.SearchVertexController.class)
                    .stream().filter(controller -> !reduceControllers.contains(controller))
                    .collect(Collectors.toList()));

            UniGraphVertexReduceStep uniGraphReduceStep = new UniGraphVertexReduceStep(uniGraphVertexStep.isReturnsVertex(), uniGraphVertexStep.getDirection(), uniGraphVertexStep.getPredicates(), uniGraphVertexStep.getKeys(), reduceOn, -1, reduceControllers, Edge.class, reduceStep.getBiOperator(), reduceStep.getSeedSupplier(), op, reduceTraversal, traversal, uniGraph);
            traversal.addStep(index, uniGraphReduceStep);
            return true;
        }
        return false;
    }

    private void setUpReduceStep(ReducingBarrierStep reduceStep, ReduceQuery.ReduceOperator op, String reduceOn, Traversal.Admin traversal) {
        Collection<UniGraphStep> graphStepStepOf = getGraphStepStepOf(reduceStep, traversal);
        if (graphStepStepOf != null && graphStepStepOf.size() == 1) {
            UniGraphStep<?, ?> UniGraphStep = graphStepStepOf.iterator().next();
            if (UniGraphStep.returnsEdge()){
                Step next = UniGraphStep.getNextStep();
                while(next != reduceStep){
                    if (next instanceof PropertyFetcher)
                        return;
                    next = next.getNextStep();
                }
            }
            DefaultTraversal reduceTraversal = new DefaultTraversal<>();
            TraversalHelper.removeToTraversal(UniGraphStep, reduceStep, reduceTraversal);
            UniGraph uniGraph = (UniGraph) traversal.getGraph().get();
            List<ReduceQuery.ReduceController> reduceControllers = uniGraph.getControllerManager()
                    .getControllers(ReduceQuery.ReduceController.class);
            reduceTraversal.asAdmin().addStep(reduceStep);

            traversal.removeStep(TraversalHelper.stepIndex(reduceStep, traversal));

            UniGraphStep.setControllers(uniGraph.getControllerManager()
                    .getControllers(SearchQuery.SearchController.class)
                    .stream().filter(controller -> !reduceControllers.contains(controller))
                    .collect(Collectors.toList()));

            UniGraphReduceStep uniGraphReduceStep = new UniGraphReduceStep(UniGraphStep.getPredicates(), UniGraphStep.getKeys(), reduceOn, UniGraphStep.getLimit(), reduceControllers, UniGraphStep.getReturnClass(), reduceStep.getBiOperator(), reduceStep.getSeedSupplier(), op, reduceTraversal, traversal, uniGraph);
            traversal.addStep(0, uniGraphReduceStep);
        }
    }

    @Override
    public Set<Class<? extends ProviderOptimizationStrategy>> applyPrior() {
        return Sets.newHashSet(UniGraphStepStrategy.class, UniGraphPropertiesStrategy.class, EdgeStepsStrategy.class);
    }

    private Collection<UniGraphVertexStep> getVertexStepStepOf(Step step, Traversal.Admin<?, ?> traversal) {
        Step previous = step.getPreviousStep();
        while (!(previous instanceof UniGraphVertexStep)) {
            if (previous instanceof DedupGlobalStep || previous instanceof OrderGlobalStep)
                previous = previous.getPreviousStep();
            else if (previous instanceof EmptyStep) {
                TraversalParent parent = traversal.getParent();
                List<UniGraphVertexStep> propertyFetchers = parent.getLocalChildren().stream()
                        .flatMap(child -> TraversalHelper.getStepsOfAssignableClassRecursively(UniGraphVertexStep.class, child)
                                .stream()).collect(Collectors.toList());
                if (propertyFetchers.size() > 0)
                    previous = propertyFetchers.get(propertyFetchers.size() - 1);
                else
                    return null;
            } else if (previous instanceof TraversalParent) {
                List<UniGraphVertexStep> uniGraphVertexSteps = Stream.concat(
                        ((TraversalParent) previous).getLocalChildren().stream(),
                        ((TraversalParent) previous).getGlobalChildren().stream())
                        .flatMap(child ->
                                TraversalHelper.getStepsOfAssignableClassRecursively(UniGraphVertexStep.class, child)
                                        .stream()).collect(Collectors.toList());
                if (uniGraphVertexSteps.size() > 0)
                    return uniGraphVertexSteps;
                else
                    return null;
            } else if (previous instanceof WhereTraversalStep.WhereStartStep)
                return null;
            else
                previous = previous.getPreviousStep();
        }
        return Collections.singleton((UniGraphVertexStep) previous);
    }

    private Collection<UniGraphStep> getGraphStepStepOf(Step step, Traversal.Admin<?, ?> traversal) {
        Step previous = step.getPreviousStep();
        while (!(previous instanceof UniGraphStep)) {
            if (previous instanceof DedupGlobalStep || previous instanceof OrderGlobalStep)
                previous = previous.getPreviousStep();
            else if (previous instanceof EmptyStep) {
                TraversalParent parent = traversal.getParent();
                List<UniGraphStep> propertyFetchers = parent.getLocalChildren().stream()
                        .flatMap(child -> TraversalHelper.getStepsOfAssignableClassRecursively(UniGraphStep.class, child)
                                .stream()).collect(Collectors.toList());
                if (propertyFetchers.size() > 0)
                    previous = propertyFetchers.get(propertyFetchers.size() - 1);
                else
                    return null;
            } else if (previous instanceof TraversalParent) {
                List<UniGraphStep> UniGraphSteps = Stream.concat(
                        ((TraversalParent) previous).getLocalChildren().stream(),
                        ((TraversalParent) previous).getGlobalChildren().stream())
                        .flatMap(child ->
                                TraversalHelper.getStepsOfAssignableClassRecursively(UniGraphStep.class, child)
                                        .stream()).collect(Collectors.toList());
                if (UniGraphSteps.size() > 0)
                    return UniGraphSteps;
                else
                    return null;
            } else if (previous instanceof WhereTraversalStep.WhereStartStep)
                return null;
            else
                previous = previous.getPreviousStep();
        }
        return Collections.singleton((UniGraphStep) previous);
    }

    private Collection<PropertiesStep> getPropertiesStepStepOf(Step step, Traversal.Admin<?, ?> traversal) {
        Step previous = step.getPreviousStep();
        while (!(previous instanceof PropertiesStep)) {
            if (previous instanceof DedupGlobalStep || previous instanceof OrderGlobalStep)
                previous = previous.getPreviousStep();
            else if (previous instanceof EmptyStep) {
                TraversalParent parent = traversal.getParent();
                List<PropertiesStep> propertyFetchers = parent.getLocalChildren().stream()
                        .flatMap(child -> TraversalHelper.getStepsOfAssignableClassRecursively(PropertiesStep.class, child)
                                .stream()).collect(Collectors.toList());
                if (propertyFetchers.size() > 0)
                    previous = propertyFetchers.get(propertyFetchers.size() - 1);
                else
                    return null;
            } else if (previous instanceof TraversalParent) {
                List<PropertiesStep> propertiesSteps = Stream.concat(
                        ((TraversalParent) previous).getLocalChildren().stream(),
                        ((TraversalParent) previous).getGlobalChildren().stream())
                        .flatMap(child ->
                                TraversalHelper.getStepsOfAssignableClassRecursively(PropertiesStep.class, child)
                                        .stream()).collect(Collectors.toList());
                if (propertiesSteps.size() > 0)
                    return propertiesSteps;
                else
                    return null;
            } else if (previous instanceof WhereTraversalStep.WhereStartStep)
                return null;
            else
                previous = previous.getPreviousStep();
        }
        return Collections.singleton((PropertiesStep) previous);
    }
}
