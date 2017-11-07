package test;

import org.apache.commons.io.FileUtils;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
//import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils.asList;

public class LocalNode {
    private final Node node;
    private final Client client;

    public LocalNode(File dataPath) throws NodeValidationException {
        try {
            FileUtils.deleteDirectory(dataPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
//
//        Settings.Builder elasticsearchSettings = Settings.settingsBuilder()
//                .put("path.data", dataPath)
//                .put("path.home", "./data/")
//                .put("index.number_of_shards", "1")
//                .put("index.number_of_replicas", "0")
//                .put("discovery.zen.ping.multicast.enabled", "false")
//                .put("script.inline", "true")
//                .put("script.indexed", "true")
//                .put("script.update", "true")
//                .put("script.groovy.sandbox.enabled", "true");
//
//        this.node = NodeBuilder.nodeBuilder()
//                .local(true)
//                .settings(elasticsearchSettings.build())
//                .node();

        this.node = elasticSearchTestNode(dataPath);

        this.client = node.client();
        checkHealth();
    }

    public void checkHealth() {
        final ClusterHealthRequest clusterHealthRequest = new ClusterHealthRequest().timeout(TimeValue.timeValueSeconds(10)).waitForYellowStatus();
        final ClusterHealthResponse clusterHealth = client.admin().cluster().health(clusterHealthRequest).actionGet();
        if (clusterHealth.isTimedOut()) {
            System.out.println(clusterHealth.getStatus() +
                    " status returned from cluster '" + client.admin().cluster().toString());

        }
    }

    public DeleteIndexResponse deleteIndices() {
        return client.admin().indices().prepareDelete("*").execute().actionGet();
    }

    public Client getClient() {
        return client;
    }

    public Node getNode() {
        return node;
    }
    public Node elasticSearchTestNode(File dataPath) throws NodeValidationException {
        Node node = new MyNode(
                Settings.builder()
                        .put("transport.type", "netty4")
                        .put("http.type", "netty4")
                        .put("http.enabled", "true")
                        //  .put("path.home", "elasticsearch-data")
                        .put("path.data", dataPath)
                        .put("path.home", "./data/")
//                        .put("index.number_of_shards", "1")
//                        .put("index.number_of_replicas", "0")
//                        .put("discovery.zen.ping.multicast.enabled", "false")
//                        .put("script.inline", "true")
//                        .put("script.indexed", "true")
//                        .put("script.update", "true")
//                        .put("script.groovy.sandbox.enabled", "true")
                        .build(),
                asList(Netty4Plugin.class));
        node.start();
        return node;
    }


    private static class MyNode extends Node {
        public MyNode(Settings preparedSettings, Collection<Class<? extends Plugin>> classpathPlugins) {
            super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null), classpathPlugins);
        }
    }
}
