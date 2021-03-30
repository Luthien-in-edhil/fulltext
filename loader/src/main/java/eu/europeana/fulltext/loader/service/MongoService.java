package eu.europeana.fulltext.loader.service;

import eu.europeana.fulltext.entity.AnnoPage;
import eu.europeana.fulltext.entity.Resource;
import eu.europeana.fulltext.loader.config.LoaderSettings;
import eu.europeana.fulltext.repository.AnnoPageRepository;
import eu.europeana.fulltext.repository.ResourceRepository;
import eu.europeana.fulltext.loader.exception.LoaderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Lúthien
 * Created on 27-02-2018
 */
@Service
public class MongoService {

    private static final Logger LOG = LogManager.getLogger(MongoService.class);

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

        long resourceCount = resourceRepository.count();
        long annoPageCount = annoPageRepository.count();
        if (MongoSaveMode.INSERT.equals(saveMode)) {
            for (AnnoPage annoPage : apList) {
                saveResource(annoPage.getRes());
                saveAnnoPage(annoPage);
            }
            long newResourceCount = resourceRepository.count();
            long newAnnoPageCount = annoPageRepository.count();
            if (resourceCount + apList.size() != newResourceCount) {
                LogFile.OUT.warn("Expected number of resource in database is {}, but actual number is {}",
                        resourceCount + apList.size(), newResourceCount);
            }
            if (annoPageCount + apList.size() != newAnnoPageCount) {
                LogFile.OUT.warn("Expected number of annotation pages in database is {}, but actual number is {}",
                        annoPageCount + apList.size(), annoPageCount);
            }
        }
        LOG.debug("Saving done.");
    }

    /**
     * Saves a Resource object to the database
     * @return true if the object was saved properly, otherwise false
     */
    public boolean saveResource(Resource resource) throws LoaderException {
        String dsId = resource.getDsId();
        String lcId = resource.getLcId();
        String id = resource.getId();
        try{
            resourceRepository.save(resource);
            LOG.debug("{}/{}/{} - Resource saved", dsId, lcId, id);
            return true;
        } catch (Exception e){
            LogFile.OUT.error("{}/{}/{} - Error saving resource", dsId, lcId, id, e);
            if (settings.isStopOnSaveError()) {
                throw new LoaderException("Error saving resource with dsId: " + dsId +
                                          ", lcId: " + lcId +
                                          ", id:" + id, e);
            }
            return false;
        }
    }

    /**
     * Deletes all resources that belong to a particular dataset
     * @param datasetId id of the dataset for which all resources should be deleted
     * @return the number of deleted resources
     */
    public long deleteAllResources(String datasetId) {
        return resourceRepository.deleteDataset(datasetId);
    }

    /**
     * Saves an AnnoPage object to the database with embedded Annotations and linking to a resource
     * @param annoPage object that should be saved
     * @return true if the object was saved properly, otherwise false     *
     */
    public boolean saveAnnoPage(AnnoPage annoPage) throws LoaderException {
        String dsId = annoPage.getDsId();
        String lcId = annoPage.getLcId();
        String pgId = annoPage.getPgId();
        try{
            annoPageRepository.save(annoPage);
            LOG.debug("{}/{}/{} AnnoPage saved", dsId, lcId, pgId);
            return true;
        } catch (Exception e){
            LogFile.OUT.error("{}/{}/{} - Error saving AnnoPage", dsId, lcId, pgId, e);
            if (settings.isStopOnSaveError()) {
                throw new LoaderException("Error saving Annopage with dsId: " + dsId +
                                          ", lcId: " + lcId +
                                          ", pgId:" + pgId, e);
            }
            return false;
        }
    }

    /**
     * Deletes all annotation pages that belong to a particular dataset
     * @param datasetId id of the dataset for which all annopages should be deleted
     * @return the number of deleted annopages
     */
    public long deleteAllAnnoPages(String datasetId) {
        return annoPageRepository.deleteDataset(datasetId);
    }

    /**
     * Adds the following fields to the AnnoPage collection (name can be overridden):
     * - String lang (default ON, switch OFF by adding &lang=false) - initial value fetched from Resource
     * - Boolean orig (default OFF, switch ON by adding &orig=true) - initial value = true
     * The value of the lang field is initiated with the value of the lang field of the associated Resource document.
     * <p>:-)</p>
     * @param  datasetId (String, required) identifier of the dataset, to break up the batch job in more manageable portions
     * @param  addLang (boolean, required) set to 'false' to suppress creating the lang field
     * @param  addOrig (boolean, required) set to 'true' to enable creating the orig field (set to 'true')
     * @return string describing processing results
     */
    public String addMultiLangFields(String datasetId, Boolean addLang, Boolean addOrig) {
        if (addLang && addOrig){
            annoPageRepository.setLangAndOrigin(dataSetId);
        }


        return "finished";
    }

    /**
     * Adds the following fields to the AnnoPage collection (name can be overridden):
     * - String lang (default ON, switch OFF by adding &lang=false) - initial value fetched from Resource
     * - Boolean orig (default OFF, switch ON by adding &orig=true) - initial value = true
     * The value of the lang field is initiated with the value of the lang field of the associated Resource document.
     * <p>:-)</p>
     * @param  datasetId (String, required) identifier of the dataset, to break up the batch job in more manageable portions
     * @param  addLang (boolean, required) set to 'false' to suppress creating the lang field
     * @param  addOrig (boolean, required) set to 'true' to enable creating the orig field (set to 'true')
     * @param  collection (String, optional) name of the collection (defaults to 'AnnoPage')
     * @return string describing processing results
     */
    public String addMultiLangFields(String datasetId, Boolean addLang, Boolean addOrig, String collection) {
        if (addLang && addOrig){
            annoPageRepository.setLangAndOrigin(dataSetId);
        }


        return "finished";
    }

}
