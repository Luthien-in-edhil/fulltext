package eu.europeana.fulltext.api.web;

import eu.europeana.fulltext.api.config.FTDefinitions;
import eu.europeana.fulltext.api.model.JsonErrorResponse;
import eu.europeana.fulltext.api.service.FTService;
import eu.europeana.fulltext.api.service.exception.AnnoPageDoesNotExistException;
import eu.europeana.fulltext.api.service.exception.RecordParseException;
import eu.europeana.fulltext.api.service.exception.ResourceDoesNotExistException;
import eu.europeana.fulltext.api.service.exception.SerializationException;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rest controller that handles incoming fulltext requests
 * @author Lúthien
 * Created on 27-02-2018
 */
@RestController
@RequestMapping("/presentation")
public class FTController {

    private static final Logger LOG = LogManager.getLogger(FTController.class);

    /* for parsing accept headers */
    private Pattern acceptProfilePattern = Pattern.compile("profile=\"(.*?)\"");

    private FTService fts;

    public FTController(FTService FTService) {
        this.fts = FTService;
    }

    /**
     * Handles fetching a single annotation
     * @return
     */
    @RequestMapping(value    = "/{datasetId}/{recordId}/anno/{annoID}",
                    method   = RequestMethod.GET,
                    produces = {MediaType.APPLICATION_JSON_VALUE, FTDefinitions.MEDIA_TYPE_JSONLD})
    public String annotation(@PathVariable String datasetId,
                             @PathVariable String recordId,
                             @PathVariable String annoID,
                             @RequestParam(value = "format", required = false) String version,
                             HttpServletRequest request,
                             HttpServletResponse response) throws SerializationException {
        LOG.debug("Retrieve Annotation: " + datasetId + "/" + recordId + "/" + annoID);

        String iiifVersion = version;
        if (iiifVersion == null) {
            iiifVersion = versionFromAcceptHeader(request);
        }

        Object annotation = null;

        try {
            if ("3".equalsIgnoreCase(iiifVersion)) {
                annotation = fts.getAnnotationV3(datasetId, recordId, annoID);
                response.setContentType(FTDefinitions.MEDIA_TYPE_IIIF_JSONLD_V3 + ";charset=UTF-8");
            } else {
                annotation = fts.getAnnotationV2(datasetId, recordId, annoID);
                response.setContentType(FTDefinitions.MEDIA_TYPE_IIIF_JSONLD_V2 + ";charset=UTF-8");
            }
        } catch (AnnoPageDoesNotExistException e) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            LOG.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return fts.serializeResource(new JsonErrorResponse(e.getMessage()));
        }

