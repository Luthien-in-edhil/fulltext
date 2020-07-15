package eu.europeana.fulltext.search.service;

import eu.europeana.fulltext.AnnotationType;
import eu.europeana.fulltext.api.service.FTService;
import eu.europeana.fulltext.api.service.exception.FTException;
import eu.europeana.fulltext.entity.AnnoPage;
import eu.europeana.fulltext.entity.Annotation;
import eu.europeana.fulltext.search.config.SearchConfig;
import eu.europeana.fulltext.search.exception.RecordDoesNotExistException;
import eu.europeana.fulltext.search.model.query.EuropeanaId;
import eu.europeana.fulltext.search.model.query.SolrHit;
import eu.europeana.fulltext.search.model.query.SolrNewspaper;
import eu.europeana.fulltext.search.model.response.Debug;
import eu.europeana.fulltext.search.model.response.Hit;
import eu.europeana.fulltext.search.model.response.SearchResult;
import eu.europeana.fulltext.search.repository.SolrNewspaperRepo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.solr.core.query.result.HighlightEntry;
import org.springframework.data.solr.core.query.result.HighlightPage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Service for querying solr, retrieving fulltext data and sending back results
 *
 * @author Patrick Ehlert
 * Created on 28 May 2020
 */
@Lazy
@Service
public class FTSearchService {

    private static final Logger LOG  = LogManager.getLogger(FTSearchService.class);

    private SolrNewspaperRepo solrRepo;
    private FTService fulltextRepo;

    FTSearchService(SolrNewspaperRepo solrRepo, FTService fulltextService){
        this.solrRepo = solrRepo;
        this.fulltextRepo = fulltextService;
    }

    /**
     * Searches fulltext for one particular newspaper issue (CHO)
     * @param searchId string that is set as id of the search (endpoint, path and query parameters)
     * @param europeanaId europeana id of the issue to search
     * @param query the string to search
     * @param pageSize maximum number of hits
     * @param annotationType requested types of annotations
     * @param debug if true we include debug information
     * @throws FTException when there is a problem processing the request (e.g. issue doesn't exist)
     * @return SearchResult object (can be empty if no hits were found)
     */
    public SearchResult searchIssue(String searchId, EuropeanaId europeanaId, String query, int pageSize, AnnotationType annotationType,
                                    boolean debug) throws FTException {
        long start = System.currentTimeMillis();
        HighlightPage<SolrNewspaper> solrResult = solrRepo.findByEuropeanaIdAndQuery(europeanaId, query, pageSize);

        SearchResult result = new SearchResult(searchId, debug);
        if (solrResult.isEmpty()) {
            LOG.debug("Solr return empty result");
            // check if there are 0 hits because the record doesn't exist
            if (!fulltextRepo.doesAnnoPageExist(europeanaId.getDatasetId(), europeanaId.getLocalId(), "1")) {
                throw new RecordDoesNotExistException(europeanaId);
            }
        } else {
            LOG.debug("Solr returned {} document", solrResult.getSize());
            findHitsInIssue(result, solrResult, europeanaId, pageSize, annotationType, result.getDebug());
        }
        LOG.debug("Search done in {} ms. Found {} annotations", (System.currentTimeMillis() - start), result.getItems().size());
        return result;
    }

