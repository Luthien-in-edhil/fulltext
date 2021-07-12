package eu.europeana.fulltext.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.morphia.query.internal.MorphiaCursor;
import eu.europeana.fulltext.AnnotationType;
import eu.europeana.fulltext.api.config.FTDefinitions;
import eu.europeana.fulltext.api.config.FTSettings;
import eu.europeana.fulltext.api.model.info.SummaryAnnoPage;
import eu.europeana.fulltext.api.model.info.SummaryCanvas;
import eu.europeana.fulltext.api.model.info.SummaryManifest;
import eu.europeana.fulltext.api.model.FTResource;
import eu.europeana.fulltext.api.model.v2.AnnotationPageV2;
import eu.europeana.fulltext.api.model.v2.AnnotationV2;
import eu.europeana.fulltext.api.model.v3.AnnotationPageV3;
import eu.europeana.fulltext.api.model.v3.AnnotationV3;
import eu.europeana.fulltext.api.service.exception.AnnoPageDoesNotExistException;
import eu.europeana.fulltext.api.service.exception.ResourceDoesNotExistException;
import eu.europeana.fulltext.api.service.exception.SerializationException;
import eu.europeana.fulltext.entity.AnnoPage;
import eu.europeana.fulltext.entity.Resource;
import eu.europeana.fulltext.entity.TranslationAnnoPage;
import eu.europeana.fulltext.repository.AnnoPageRepository;
import eu.europeana.fulltext.repository.ResourceRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 *
 *
 * @author Lúthien
 * Created on 27-02-2018
 */
@Service
public class FTService {

    private static final String GENERATED_IN    = "Generated in {} ms ";
    private static final Logger LOG             = LogManager.getLogger(FTService.class);

    private final ResourceRepository resourceRepository;
    private final AnnoPageRepository annoPageRepository;
    private final FTSettings         ftSettings;

    private final ObjectMapper mapper;

    /*
     * Constructs an FTService object with autowired dependencies
     */
    public FTService(ResourceRepository resourceRepository, AnnoPageRepository annoPageRepository, FTSettings ftSettings, ObjectMapper mapper) {
        this.resourceRepository = resourceRepository;
        this.annoPageRepository = annoPageRepository;
        this.ftSettings = ftSettings;
        this.mapper = mapper;
    }

    /**
     * @return ManifestSettings object containing settings loaded from properties file
     */
    public FTSettings getSettings() {
        return ftSettings;
    }


    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    /**
     * Handles fetching an Annotation page (aka AnnoPage) with all its annotations
     * @param datasetId identifier of the AnnoPage's dataset
     * @param localId   identifier of the AnnoPage's record
     * @param pageId    identifier of the AnnoPage
     * @param textGranValues dcType values to filter annotations with
     * @param lang      optional, if provided we'll check if there's an original or translation annopage with this language
     * @throws AnnoPageDoesNotExistException when the Annopage cannot be found
     * @return AnnoPage
     */
    public AnnoPage fetchAnnoPage(String datasetId, String localId, String pageId, List<AnnotationType> textGranValues,
                                  String lang) throws AnnoPageDoesNotExistException {
        AnnoPage result;
        if (StringUtils.isEmpty(lang)) {
            result = annoPageRepository.findOriginalByPageId(datasetId, localId, pageId, textGranValues);
            if (result == null) {
                throw new AnnoPageDoesNotExistException(String.format("/%s/%s/annopage/%s", datasetId, localId, pageId));
            }
        } else  {
            // TODO do request to get original and translation in parallel instead in series
            //  (unless we can fix this my doing 1 query in AnnoPageRepository)
            result = annoPageRepository.findOriginalByPageIdLang(datasetId, localId, pageId, textGranValues, lang);
            if (result == null) {
                result = annoPageRepository.findTranslationByPageIdLang(datasetId, localId, pageId, textGranValues, lang);
                LOG.debug("No original AnnoPage, TranslationAnnoPage = {}", result);
            }
            if (result == null) {
                throw new AnnoPageDoesNotExistException(String.format("/%s/%s/annopage/%s", datasetId, localId, pageId), lang);
            }
        }
        return result;
    }

    /**
     * Retrieve a cursor to AnnoPages with the provided datasetId, localId and imageIds. If the annotationType is
     * specified the returned AnnoPages will only contain annotations of that type. If annotationType is null or empty
     * then all annotations of that type will be returned. The cursor must be closed when the caller is done!
     * @param datasetId ID of the dataset
     * @param localId   ID of the parent of the Annopage object
     * @param imageIds  IDs of the images
     * @param annoTypes type of annotations that should be retrieved, if null or empty all annotations of that
     *                        annopage will be retrieved
     * @return MorphiaCursor containing AnnoPage entries.
     */
    public MorphiaCursor<AnnoPage> fetchAnnoPageFromImageId(String datasetId, String localId, List<String> imageIds, List<AnnotationType> annoTypes) {
        return annoPageRepository.findByImageId(datasetId, localId, imageIds, annoTypes);
    }


