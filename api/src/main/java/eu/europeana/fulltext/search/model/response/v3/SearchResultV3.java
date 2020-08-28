package eu.europeana.fulltext.search.model.response.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import eu.europeana.fulltext.api.model.v3.AnnotationV3;
import eu.europeana.fulltext.api.service.EDM2IIIFMapping;
import eu.europeana.fulltext.entity.AnnoPage;
import eu.europeana.fulltext.entity.Annotation;
import eu.europeana.fulltext.search.model.response.Debug;
import eu.europeana.fulltext.search.model.response.Hit;
import eu.europeana.fulltext.search.model.response.SearchResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Root object for serializing search response
 *
 * @author Patrick Ehlert
 * Created on 2 June 2020
 */
@JsonPropertyOrder({"id", "type", "debug", "items", "hits"})
public class SearchResultV3 implements Serializable, SearchResult {

    private static final long serialVersionUID = -5643549600050178321L;

    private static final String TYPE = "AnnotationPage";

    private String id;
    private Debug debug;
    private List<AnnotationV3> items = new ArrayList<>();
    private List<Hit> hits = new ArrayList<>();

    public SearchResultV3(String searchId, boolean debug) {
        this.id = searchId;
        if (debug) {
            this.debug = new Debug();
        }
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return SearchResultV3.TYPE;
    }

    /**
     * @return object containing information for debugging (only available if user requested debug parameter)
     */
    public Debug getDebug() {
        return debug;
    }

    @Override
    public int itemSize() {
        return items.size();
    }

    public List<AnnotationV3> getItems() {
        return items;
    }

    /**
     * @return List of found hits (only available for Block and Line level annotations)
     */
    public List<Hit> getHits() {
        return hits;
    }

    /**
     * Add an Annotation and optionally a Hit to the search result
     *
     * @param annoPage,  the annotation page where the hit was found
     * @param annotation the annotation that matches/overlaps with the hit
     * @param hit        the found hit (can be null for word-level annotation search)
     */
    public void addAnnotationHit(AnnoPage annoPage, Annotation annotation, Hit hit) {
        AnnotationV3 annoV3 = EDM2IIIFMapping.getAnnotationV3(annoPage, annotation, false, false);
        items.add(annoV3);

        if (hit != null) {
            hits.add(hit);
        }
    }
}