        return fts.serializeResource(annotation);
    }


    /**
     * for testing HEAD request performance (EA-1239)
     * @return
     */
    @Deprecated
    @RequestMapping(value    = "/{datasetId}/{recordId}/annopage-findAll/{pageId}",
                    method   = RequestMethod.HEAD,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity annoPageHead_findAll(@PathVariable String datasetId,
                                       @PathVariable String recordId,
                                       @PathVariable String pageId,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws RecordParseException {
        if (fts.doesAnnoPageExist_findNotEmpty(datasetId, recordId, pageId)){
            return new ResponseEntity(HttpStatus.OK);
        } else {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * for testing HEAD request performance (EA-1239)
     * @return
     */
    @Deprecated
    @RequestMapping(value    = "/{datasetId}/{recordId}/annopage-findOne/{pageId}",
            method   = RequestMethod.HEAD,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity annoPageHead_findOne(@PathVariable String datasetId,
                                       @PathVariable String recordId,
                                       @PathVariable String pageId,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws RecordParseException {
        if (fts.doesAnnoPageExist_findOneNotNull(datasetId, recordId, pageId)){
            return new ResponseEntity(HttpStatus.OK);
        } else {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * For testing HEAD request performance (EA-1239)
     * This is currently also the method that is used in production, as it seems to be the fastest (together with count)
     * @return
     */
    @RequestMapping(value    = {"/{datasetId}/{recordId}/annopage/{pageId}",
                                "/{datasetId}/{recordId}/annopage-exists/{pageId}"},
            method   = RequestMethod.HEAD,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity annoPageHead_exists(@PathVariable String datasetId,
                                        @PathVariable String recordId,
                                        @PathVariable String pageId,
                                        HttpServletRequest request,
                                        HttpServletResponse response) throws RecordParseException {
        if (fts.doesAnnoPageExist_exists(datasetId, recordId, pageId)){
            return new ResponseEntity(HttpStatus.OK);
        } else {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * for testing HEAD request performance (EA-1239)
     * @return
     */
    @Deprecated
    @RequestMapping(value    = "/{datasetId}/{recordId}/annopage-count/{pageId}",
            method   = RequestMethod.HEAD,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity annoPageHead_count(@PathVariable String datasetId,
                                       @PathVariable String recordId,
                                       @PathVariable String pageId,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws RecordParseException {
        if (fts.doesAnnoPageExist_countNotZero(datasetId, recordId, pageId)){
            return new ResponseEntity(HttpStatus.OK);
        } else {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
    }


    /**
     * Handles fetching a page (resource) with all its annotations
     * @return
     */
    @RequestMapping(value    = "/{datasetId}/{recordId}/annopage/{pageId}",
                    method   = RequestMethod.GET,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    public String annopage(@PathVariable String datasetId,
                           @PathVariable String recordId,
                           @PathVariable String pageId,
                           @RequestParam(value = "format", required = false) String version,
                           HttpServletRequest request,
                           HttpServletResponse response) throws SerializationException {
        LOG.debug("Retrieve Annopage: " + datasetId + "/" + recordId + "/" + pageId);

        String iiifVersion = version;
        if (iiifVersion == null) {
            iiifVersion = versionFromAcceptHeader(request);
        }

        Object annotationPage = null;

        try {
            if ("3".equalsIgnoreCase(iiifVersion)) {
                annotationPage = fts.getAnnotationPageV3(datasetId, recordId, pageId);
                response.setContentType(FTDefinitions.MEDIA_TYPE_IIIF_JSONLD_V3+";charset=UTF-8");
            } else {
                annotationPage = fts.getAnnotationPageV2(datasetId, recordId, pageId);
                response.setContentType(FTDefinitions.MEDIA_TYPE_IIIF_JSONLD_V2+";charset=UTF-8");
            }
        } catch (AnnoPageDoesNotExistException e) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            LOG.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return fts.serializeResource(new JsonErrorResponse(e.getMessage()));
        }
        return fts.serializeResource(annotationPage);
    }

    /**
     * Handles fetching a Fulltext Resource
     * @return
     */
    @RequestMapping(value    = "/{datasetId}/{recordId}/{resId}",
                    method   = RequestMethod.GET,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    public String fulltextJsonLd(@PathVariable String datasetId,
                           @PathVariable String recordId,
                           @PathVariable String resId,
                           HttpServletRequest request,
                           HttpServletResponse response) throws SerializationException {

        // No support for v2 and v3 yet?

        Object resource = null;
        try {
            resource = fts.getFullTextResource(datasetId, recordId, resId);
        } catch (ResourceDoesNotExistException e) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            LOG.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return fts.serializeResource(new JsonErrorResponse(e.getMessage()));
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        return fts.serializeResource(resource);
    }

    private String versionFromAcceptHeader(HttpServletRequest request) {
        String result = "2"; // default version if no accept header is present

        String accept = request.getHeader("Accept");
        if (StringUtils.isNotEmpty(accept)) {
            Matcher m = acceptProfilePattern.matcher(accept);
            if (m.find()) {
                String profiles = m.group(1);
                if (profiles.toLowerCase(Locale.getDefault()).contains(FTDefinitions.MEDIA_TYPE_IIIF_V3)) {
                    result = "3";
                } else {
                    result = "2";
                }
            }
        }
        return result;
    }

}