    /**
     * We use all the strings found by Solr as keywords to do a search in a record/newspaper issue to find the correct
     * annopages and annotations.
     */
    private SearchResult findHitsInIssue(SearchResult result, HighlightPage<SolrNewspaper> solrResult,
                                         EuropeanaId europeanaId, int pageSize, AnnotationType annoType, Debug debug) {
        Collection<SolrHit> hitsToFind = getHitsFromSolrSnippets(solrResult, debug);

        // TODO tmp hack to get things working: for now we have to search through all annopages to get the right fulltext
        long start = System.currentTimeMillis();
        List<AnnoPage> pages = fulltextRepo.fetchAnnoPages(europeanaId.getDatasetId(), europeanaId.getLocalId());
        LOG.debug("Retrieved {} annopages in {} ms", pages.size(), System.currentTimeMillis() - start);

        for (AnnoPage annoPage : pages) {
            // check each fulltext if we can find our keywords (solrhits)
            LOG.trace("Searching through page {}, fulltext = {}", annoPage.getPgId(), annoPage.getRes().getId());
            for (SolrHit hitToFind : hitsToFind) {
                // check how many hits we still need until we have the maximum
                int maxHits = pageSize - result.getHits().size();
                if (maxHits <= 0) {
                    return result;
                }
                // find more hits and corresponding annotations
                List<Hit> hitsFound = findHitInFullText(hitToFind, annoPage, maxHits);
                for (Hit hit : hitsFound) {
                    findAnnotation(result, hit, annoPage, annoType);
                }
            }
        }
        return result;
    }