    /**
     * Handles fetching an Annotation page (aka AnnoPage) containing the Annotation with given annoId
     * @param datasetId identifier of the AnnoPage's dataset
     * @param localId   identifier of the AnnoPage's record
     * @param annoId    identifier of the Annotation to be found
     * @throws AnnoPageDoesNotExistException when the Annopage containing the required Annotation can't be found
     * @return AnnoPage
     */
    public AnnoPage fetchAPAnnotation(String datasetId, String localId, String annoId) throws AnnoPageDoesNotExistException {
        // TODO do request to get original and translation in parallel instead in series (unless we can fix this my doing 1 query in AnnoPageRepository)
        AnnoPage result = annoPageRepository.findOriginalByAnnoId(datasetId, localId, annoId);
        if (result == null) {
            annoPageRepository.findTranslationByAnnoId(datasetId, localId, annoId);
            LOG.debug("Annotation not in original AnnoPage, TranslationAnnoPage = {}", result);
        }

        if (result == null) {
            throw new AnnoPageDoesNotExistException(String.format("/%s/%s/anno/%s", datasetId, localId, annoId));
        }
        return result;
    }


    /**
     * Handles fetching an Annotation page (aka AnnoPage) containing the Annotation with given annoId
     * @param datasetId identifier of the dataset that contains the Annopage that refers to Resource
     * @param localId   identifier of the record that contains the Annopage that refers to Resource
     * @param resId     identifier of the Resource
     * @throws ResourceDoesNotExistException when the Resource can't be found
     * @return FTResource
     */
    public FTResource fetchFTResource(String datasetId, String localId, String resId)  throws ResourceDoesNotExistException {
        // TODO investigate if we can combine original annopage and translation annopage in 1 query
        //  (or maybe when lang is specified we can sent 2 requests in parallel)
        Resource result = resourceRepository.findOriginalByResId(datasetId, localId, resId);
        if (result == null) {
            result = resourceRepository.findTranslationByResId(datasetId, localId, resId);
            LOG.debug("No original Resource, TranslationResource = {}", result);
        }

        if (result == null) {
            throw new ResourceDoesNotExistException(String.format("/%s/%s/%s", datasetId, localId, resId));
        }
        return generateFTResource(result);
    }

    // = = [ collect summary information ]= = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    public SummaryManifest collectAnnoPageInfo(String datasetId, String localId) throws AnnoPageDoesNotExistException {
        // 1) create SummaryManifest container for this EuropeanaID
        SummaryManifest apInfoSummaryManifest = new SummaryManifest(datasetId, localId);

