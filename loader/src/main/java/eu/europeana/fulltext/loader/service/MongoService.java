package eu.europeana.fulltext.loader.service;

import dev.morphia.query.internal.MorphiaCursor;
import eu.europeana.fulltext.entity.AnnoPage;
import eu.europeana.fulltext.entity.Resource;
import eu.europeana.fulltext.loader.config.LoaderSettings;
import eu.europeana.fulltext.loader.exception.DocumentDoesNotExistException;
import eu.europeana.fulltext.loader.exception.LoaderException;
import eu.europeana.fulltext.repository.AnnoPageRepository;
import eu.europeana.fulltext.repository.ResourceRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lúthien
 * Created on 27-02-2018
 */
@Service
public class MongoService {

    private static final Logger LOG                = LogManager.getLogger(MongoService.class);
    private static final String NOMONGORESULTS     = "No results from Mongo";
    private static final String RETRIEVEDANNOPAGES = "Retrieved AnnoPages for {} in {} ms";
    private static final String ADDINGLOGMSG       = "Adding 'lang' field to annoPage {}";
    private static final String RETURNMSG          = "Finished: added 'lang' field to %d documents from dataset %s in %d seconds" ;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    AnnoPageRepository annoPageRepository;

    private LoaderSettings settings;

    public MongoService(LoaderSettings settings) {
        this.settings = settings;
    }

    public void saveAnnoPageList(List<AnnoPage> apList, MongoSaveMode saveMode) throws LoaderException {
        LOG.debug("Saving {} annoPages...", apList.size());

//        long resourceCount = resourceRepository.count();
//        long annoPageCount = annoPageRepository.count();
        if (MongoSaveMode.INSERT.equals(saveMode)) {
            for (AnnoPage annoPage : apList) {
                saveResource(annoPage.getRes());
                saveAnnoPage(annoPage);
            }
//            long newResourceCount = resourceRepository.count();
//            long newAnnoPageCount = annoPageRepository.count();
//            if (resourceCount + apList.size() != newResourceCount) {
//                LogFile.OUT.warn("Expected number of resource in database is {}, but actual number is {}",
//                                 resourceCount + apList.size(),
//                                 newResourceCount);
//            }
//            if (annoPageCount + apList.size() != newAnnoPageCount) {
//                LogFile.OUT.warn("Expected number of annotation pages in database is {}, but actual number is {}",
//                                 annoPageCount + apList.size(),
//                                 annoPageCount);
//            }
        }
        LOG.debug("Saving done.");
    }

    /**
     * Saves a Resource object to the database
     *
     * @return true if the object was saved properly, otherwise false
     */
    public boolean saveResource(Resource resource) throws LoaderException {
        String dsId = resource.getDsId();
        String lcId = resource.getLcId();
        String id   = resource.getId();
        try {
            resourceRepository.save(resource);
            LOG.debug("{}/{}/{} - Resource saved", dsId, lcId, id);
            return true;
        } catch (Exception e) {
            LogFile.OUT.error("{}/{}/{} - Error saving resource", dsId, lcId, id, e);
            if (settings.isStopOnSaveError()) {
                throw new LoaderException("Error saving resource with dsId: " + dsId + ", lcId: " + lcId + ", id:" + id,
                                          e);
            }
            return false;
        }
    }

    /**
     * Deletes all resources that belong to a particular dataset
     *
     * @param datasetId id of the dataset for which all resources should be deleted
     * @return the number of deleted resources
     */
    public long deleteAllResources(String datasetId) {
        return resourceRepository.deleteDataset(datasetId);
    }

    /**
     * Saves an AnnoPage object to the database with embedded Annotations and linking to a resource
     *
     * @param annoPage object that should be saved
     * @return true if the object was saved properly, otherwise false     *
     */
    public boolean saveAnnoPage(AnnoPage annoPage) throws LoaderException {
        String dsId = annoPage.getDsId();
        String lcId = annoPage.getLcId();
        String pgId = annoPage.getPgId();
        try {
            annoPageRepository.save(annoPage);
            LOG.debug("{}/{}/{} AnnoPage saved", dsId, lcId, pgId);
            return true;
        } catch (Exception e) {
            LogFile.OUT.error("{}/{}/{} - Error saving AnnoPage", dsId, lcId, pgId, e);
            if (settings.isStopOnSaveError()) {
                throw new LoaderException("Error saving Annopage with dsId: "
                                          + dsId
                                          + ", lcId: "
                                          + lcId
                                          + ", pgId:"
                                          + pgId, e);
            }
            return false;
        }
    }

    /**
     * Deletes all annotation pages that belong to a particular dataset
     *
     * @param datasetId id of the dataset for which all annopages should be deleted
     * @return the number of deleted annopages
     */
    public long deleteAllAnnoPages(String datasetId) {
        return annoPageRepository.deleteDataset(datasetId);
    }


    /**
     * Adds the 'lang' fields to the AnnoPage collection with value fetched from the Linked Resource
     * <p>:-)</p>
     *
     * @param datasetId  (String) identifier of the dataset, to break up the batch job in more manageable portions
     * @param bufferSize (Integer) number of AnnoPages to process before saving them to the MongoDB server
     * @param collection (String) [NOT IMPLEMENTED YET] name of the collection
     * @return String describing processing results
     */
    public String addMultiLangFieldDataset(
        String datasetId, Integer bufferSize, String collection) throws DocumentDoesNotExistException {
        return iterateCursor(annoPageRepository.findByDatasetNoLang(datasetId),
                             bufferSize,
                             datasetId,
                             System.currentTimeMillis());
    }

    public String addMultiLangFieldAll(Integer bufferSize, String collection) throws DocumentDoesNotExistException {
        return iterateCursor(annoPageRepository.findAllNoLang(), bufferSize, "ALL", System.currentTimeMillis());
    }

    private String iterateCursor(
            MorphiaCursor<AnnoPage> annoPageCursor,
            Integer bufferSize,
            String datasetId,
            long start) throws DocumentDoesNotExistException {

        if (annoPageCursor == null || !annoPageCursor.hasNext()) {
            LOG.debug(NOMONGORESULTS);
            throw new DocumentDoesNotExistException(datasetId);
        } else {
            LOG.debug(RETRIEVEDANNOPAGES, datasetId, System.currentTimeMillis() - start);
        }

        List<AnnoPage> apList    = new ArrayList<>();
        long           index     = 0L;
        int            toBeSaved = 0;

        while (annoPageCursor.hasNext()) {
            AnnoPage annoPage = annoPageCursor.next();
            LOG.debug(ADDINGLOGMSG, annoPage);
            annoPage.setLang(annoPage.getRes().getLang());
            apList.add(annoPage);
            toBeSaved++;
            index++;
            if (toBeSaved >= bufferSize) {
                flushToServer(apList);
                toBeSaved = 0;
                LOG.info("{} items updated", index);
            }
        }
        if (toBeSaved > 0) {
            flushToServer(apList);
        }
        String msg = String.format(RETURNMSG, index, datasetId, (System.currentTimeMillis() - start)/1000);
        LOG.info(msg);
        return msg;
    }

    private void flushToServer(List<AnnoPage> apList) {
        LOG.debug("Saving {} updated annoPages...", apList.size());
        for (AnnoPage annoPage : apList) {
            annoPageRepository.save(annoPage);
        }
        apList.clear();
    }

}
