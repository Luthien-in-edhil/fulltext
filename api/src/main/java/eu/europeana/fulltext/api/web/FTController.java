package eu.europeana.fulltext.api.web;

import eu.europeana.api.commons.error.EuropeanaApiException;
import eu.europeana.fulltext.AnnotationType;
import eu.europeana.fulltext.api.model.info.SummaryManifest;
import eu.europeana.fulltext.api.model.AnnotationWrapper;
import eu.europeana.fulltext.api.model.FTResource;
import eu.europeana.fulltext.api.service.CacheUtils;
import eu.europeana.fulltext.api.service.ControllerUtils;
import eu.europeana.fulltext.api.service.FTService;
import eu.europeana.fulltext.api.service.exception.SerializationException;
import eu.europeana.fulltext.entity.AnnoPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;

import static eu.europeana.fulltext.RequestUtils.*;
import static eu.europeana.fulltext.api.config.FTDefinitions.*;
import static eu.europeana.fulltext.api.service.CacheUtils.generateETag;
import static eu.europeana.fulltext.api.service.CacheUtils.generateSimpleETag;

/**
 * Rest controller that handles fulltext annotation page (annopage)- annotation- & resource requests
 *
 * @author Lúthien
 * Created on 27-02-2018
 * Note that the eTag for the Fulltext response is created from a concatenation of:
 * - datasetId + localId + pageId / AnnoId;
 * - modified date (toString()) of the fetched document (not for the resource)
 * - the requested IIIF version (2 or 3) (not for the resource); and the
 * - Fulltext API version as defined in the pom.xml
 */
@RestController
@Api(tags = {"Full-text item"},
     description = "Retrieve a page with annotations, an individual annotation or a full-text")
@RequestMapping("/presentation")
public class FTController {

    private static final Set<AnnotationType> ALLOWED_ANNOTATION_TYPES = EnumSet.of(AnnotationType.PAGE,
                                                                                   AnnotationType.BLOCK,
                                                                                   AnnotationType.LINE,
                                                                                   AnnotationType.WORD,
                                                                                   AnnotationType.MEDIA,
                                                                                   AnnotationType.CAPTION);

    private static final Logger LOG = LogManager.getLogger(FTController.class);

    private FTService fts;

    public FTController(FTService ftService) {
        this.fts = ftService;
    }

    /**
     * Lists available AnnoPages for this record, including all translations
     *
     * @param datasetId identifier of the AnnoPage's dataset
     * @param localId   identifier of the AnnoPage's record
     * @return result String containing requested info
     * @throws EuropeanaApiException when serialising to Json fails
     */
    @ApiOperation(value = "Lists available Annotation Pages for a given EuropeanaID (dataset + localID), including translations")
    @GetMapping(value = "/{datasetId}/{localId}/annopage", headers = ACCEPT_JSON)
    public ResponseEntity<String> annoPageInfo(
            @PathVariable String datasetId,
            @PathVariable String localId,
            HttpServletRequest request) throws EuropeanaApiException {
       return getAnnoPageInfo(datasetId, localId, request);
    }

    private ResponseEntity<String> getAnnoPageInfo(String datasetId, String localId, HttpServletRequest request) throws EuropeanaApiException {
        SummaryManifest apInfo = fts.collectAnnoPageInfo(datasetId, localId);
        ZonedDateTime modified = CacheUtils.dateToZonedUTC(apInfo.getModified());
        String eTag = generateETag(datasetId + localId ,
                 modified,
                fts.getSettings().getAppVersion(),
                true);
        ResponseEntity<String> cached = CacheUtils.checkCached(request, modified, eTag);
        if (null != cached) {
            return cached;
        }
        HttpHeaders headers = CacheUtils.generateHeaders(request, eTag, CacheUtils.zonedDateTimeToString(modified));
        return new ResponseEntity<>(fts.serialise(apInfo), headers, HttpStatus.OK);
    }