        // 2) find all original AnnoPages and create a SummaryCanvas for each
        List<AnnoPage> annoPages = annoPageRepository.findOrigPages(datasetId, localId);
        if (annoPages == null || annoPages.size() == 0) {
            throw new AnnoPageDoesNotExistException(datasetId + "/" + localId);
        }
        for (AnnoPage ap : annoPages) {
            SummaryCanvas summaryCanvas = new SummaryCanvas(makeSummaryCanvasID(ap));

            // add original SummaryAnnoPage to the SummaryCanvas
            summaryCanvas.addAnnotation(new SummaryAnnoPage(makeLangAwareAnnoPageID(ap), ap.getLang()));
            summaryCanvas.setOriginalLanguage(ap.getLang());

            // add translated AnnotationLangPages (if any) to the SummaryCanvas
            for (TranslationAnnoPage tap : annoPageRepository.findTranslatedPages(datasetId, localId, ap.getPgId())) {
                summaryCanvas.addAnnotation(new SummaryAnnoPage(makeLangAwareAnnoPageID(tap), tap.getLang()));
            }
            // modified value of last Anno Page will be added in the SummaryManifest
            apInfoSummaryManifest.setModified(ap.getModified());
            // add SummaryCanvas to SummaryManifest
            apInfoSummaryManifest.addCanvas(summaryCanvas);
        }
        return apInfoSummaryManifest;
    }

    private String makeSummaryCanvasID(AnnoPage ap){
        return ftSettings.getAnnoPageBaseUrl() + ap.getDsId() + "/" + ap.getLcId() + FTDefinitions.CANVAS_PATH +"/" + ap.getPgId();
    }

    private String makeLangAwareAnnoPageID(AnnoPage ap){
        StringBuilder result = new StringBuilder(100);
        result.append(ftSettings.getAnnoPageBaseUrl())
                .append(ap.getDsId())
                .append("/")
                .append(ap.getLcId())
                .append(FTDefinitions.ANNOPAGE_PATH)
                .append("/")
                .append(ap.getPgId());
        if (!StringUtils.isEmpty(ap.getLang())) {
            result.append("?")
                    .append(FTDefinitions.LANGUAGE_PARAM)
                    .append(ap.getLang());
        }
        return result.toString();
    }

    // = = [ check Document existence ]= = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    /**
     * Check if a particular annotation page with the provided ids exists or not
     * @param datasetId Identifier of the dataset
     * @param localId   Identifier of the item
     * @param pageId    Identifier of the item's page
     * @param lang      optional, in which language should the AnnoPage be
     * @return true if it exists, otherwise false
     */
    public boolean doesAnnoPageExist(String datasetId, String localId, String pageId, String lang){
        if (StringUtils.isEmpty(lang)) {
            return annoPageRepository.existsOriginalByPageId(datasetId, localId, pageId);
        }

        // TODO investigate if we can combine original annopage and translation annopage in 1 query
        //  (or maybe when lang is specified we can sent 2 requests in parallel)
        boolean result = annoPageRepository.existsOriginalByPageIdLang(datasetId, localId, pageId, lang);
        if (!result) {
            result = annoPageRepository.existsTranslationByPageIdLang(datasetId, localId, pageId, lang);
            LOG.debug("No original AnnoPage, TranslationAnnoPage exists = {}", result);
        }
        return result;
    }

    // = = [ generate JSON objects ] = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =
    /**
     * Generates an AnnotationPageV3 (IIIF V3 response type) object with the AnnoPage as input
     * @param annoPage AnnoPage input object
     * @param derefResource boolean indicating whether to dereference the Resource object on the top level Annotation
     * @return AnnotationPageV3
     */
     public AnnotationPageV3 generateAnnoPageV3(AnnoPage annoPage, boolean derefResource){
        long start = System.currentTimeMillis();
        AnnotationPageV3 result = EDM2IIIFMapping.getAnnotationPageV3(annoPage, derefResource);
        if (LOG.isDebugEnabled()) {
            LOG.debug(GENERATED_IN, System.currentTimeMillis() - start);
        }
        return result;
    }

    /**
     * Generates an AnnotationPageV2 (IIIF V2 response type) object with the AnnoPage as input
     * @param annoPage AnnoPage input object
     * @param derefResource boolean indicating whether or not to load and dereference the Resource
     * @return AnnotationPageV2
     */
    public AnnotationPageV2 generateAnnoPageV2(AnnoPage annoPage, boolean derefResource){
        long start = System.currentTimeMillis();
        AnnotationPageV2 result = EDM2IIIFMapping.getAnnotationPageV2(annoPage, derefResource);
        if (LOG.isDebugEnabled()) {
            LOG.debug(GENERATED_IN, System.currentTimeMillis() - start);
        }
        return result;
    }

    /**
     * Generates an AnnotationV3 (IIIF V3 response type) object of the Annotation with ID annoId, found within the
     * the AnnoPage input
     * @param annoPage AnnoPage input object
     * @param annoId String the Annotation idenfifier
     * @return AnnotationV3
     */
    public AnnotationV3 generateAnnotationV3(AnnoPage annoPage, String annoId){
        long start = System.currentTimeMillis();
        AnnotationV3 result = EDM2IIIFMapping.getSingleAnnotationV3(annoPage, annoId);
        if (LOG.isDebugEnabled()) {
            LOG.debug(GENERATED_IN, System.currentTimeMillis() - start);
        }
        return result;
    }


    /**
     * Generates an AnnotationV2 (IIIF V2 response type) object of the Annotation with ID annoId, found within the
     * the AnnoPage input
     * @param annoPage AnnoPage input object
     * @param annoId String the Annotation idenfifier
     * @return AnnotationV2
     */
    public AnnotationV2 generateAnnotationV2(AnnoPage annoPage, String annoId){
        long start = System.currentTimeMillis();
        AnnotationV2 result = EDM2IIIFMapping.getSingleAnnotationV2(annoPage, annoId);
        if (LOG.isDebugEnabled()) {
            LOG.debug(GENERATED_IN, System.currentTimeMillis() - start);
        }
        return result;
    }

    private FTResource generateFTResource(Resource resource){
        long       start  = System.currentTimeMillis();
        FTResource result = EDM2IIIFMapping.getFTResource(resource);
        if (LOG.isDebugEnabled()) {
            LOG.debug(GENERATED_IN, System.currentTimeMillis() - start);
        }
        return result;
    }

    // = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

    /**
     * Serialize data from MongoDB to JSON-LD
     * @param  data input data
     * @return JSON-LD string
     * @throws SerializationException when serialisation seriously severely snaps somewhere
     */
    public String serialise(Object data) throws SerializationException {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (IOException e) {
            throw new SerializationException("Error serialising data: " + e.getMessage(), e);
        }
    }


}
