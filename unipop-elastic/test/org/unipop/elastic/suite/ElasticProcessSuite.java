package org.unipop.elastic.suite;

import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.junit.runner.RunWith;
import org.unipop.structure.UniGraph;
import org.unipop.test.UnipopProcessSuite;
import test.ElasticGraphProvider;

@RunWith(UnipopProcessSuite.class)
@GraphProviderClass(provider = ElasticGraphProvider.class, graph = UniGraph.class)
public class ElasticProcessSuite {
}