    /**
     * Use the Solr snippets to create a set of unique hits. We use those to search in Mongo fulltexts ourselves,
     * to find the correct annopage(s) and annotations
     */
    private Collection<SolrHit> getHitsFromSolrSnippets(HighlightPage<SolrNewspaper> solrResult, Debug debug) {
        // we use a hashmap where the string is the hit in String representation, so we can quickly see if we already
        // have the same hit or not
        List<SolrHit> hits = new ArrayList<>();
        for (HighlightEntry<SolrNewspaper> content : solrResult.getHighlighted()) {
            LOG.debug("Record = {}", content.getEntity().europeanaId);

            for (HighlightEntry.Highlight highlight : content.getHighlights()) {
                for (String snippet : highlight.getSnipplets()) {
                    getHitsFromSolrSnippet(hits, snippet, debug);
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} distinct keywords: {} ", hits.size(), Arrays.toString(hits.toArray()));
        }
        return hits;
    }

    /**
     * Extracts all keyword(s) from a single solr snippet and stores that in SolrHit objects. We also retrieve 1
     * leading and 1 trailing character. We use those extra characters as word delimiters when searching in fulltext.
     * @param hitsFound list to which newly found hits will be added
     * @param snippet the snippet to search
     * @param debug object where debug information can be stored (optional)
     */
    void getHitsFromSolrSnippet(List<SolrHit> hitsFound, String snippet, Debug debug) {
        LOG.debug("  Processing snippet {}", snippet);
        if (debug != null) {
            debug.addSolrSnippet(snippet);
        }

        SolrHit previousHit = null;
        int startHlTag = snippet.indexOf(SearchConfig.HIT_TAG_START);
        while (startHlTag != -1) {
            int endKeyword = snippet.indexOf(SearchConfig.HIT_TAG_END, startHlTag);
            String exact = snippet.substring(startHlTag + SearchConfig.HIT_TAG_START.length(), endKeyword);
            // get character in front (if available)
            Character prefix = null;
            if (startHlTag > 0) {
                prefix = snippet.charAt(startHlTag - 1);
            }
            // get character after (if available)
            Character suffix = null;
            int endHlTag = endKeyword + SearchConfig.HIT_TAG_END.length();
            if (endHlTag < snippet.length()) {
                suffix = snippet.charAt(endHlTag);
            }
            SolrHit newHit = mergeIfNearby(hitsFound, snippet, previousHit,
                    new SolrHit(prefix, exact, suffix, startHlTag, endHlTag), debug);

            if (hitsFound.contains(newHit)) {
                LOG.debug("    Existing keyword = '{}'", newHit);
            } else {
                LOG.debug("    New keyword = '{}'", newHit);
                hitsFound.add(newHit);
                if (debug != null) {
                    debug.addKeyword(newHit);
                }
            }

            // prepare for next iteration
            previousHit = newHit;
            startHlTag = snippet.indexOf(SearchConfig.HIT_TAG_START, endKeyword);
        }
    }

    /**
     * Check if we can merge with a near-by previous found hit in the same snippet
     */
    private SolrHit mergeIfNearby(List<SolrHit> hitsFound, String snippet, SolrHit previousHit, SolrHit newHit, Debug debug) {
        if (previousHit != null && (newHit.getHlStartPos() - previousHit.getHlEndPos() <= SearchConfig.HIT_MERGE_MAX_DISTANCE)) {
            String mergedExact = previousHit.getExact() +
                    snippet.substring(previousHit.getHlEndPos(), newHit.getHlStartPos()) +
                    newHit.getExact();

            SolrHit mergedHit = new SolrHit(previousHit.getPrefix(), mergedExact, newHit.getSuffix(),
                    previousHit.getHlStartPos(), newHit.getHlEndPos());
            LOG.debug("    Merging keywords '{}' and '{}' into '{}'", previousHit, newHit, mergedHit);

            hitsFound.remove(previousHit);
            if (debug != null) {
                debug.removeKeyword(previousHit);
            }
            return mergedHit;
        }
        return newHit;
    }

    /**
     * We check if we can find one or more of the keywords/hits in the resource (fulltext) of a particular AnnoPage
     * If so we calculate the start and end index in the text and add it to the result list
     * Coordinates start with 0 and the end coordinate is inclusive (e.g. in "Hi there", the word Hi has coordinates 0,2)
     * @param hitToFind a HitSelector object that contains the exact string (plus 1 leading and trailing character) we
     *                  want to find
     * @param annoPage  the annoPage containing the fulltext we want to search through
     * @param maxHits  maximum number of hits
     * @return a list of hits, can be empty if there are no matches
     */
    List<Hit> findHitInFullText(SolrHit hitToFind, AnnoPage annoPage, int maxHits) {
        LOG.trace("Searching on page {} for Solr exact string '{}'", annoPage, hitToFind);
        List<Hit> result = new ArrayList<>();

        String resourceTxt = annoPage.getRes().getValue();

        int startIndex = -1;
        while ((startIndex = getHitStartInFulltext(hitToFind, startIndex, resourceTxt)) >= 0) {
            int endIndex = startIndex + hitToFind.getExact().length();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found hit '{}' on page {}, fulltext {}, word start = {}, end = {} -> '{}'",
                        hitToFind, annoPage.getPgId(), annoPage.getRes().getId(), startIndex, endIndex,
                        annoPage.getRes().getValue().substring(startIndex, endIndex));
            }
            result.add(new Hit(startIndex, endIndex, hitToFind.getExact()));
            if (result.size() >= maxHits) {
                LOG.trace("Stopping search because we have {} results", result.size());
                break;
            }

            startIndex = startIndex + hitToFind.getExact().length();
            LOG.trace("Continue search at index {}", startIndex);
        }
        return result;
    }

    /**
     *  Find the startIndex of a particular hit (the exact word, excluding any prefix) in the fulltext.
     *  NOTE: sometimes Solr will return a snippet where the hit starts immediately (so there is no prefix), even though
     *  the snippet is not the page start. So far I haven't seen Solr sending no snippet suffix, but for safety we assume
     *  this can happen
     */
    private int getHitStartInFulltext(SolrHit hitToFind, int startIndex, String fulltext) {
        if (StringUtils.isEmpty(hitToFind.getPrefix())) {
            return getHitNoPrefix(hitToFind, startIndex, fulltext);
        }

        if (StringUtils.isEmpty(hitToFind.getSuffix())) {
            return getHitNoSuffix(hitToFind, startIndex, fulltext);
        }

        int result = fulltext.indexOf(hitToFind.toString(), startIndex);
        if (result != -1) {
            return result + hitToFind.getPrefix().length();
        }
        return -1;
    }

    private int getHitNoPrefix(SolrHit hitToFind, int startIndex, String fulltext) {
        // hit could be at start of fulltext
        if (startIndex <= 0 && fulltext.startsWith(hitToFind.getExact() + hitToFind.getSuffix())) {
            return 0;
        }
        // or at start of a sentence (after a newline)
        int result = fulltext.indexOf('\n' + hitToFind.toString(), startIndex);
        if (result != -1) {
            return result + 1; // add 1 because of newline prefix
        }
        // or at start of a sentence part (after a space)
        result = fulltext.indexOf(" " + hitToFind.toString(), startIndex);
        if (result != -1) {
            return result + 1; // add 1 because of space prefix
        }
        return -1;
    }

    private int getHitNoSuffix(SolrHit hitToFind, int startIndex, String fulltext) {
        // hit could be end of sentence (before a newline)
        int result = fulltext.indexOf(hitToFind.toString() + '\n', startIndex);
        if (result != -1) {
            return result + hitToFind.getPrefix().length();
        }
        // or end of sentence part (before a space)
        result = fulltext.indexOf(hitToFind.toString() + " ", startIndex);
        if (result != -1 ) {
            return result + hitToFind.getPrefix().length();
        }
        // or at the end of a fulltext
        if (startIndex <= fulltext.length() - hitToFind.toString().length()
                && fulltext.endsWith(hitToFind.getPrefix() + hitToFind.getExact())) {
            return fulltext.length() - hitToFind.getExact().length();
        }
        return -1;
    }

    /**
     * Given a hit, we go over all Annotations of an AnnoPage to see which one(s) match. When we found one we use it to
     * generate the hit prefix and suffix and we add the annotation to the search result
     */
    void findAnnotation(SearchResult result, Hit hit, AnnoPage annoPage, AnnotationType annoType) {
        boolean annotationHitAdded = false;
        LOG.trace("Searching annotations for hit {},{}", hit.getStartIndex(), hit.getEndIndex());
        for (Annotation anno : annoPage.getAns()) {
            if (anno.getDcType() == annoType.getAbbreviation() &&
                    overlap(hit.getStartIndex(), hit.getEndIndex(), anno.getFrom(), anno.getTo())) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found overlap between hit {},{} '{}' and annotation {},{} '{}'",
                            hit.getStartIndex(), hit.getEndIndex(),
                            annoPage.getRes().getValue().substring(hit.getStartIndex(), hit.getEndIndex()),
                            anno.getFrom(), anno.getTo(),
                            annoPage.getRes().getValue().substring(anno.getFrom(), anno.getTo()));
                }
                // Sometimes a trailing character like a dot or comma directly after the keyword is regarded as
                // another annotation (word). So we filter those out.
                if (anno.getTo() - anno.getFrom() > 1) {
                    if (AnnotationType.WORD.equals(annoType)) {
                        // don't output hit information for word-level annotations
                        result.addAnnotationHit(annoPage, anno, null);
                    } else if (annotationHitAdded) {
                        // make sure we add a hit only once to the search result, even if it has multiple annotations
                        hit.addAnnotation(annoPage, anno);
                        result.addAnnotationHit(annoPage, anno, null);
                    } else {
                        // first annotation for this hit, added both to result
                        hit.addAnnotation(annoPage, anno);
                        result.addAnnotationHit(annoPage, anno, hit);
                    }
                    annotationHitAdded = true;
                } else {
                    LOG.debug("Ignoring overlap with annotation {} because it's only 1 character long", anno.getAnId());
                }
            }
        }
        if (!annotationHitAdded) {
            LOG.warn("Could not find any annotation for hit {} on /{}/{}/annopage/{}",
                    hit, annoPage.getDsId(), annoPage.getLcId(), annoPage.getPgId());
        }
    }

    /**
     * Checks if there is an overlap between 2 start and end indexes.
     * Implementation based on https://stackoverflow.com/a/36035369
     *
     * Note that we can't use this for page-level annotations at the moment because those don't have a to and from
     * coordinate
     * @param s1 start index1
     * @param e1 end index1
     * @param s2 start index2
     * @param e2 end index2
     * @return true if there is overlap, otherwise false
     */
    private boolean overlap(int s1, int e1, int s2, int e2) {
        return (s1 <= e2 && e1 >= s2);
    }

}