    /**
     * Handles fetching the Annotation page (aka AnnoPage) with embedded annotations with boolean 'orig' == true
     *
     * @param datasetId       identifier of the AnnoPage's dataset
     * @param localId         identifier of the AnnoPage's record
     * @param pageId          identifier of the AnnoPage
     * @param lang            optional, in which language should the AnnoPage be
     * @param versionParam    optional, requested IIIF output format (2|3)
     * @param profile         optional, when value = 'text', resources are dereferenced
     * @param textGranularity optional, types of annotations that should be included (e.g. Block, Line, Page)
     * @return response in json format
     * @throws EuropeanaApiException when serialising to Json fails or an invalid parameter value is provided
     */
    @ApiOperation(value = "Retrieve a page with annotations")
    @GetMapping(value = "/{datasetId}/{localId}/annopage/{pageId}", headers = ACCEPT_JSON)
    public ResponseEntity<String> annoPageJson(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String pageId,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "format", required = false) String versionParam,
            @RequestParam(value = "profile", required = false) String profile,
            @RequestParam(value = "textGranularity", required = false) String textGranularity,
            HttpServletRequest request) throws EuropeanaApiException {
        return annoPage(datasetId, localId, pageId, lang, versionParam, profile, textGranularity, request, true);
    }

    /**
     * Handles fetching a page (resource) with all its annotations
     *
     * @param datasetId       identifier of the AnnoPage's dataset
     * @param localId         identifier of the AnnoPage's record
     * @param pageId          identifier of the AnnoPage
     * @param lang            optional, in which language should the AnnoPage be
     * @param versionParam    optional, requested IIIF output format (2|3)
     * @param profile         optional, when value = 'text', resources are dereferenced
     * @param textGranularity optional, specifies what annotations should be returned
     * @return response in json-ld format
     * @throws EuropeanaApiException when serialising to JsonLd fails or an invalid parameter value is provided
     */
    @ApiOperation(value = "Retrieve a page with annotations")
    @GetMapping(value = "/{datasetId}/{localId}/annopage/{pageId}", headers = ACCEPT_JSONLD)
    public ResponseEntity<String> annoPageJsonLd(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String pageId,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "format", required = false) String versionParam,
            @RequestParam(value = "profile", required = false) String profile,
            @RequestParam(value = "textGranularity", required = false) String textGranularity,
            HttpServletRequest request) throws EuropeanaApiException {
        return annoPage(datasetId, localId, pageId, lang, versionParam, profile, textGranularity, request, false);
    }

    private ResponseEntity<String> annoPage(
            String datasetId,
            String localId,
            String pageId,
            String lang,
            String versionParam,
            String profile,
            String textGranularity,
            HttpServletRequest request,
            boolean isJson) throws EuropeanaApiException {
        LOG.debug("Retrieve Annopage: {}/{}/{} with language {}", datasetId, localId, pageId, lang);
        String requestVersion = getRequestVersion(request, versionParam);
        if (ACCEPT_VERSION_INVALID.equals(requestVersion)) {
            return new ResponseEntity<>(ACCEPT_VERSION_INVALID, HttpStatus.NOT_ACCEPTABLE);
        }
        AnnotationWrapper annotationPage;
        HttpHeaders       headers;

        List<AnnotationType> textGranValues = ControllerUtils.validateTextGranularity(textGranularity,
                                                                                      ALLOWED_ANNOTATION_TYPES);
        AnnoPage      annoPage = fts.fetchAnnoPage(datasetId, localId, pageId, textGranValues, lang);
        ZonedDateTime modified = CacheUtils.dateToZonedUTC(annoPage.getModified());
        String eTag = generateETag(datasetId + localId + pageId,
                                   modified,
                                   requestVersion + fts.getSettings().getAppVersion(),
                                   true);
        ResponseEntity<String> cached = CacheUtils.checkCached(request, modified, eTag);
        if (null != cached) {
            return cached;
        }

        headers = CacheUtils.generateHeaders(request, eTag, CacheUtils.zonedDateTimeToString(modified));
        addContentTypeToResponseHeader(headers, requestVersion, isJson);

        if ("3".equalsIgnoreCase(requestVersion)) {
            annotationPage = fts.generateAnnoPageV3(annoPage, StringUtils.equalsAnyIgnoreCase(profile, PROFILE_TEXT));
        } else {
            annotationPage = fts.generateAnnoPageV2(annoPage, StringUtils.equalsAnyIgnoreCase(profile, PROFILE_TEXT));
        }

        if (isJson) {
            annotationPage.setContext(null);
        }
        return new ResponseEntity<>(fts.serialise(annotationPage), headers, HttpStatus.OK);
    }

    /**
     * HTTP Head endpoint to check for existence of an AnnoPage
     *
     * @param datasetId    identifier of the AnnoPage's dataset
     * @param localId      identifier of the AnnoPage's record
     * @param pageId       identifier of the AnnoPage
     * @param lang         optional, in which language should the AnnoPage be
     * @param versionParam optional, requested IIIF output format (2|3)
     * @return ResponseEntity
     */
    @ApiOperation(value = "Check if a page with annotations exists")
    @RequestMapping(value = {"/{datasetId}/{localId}/annopage/{pageId}"},
                    method = RequestMethod.HEAD,
                    headers = ACCEPT_JSON)
    public ResponseEntity annoPageHeadExistsJson(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String pageId,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "format", required = false) String versionParam,
            HttpServletRequest request) {
        return getAnnoPageHead(datasetId, localId, pageId, lang, versionParam, true, request);
    }

    /**
     * HTTP Head endpoint to check for existence of an AnnoPage
     *
     * @param datasetId    identifier of the AnnoPage's dataset
     * @param localId      identifier of the AnnoPage's record
     * @param pageId       identifier of the AnnoPage
     * @param lang         optional, in which language should the AnnoPage be
     * @param versionParam optional, requested IIIF output format (2|3
     * @return ResponseEntity
     */
    @ApiOperation(value = "Check if a page with annotations exists")
    @RequestMapping(value = {"/{datasetId}/{localId}/annopage/{pageId}"},
                    method = RequestMethod.HEAD,
                    headers = ACCEPT_JSONLD)
    public ResponseEntity annoPageHeadExistsJsonld(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String pageId,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "format", required = false) String versionParam,
            HttpServletRequest request) {
        return getAnnoPageHead(datasetId, localId, pageId, lang, versionParam, false, request);
    }

    private ResponseEntity getAnnoPageHead(
            String datasetId,
            String localId,
            String pageId,
            String lang,
            String versionParam,
            boolean isJson,
            HttpServletRequest request) {
        String requestVersion = getRequestVersion(request, versionParam);
        if (ACCEPT_VERSION_INVALID.equals(requestVersion)) {
            return new ResponseEntity(ACCEPT_VERSION_INVALID, HttpStatus.NOT_ACCEPTABLE);
        }
        HttpHeaders headers = new HttpHeaders();
        addContentTypeToResponseHeader(headers, requestVersion, isJson);
        if (fts.doesAnnoPageExist(datasetId, localId, pageId, lang)) {
            return new ResponseEntity(headers, HttpStatus.OK);
        } else {
            return new ResponseEntity(headers, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Handles fetching a single annotation
     *
     * @param datasetId    identifier of the dataset that contains the AnnoPage with this Annotation
     * @param localId      identifier of the record that contains the AnnoPage with this Annotation
     * @param annoID       identifier of the Annotation
     * @param versionParam requested IIIF output format (2|3)
     * @return response in json format
     * @throws EuropeanaApiException when serialising to Json fails
     */
    @ApiOperation(value = "Retrieve a single annotation")
    @GetMapping(value = "/{datasetId}/{localId}/anno/{annoID}", headers = ACCEPT_JSON)
    public ResponseEntity<String> annotationJson(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String annoID,
            @RequestParam(value = "format", required = false) String versionParam,
            HttpServletRequest request) throws EuropeanaApiException {
        return annotation(datasetId, localId, annoID, versionParam, request, true);
    }

    /**
     * Handles fetching a single annotation
     *
     * @param datasetId    identifier of the dataset that contains the AnnoPage with this Annotation
     * @param localId      identifier of the record that contains the AnnoPage with this Annotation
     * @param annoID       identifier of the Annotation
     * @param versionParam requested IIIF output format (2|3)
     * @return response in json-ld format
     * @throws EuropeanaApiException when serialising to JsonLd fails
     */
    @ApiOperation(value = "Retrieve a single annotation")
    @GetMapping(value = "/{datasetId}/{localId}/anno/{annoID}", headers = ACCEPT_JSONLD)
    public ResponseEntity<String> annotationJsonLd(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String annoID,
            @RequestParam(value = "format", required = false) String versionParam,
            HttpServletRequest request) throws EuropeanaApiException {
        return annotation(datasetId, localId, annoID, versionParam, request, false);
    }

    private ResponseEntity<String> annotation(
            String datasetId,
            String localId,
            String annoID,
            String versionParam,
            HttpServletRequest request,
            boolean isJson) throws EuropeanaApiException {
        LOG.debug("Retrieve Annotation: {}/{}/{}", datasetId, localId, annoID);
        String requestVersion = getRequestVersion(request, versionParam);
        if (ACCEPT_VERSION_INVALID.equals(requestVersion)) {
            return new ResponseEntity<>(ACCEPT_VERSION_INVALID, HttpStatus.NOT_ACCEPTABLE);
        }

        HttpHeaders       headers;
        AnnotationWrapper annotation;
        AnnoPage          annoPage = fts.fetchAPAnnotation(datasetId, localId, annoID);
        ZonedDateTime     modified = CacheUtils.dateToZonedUTC(annoPage.getModified());
        String eTag = generateETag(datasetId + localId + annoID,
                                   modified,
                                   requestVersion + fts.getSettings().getAppVersion(),
                                   true);
        ResponseEntity<String> cached = CacheUtils.checkCached(request, modified, eTag);
        if (cached != null) {
            return cached;
        }

        headers = CacheUtils.generateHeaders(request, eTag, CacheUtils.zonedDateTimeToString(modified));
        addContentTypeToResponseHeader(headers, requestVersion, isJson);

        if ("3".equalsIgnoreCase(requestVersion)) {
            annotation = fts.generateAnnotationV3(annoPage, annoID);
        } else {
            annotation = fts.generateAnnotationV2(annoPage, annoID);
        }

        if (isJson) {
            annotation.setContext(null);
        }
        return new ResponseEntity<>(fts.serialise(annotation), headers, HttpStatus.OK);
    }

    /**
     * Handles fetching a Resource in JSON-LD format
     *
     * @param datasetId identifier of the dataset that contains the Annopage that refers to the Resource
     * @param localId   identifier of the record that contains the Annopage that refers to the Resource
     * @param resId     identifier of the Resource
     * @return response in json-ld format
     * @throws EuropeanaApiException when serialising to JsonLd fails
     */
    @ApiOperation(value = "Retrieve a full-text")
    @GetMapping(value = "/{datasetId}/{localId}/{resId}",
                headers = ACCEPT_JSONLD,
                produces = MEDIA_TYPE_JSONLD + ';' + UTF_8)
    public ResponseEntity<String> resourceJsonLd(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String resId,
            HttpServletRequest request) throws EuropeanaApiException {
        return resource(datasetId, localId, resId, request, false);
    }

    /**
     * Handles fetching a Resource in JSON format
     *
     * @param datasetId identifier of the dataset that contains the Annopage that refers to the Resource
     * @param localId   identifier of the record that contains the Annopage that refers to the Resource
     * @param resId     identifier of the Resource
     * @return response in json format
     * @throws EuropeanaApiException when serialising to Json fails
     */
    @ApiOperation(value = "Retrieve a full-text")
    @GetMapping(value = "/{datasetId}/{localId}/{resId}",
                headers = ACCEPT_JSON,
                produces = MEDIA_TYPE_JSON + ';' + UTF_8)
    public ResponseEntity<String> resourceJson(
            @PathVariable String datasetId,
            @PathVariable String localId,
            @PathVariable String resId,
            HttpServletRequest request) throws EuropeanaApiException {
        return resource(datasetId, localId, resId, request, true);
    }

    private ResponseEntity<String> resource(
            String datasetId, String localId, String resId, HttpServletRequest request, boolean isJson) throws
                                                                                                        EuropeanaApiException {
        LOG.debug("Retrieve Resource: {}/{}/{}", datasetId, localId, resId);
        HttpHeaders headers;
        FTResource  resource;

        resource = fts.fetchFTResource(datasetId, localId, resId);
        ZonedDateTime modified = CacheUtils.januarificator();
        String eTag = generateSimpleETag(datasetId
                                         + localId
                                         + resId
                                         + resource.getLanguage()
                                         + resource.getValue()
                                         + fts.getSettings().getAppVersion(), true);
        ResponseEntity<String> cached = CacheUtils.checkCached(request, modified, eTag);
        if (cached != null) {
            return cached;
        }

        headers = CacheUtils.generateHeaders(request, eTag, CacheUtils.zonedDateTimeToString(modified));
        headers.add(CONTENT_TYPE, (isJson ? MEDIA_TYPE_JSON : MEDIA_TYPE_JSONLD) + ";" + UTF_8);


        if (isJson) {
            resource.setContext(null);
        }
        return new ResponseEntity<>(fts.serialise(resource), headers, HttpStatus.OK);
    }

    // --- utils ---

    private void addContentTypeToResponseHeader(HttpHeaders headers, String version, boolean isJson) {
        if ("3".equalsIgnoreCase(version)) {
            if (isJson) {
                headers.add(CONTENT_TYPE, MEDIA_TYPE_IIIF_JSON_V3);
            } else {
                headers.add(CONTENT_TYPE, MEDIA_TYPE_IIIF_JSONLD_V3);
            }
        } else {
            if (isJson) {
                headers.add(CONTENT_TYPE, MEDIA_TYPE_IIIF_JSON_V2);
            } else {
                headers.add(CONTENT_TYPE, MEDIA_TYPE_IIIF_JSONLD_V2);
            }
        }
    }

    /**
     * Retrieve the requested version from the accept header, or if not present from the format parameter. If nothing
     * is specified then 2 is returned as default
     *
     * @return either version 2, 3 or ACCEPT_INVALID
     */
    private String getRequestVersion(HttpServletRequest request, String format) {
        String result = null;
        String accept = request.getHeader(ACCEPT);
        if (StringUtils.isNotEmpty(accept)) {
            Matcher m = ACCEPT_PROFILE_PATTERN.matcher(accept);
            if (m.find()) { // found a Profile parameter in the Accept header
                String profiles = m.group(1);
                if (profiles.toLowerCase(Locale.getDefault()).contains(MEDIA_TYPE_IIIF_V3)) {
                    result = "3";
                } else if (profiles.toLowerCase(Locale.getDefault()).contains(MEDIA_TYPE_IIIF_V2)) {
                    result = "2";
                } else {
                    result
                            = ACCEPT_VERSION_INVALID; // in case a Profile is found that matches neither version => HTTP 406
                }
            }
        }
        if (result == null) {
            // Request header is empty, or does not contain a Profile parameter
            if (StringUtils.isBlank(format)) {
                result = "2";    // if format not given, fall back to default "2"
            } else if ("2".equals(format) || "3".equals(format)) {
                result = format; // else use the format parameter
            } else {
                result = ACCEPT_VERSION_INVALID;
            }
        }
        return result;
    }

    /**
     * For testing retrieving the version from the pom file
     *
     * @return String representing the API version
     * @throws SerializationException when serialising to a String fails
     */
    @ApiIgnore
    @GetMapping(value = "/showversion")
    public ResponseEntity<String> showVersion() throws SerializationException {
        String response = "The version of this API is: " + fts.getSettings().getAppVersion();
        return new ResponseEntity<>(fts.serialise(response), HttpStatus.I_AM_A_TEAPOT);
    }

}
