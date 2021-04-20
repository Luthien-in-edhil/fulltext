package eu.europeana.fulltext.loader.web;

import eu.europeana.fulltext.loader.exception.DocumentDoesNotExistException;
import eu.europeana.fulltext.loader.exception.LoaderException;
import eu.europeana.fulltext.loader.service.LoadArchiveService;
import eu.europeana.fulltext.loader.service.MongoSaveMode;
import eu.europeana.fulltext.loader.service.MongoService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;


/**
 * Rest controller that handles incoming requests to parse full text xml files and load them into a database
 * @author Lúthien
 * Created on 27-02-2018
 */
@RestController
@RequestMapping("/fulltext")
public class LoaderController {

    private LoadArchiveService loadArchiveService;
    private MongoService mongoService;

    public LoaderController(LoadArchiveService loadArchiveService, MongoService mongoService) {
        this.loadArchiveService = loadArchiveService;
        this.mongoService = mongoService;
    }

    /**
     * starts batch importing of a zip-file
     * @return string describing processing results
     * @throws LoaderException when there is a problem reading or processing the provided archive file
     */
    @GetMapping(value = "/zipbatch", produces = MediaType.TEXT_PLAIN_VALUE)
    public String zipbatch(@RequestParam(value = "archive", required = true) String archive,
                           @RequestParam(value = "mode", required = false, defaultValue = "INSERT") MongoSaveMode saveMode)
                            throws LoaderException {
        return loadArchiveService.importZipBatch(archive, saveMode);
    }

    /**
     * Delete all resources and annotationpages of the provided dataset
     * @param datasetId id of the dataset that is to be removed
     * @return String describing what was deleted
     */
    @GetMapping(value = "/delete", produces = MediaType.TEXT_PLAIN_VALUE)
    public String delete(@RequestParam(value = "datasetId", required = true) String datasetId) {
        LogManager.getLogger(LoaderController.class).debug("Starting delete...");
        StringBuilder s = new StringBuilder("Deleted ");
        s.append(mongoService.deleteAllAnnoPages(datasetId));
        s.append(" annopages and ");
        s.append(mongoService.deleteAllResources(datasetId));
        s.append(" resources");
        String result = s.toString();
        LogManager.getLogger(LoaderController.class).info(result);
        return result;
    }

    /**
     * Adds the following fields to the AnnoPage collection (name can be overridden):
     * - String lang (default true, disable by adding &lang=false) Initial value: fetched from linked Resource
     * - Boolean orig (default false, enable by adding &orig=true). Initial value = true
     * The value of the lang field is initiated with the value of the lang field of the associated Resource document.
     * <p>:-)</p>
     * @param  datasetId (String, required) identifier of the dataset, to break up the batch job in more manageable portions
     * @param  flushBuffer (Integer, optional) number of AnnoPages to process before saving them to the MongoDB server
     *                     Default value is 100
     * @param  collection (String, optional) [NOT IMPLEMENTED YET] name of the collection (defaults to 'AnnoPage')
     * @return string describing processing results
     */
    @GetMapping(value = "/addlangto/{datasetId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String langfield(@PathVariable(value = "datasetId") String datasetId,
            @RequestParam(value = "flushBuffer", required = false, defaultValue = "100") String flushBuffer,
            @RequestParam(value = "collection", required = false, defaultValue="AnnoPage") String collection) {

        Integer bufferSize = 100;
        if (NumberUtils.isCreatable(flushBuffer)){
            bufferSize = NumberUtils.createInteger(flushBuffer);
            if (bufferSize < 1){
                return "flushBuffer should be larger than 1";
            }
        } else {
            return "flushBuffer should have an integer value";
        }

        try {
            if ("ALL".equalsIgnoreCase(datasetId)) {
                return mongoService.addMultiLangFieldAll(bufferSize, collection);
            } else{
                return mongoService.addMultiLangFieldDataset(datasetId, bufferSize, collection);
            }
        } catch (DocumentDoesNotExistException e) {
            LogManager.getLogger(LoaderController.class).info(e.getMessage());
            return e.getMessage();
        }

    }


}
