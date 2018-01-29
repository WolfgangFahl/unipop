package org.unipop.elastic.document.schema;

import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.shaded.jackson.databind.JsonNode;
import org.apache.tinkerpop.shaded.jackson.databind.node.ArrayNode;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.javatuples.Pair;
import org.json.JSONException;
import org.json.JSONObject;
import org.unipop.elastic.common.ElasticClient;
import org.unipop.elastic.document.Document;
import org.unipop.elastic.document.DocumentEdgeSchema;
import org.unipop.query.UniQuery;
import org.unipop.query.VertexQuery;
import org.unipop.query.aggregation.LocalQuery;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.predicates.PredicatesHolderFactory;
import org.unipop.query.search.SearchVertexQuery;
import org.unipop.schema.property.AbstractPropertyContainer;
import org.unipop.structure.UniGraph;

import java.io.IOException;
import java.util.*;

/**
 * Created by sbarzilay on 9/14/16.
 */
public abstract class AbstractDocEdgeSchema extends AbstractDocSchema<Edge> implements DocumentEdgeSchema{
    public AbstractDocEdgeSchema(JSONObject configuration, ElasticClient client, UniGraph graph) throws JSONException {
        super(configuration, client, graph);
    }

    protected PredicatesHolder getVertexPredicates(List<Vertex> vertices, Direction direction){
        PredicatesHolder outPredicates = getOutVertexSchema().toPredicates(vertices);

        PredicatesHolder inPredicates = getInVertexSchema().toPredicates(vertices);

        if(direction.equals(Direction.OUT) && outPredicates.notAborted()) return outPredicates;
        if(direction.equals(Direction.IN) && inPredicates.notAborted()) return inPredicates;
        if (outPredicates.notAborted() && inPredicates.notAborted())
            return PredicatesHolderFactory.or(inPredicates, outPredicates);
        else if (outPredicates.isAborted()) return inPredicates;
        else if (inPredicates.isAborted()) return outPredicates;
        else return PredicatesHolderFactory.abort();
    }

    protected abstract AggregationBuilder createTerms(String name, AbstractAggregationBuilder subs, VertexQuery searchQuery, Direction direction, Iterator<String> fields);

    public QueryBuilder createQueryBuilder(SearchVertexQuery query) {
        PredicatesHolder edgePredicates = this.toPredicates(query.getPredicates());
        PredicatesHolder vertexPredicates = this.getVertexPredicates(query.getVertices(), query.getDirection());
        PredicatesHolder predicatesHolder = PredicatesHolderFactory.and(edgePredicates, vertexPredicates);
        if (predicatesHolder.isAborted()) return null;
        return createQueryBuilder(predicatesHolder);
    }

    @Override
    public List<AggregationBuilder> getLocal(LocalQuery query) {
        SearchVertexQuery searchQuery = (SearchVertexQuery) query.getSearchQuery();
        Iterator<String> fields;
        List<AggregationBuilder> aggs = new ArrayList<>();
        List<Pair<String, Order>> orders = searchQuery.getOrders();
        TopHitsAggregationBuilder hits = AggregationBuilders.topHits("hits").size(searchQuery.getLimit());
        if (searchQuery.getPropertyKeys() != null){
            Set<String> toFields = toFields(searchQuery.getPropertyKeys());
            hits.fetchSource(toFields.toArray(new String[toFields.size()]), new String[0]);
        }
        if (orders != null && orders.size() > 0){
            orders.forEach(order -> {
                SortOrder sort = order.getValue1().equals(Order.incr) ? SortOrder.ASC : SortOrder.DESC;
                FieldSortBuilder order1 = SortBuilders.fieldSort(getFieldByPropertyKey(order.getValue0())).order(sort);
            });
        }
        if (searchQuery.getDirection().equals(Direction.OUT) || searchQuery.getDirection().equals(Direction.BOTH)) {
            fields = ((AbstractPropertyContainer) getOutVertexSchema()).getPropertySchemas().stream()
                    .filter(schema -> schema.getKey().equals(T.id.getAccessor()))
                    .findFirst().get().toFields(Collections.emptySet()).iterator();
            AggregationBuilder out = createTerms("out", getSubAggregation(query.getSearchQuery(), hits, Direction.OUT), searchQuery, Direction.OUT, fields);
            aggs.add(out);
        }
        if(searchQuery.getDirection().equals(Direction.IN) || searchQuery.getDirection().equals(Direction.BOTH)){
            fields = ((AbstractPropertyContainer) getInVertexSchema()).getPropertySchemas().stream()
                    .filter(schema -> schema.getKey().equals(T.id.getAccessor()))
                    .findFirst().get().toFields(Collections.emptySet()).iterator();
            AggregationBuilder in = createTerms("in", getSubAggregation(query.getSearchQuery(), hits, Direction.IN), searchQuery, Direction.IN, fields);
            aggs.add(in);
        }
        return aggs;
    }

    abstract protected AbstractAggregationBuilder getSubAggregation(UniQuery query, AbstractAggregationBuilder builder, Direction direction);

    protected List<Pair<String, Element>> parseTerms(String path, String bottomPath, String name, LocalQuery query, String result, Set<String> fields){
        SearchVertexQuery searchQuery = (SearchVertexQuery) query.getSearchQuery();
        try {
            JsonNode jsonNode = mapper.readTree(result);
            String[] split = path.split("\\.");
            for (String s : split) {
                jsonNode = jsonNode.get(s);
            }
            for (int i = 1; i < fields.size() + 1; i++) {
                jsonNode = jsonNode.get(name + "_id");
            }
            ArrayNode buckets = (ArrayNode) jsonNode.get("buckets");
            ArrayList<Pair<String, Element>> objects = new ArrayList<>();
            for (JsonNode node : buckets) {
                JsonNode parse = null;
                String[] bottomSplit = bottomPath.split("\\.");
                for (String s : bottomSplit) {
                    if(parse == null)
                        parse = node.get(s);
                    else
                        parse = parse.get(s);
                }
                ArrayNode hits = (ArrayNode) parse;
                        //node.get("filter").get("hits").get("hits").withArray("hits");
                for (JsonNode hit : hits) {
                    Collection<Edge> edges = fromDocument(
                            new Document(hit.get("_index").asText(),
                                    hit.get("_type").asText(),
                                    hit.get("_id").asText(),
                                    mapper.readValue(hit.get("_source").toString(), Map.class)));
                    if (edges != null){
                        edges.forEach(edge -> {
                            String key = null;
                            if (name.equals("out"))
                                key = edge.outVertex().id().toString();
                            else
                                key = edge.inVertex().id().toString();
                            if (query.getQueryClass().equals(Edge.class))
                                objects.add(Pair.with(key, edge));
                            else{
                                if (searchQuery.getDirection().equals(Direction.OUT))
                                    objects.add(Pair.with(key, edge.inVertex()));
                                else if (searchQuery.getDirection().equals(Direction.IN))
                                    objects.add(Pair.with(key, edge.outVertex()));
                                else{
                                    objects.add(Pair.with(key, edge.inVertex()));
                                    objects.add(Pair.with(key, edge.outVertex()));
                                }
                            }
                        });
                    }
                }
            }
            return objects;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }
}
