package eu.europeana.fulltext.search.config;

import org.apache.logging.log4j.LogManager;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;

import java.util.*;

/**
 * Search configuration, including connection to Solr and the Solr repository
 *
 * @author Patrick Ehlert
 * Created on 28 May 2020
 */
@Configuration
@EnableSolrRepositories(basePackages={"eu.europeana.fulltext.search.repository"})
public class SearchConfig {

    public static final int MAXIMUM_HITS = 100;

    public static final String HIT_TAG_START = "<em>";
    public static final String HIT_TAG_END = "</em>";

    // try to merge two hits that are away a maximum of this much characters
    public static final int HIT_MERGE_MAX_DISTANCE = 3;

    @Value("${spring.data.solr.zk-host:}")
    private String zookeeperHosts;
    @Value("${spring.data.solr.host:}")
    private String solrHost;
    @Value("${spring.data.solr.core:}")
    private String solrCore;
    @Value("${spring.data.solr.hl.maxAnalyzedChars:}")
    private Integer hlMaxAnalyzeChars;

    // TODO set timeouts!?

    /**
     * Create a new SolrClient that connects via Zookeeper
     * @return
     */
    @Bean
    public SolrClient solrClient() {
        if (zookeeperHosts.isBlank() || zookeeperHosts.toUpperCase(Locale.GERMAN).contains("REMOVED")) {
            LogManager.getLogger(SearchConfig.class).info("No zookeeper configured, trying to connect to standalone server");
            return new HttpSolrClient.Builder(solrHost).build();
        }
        List<String> zkHosts = Arrays.asList(zookeeperHosts.split(","));
        CloudSolrClient client = new CloudSolrClient.Builder(zkHosts, Optional.empty()).build();
        client.setDefaultCollection(solrCore);
        return client;
    }

    @Bean
    public SolrTemplate solrTemplate(SolrClient solrClient) {
        return new SolrTemplate(solrClient);
    }

}

