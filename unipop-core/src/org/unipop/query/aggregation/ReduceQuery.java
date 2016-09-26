package org.unipop.query.aggregation;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.StepDescriptor;
import org.unipop.query.controller.UniQueryController;
import org.unipop.query.predicates.PredicateQuery;

public class ReduceQuery extends PredicateQuery {

    public ReduceQuery(PredicatesHolder predicates, StepDescriptor stepDescriptor, Traversal traversal) {
        super(predicates, stepDescriptor, traversal);
    }

    public interface SearchController<E extends Element> extends UniQueryController {
        long count(ReduceQuery uniQuery);
    }
}
