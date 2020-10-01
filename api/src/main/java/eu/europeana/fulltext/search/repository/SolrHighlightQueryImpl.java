package eu.europeana.fulltext.search.repository;

import eu.europeana.fulltext.api.service.exception.FTException;
import eu.europeana.fulltext.search.config.SearchConfig;
import eu.europeana.fulltext.search.model.query.EuropeanaId;
import eu.europeana.fulltext.search.model.response.Debug;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.solr.core.SolrTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Defines the query sent to solr to retrieve highlights in a particular newspaper issue (record)
 *
 * @author Patrick Ehlert
 * Created 29 May 2020
 */
public class SolrHighlightQueryImpl implements SolrHighlightQuery {

    private static final Logger LOG = LogManager.getLogger(SolrHighlightQueryImpl.class);

    private static final String HL_QUERY = "hl.q";
    private static final String HL_FIELDS = "hl.fl";
    private static final String HL_EXTENDED_PARAM = "hl.extended";
    private static final String HL_METHOD_PARAM = "hl.method";
    private static final String HL_MAXANALYZEDCHARS_PARAM = "hl.maxAnalyzedChars";
    private static final String EUROPEANA_ID_FIELD = "europeana_id";

    @Autowired
    private SolrTemplate solrTemplate;

    @Value("${spring.data.solr.core:}")
    private String solrCore;
    @Value("${spring.data.solr.hl.maxAnalyzedChars:}")
    private Integer maxAnalyzedChars;

    /**
     * @see SolrHighlightQuery#getHighlightsWithOffsets(EuropeanaId, String, int, Debug)
     */
    public Map<String, List<String>> getHighlightsWithOffsets(EuropeanaId europeanaId, String query, int maxSnippets,
                                                              Debug debug) throws FTException {
        SolrQuery q = createQuery(europeanaId, query, maxSnippets);
        if (debug != null) {
            debug.setSolrQuery(q.toQueryString());
        }

        // do query
        QueryResponse response;
        try {
            response = solrTemplate.getSolrClient().query(solrCore, createQuery(europeanaId, query, maxSnippets));
            if (LOG.isTraceEnabled()) {
                LOG.trace("Solr response = {}", response.jsonStr());
            }
        } catch (SolrServerException | IOException e) {
            throw new FTException("Error querying Solr", e);
        }

        // process results
        SolrDocumentList list = response.getResults();
        if (list.getNumFound() == 0) {
            return null;
        }
        Map<String, Map<String, List<String>>> highlights = response.getHighlighting();
        return highlights.get(europeanaId.toString()); // should only be 1 item
    }

    private SolrQuery createQuery(EuropeanaId europeanaId, String query, int maxSnippets) {
        SolrQuery sq = new SolrQuery();
        sq.setQuery(EUROPEANA_ID_FIELD + ":" + ClientUtils.escapeQueryChars(europeanaId.toString()));
        sq.setRows(1);  // we expect 1 issue to return anyway
        sq.setTimeAllowed(SearchConfig.QUERY_TIME_ALLOWED);
        sq.setFields(EUROPEANA_ID_FIELD); // just 1 field, so we limit the amount of data that is returned

        sq.setHighlight(true)
                .setHighlightSnippets(maxSnippets)
                .setHighlightFragsize(0) // we need to entire fragment because that includes the imageId
                .setHighlightSimplePre(SearchConfig.HIT_TAG_START)
                .setHighlightSimplePost(SearchConfig.HIT_TAG_END)
                .set(HL_EXTENDED_PARAM, "true")
                .set(HL_METHOD_PARAM, "unified")
                .set(HL_QUERY, ClientUtils.escapeQueryChars(query))
                // TODO temporarily request to set to fulltext.fr as our current test instance only supports that
                .set(HL_FIELDS, "fulltext.fr");
        if (maxAnalyzedChars != null) {
            sq.set(HL_MAXANALYZEDCHARS_PARAM, String.valueOf(maxAnalyzedChars));
        }
        return sq;
    }

}
