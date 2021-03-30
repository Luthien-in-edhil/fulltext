package eu.europeana.fulltext.repository;

import dev.morphia.Datastore;
import eu.europeana.fulltext.entity.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static dev.morphia.query.experimental.filters.Filters.eq;
import static eu.europeana.fulltext.util.MorphiaUtils.Fields.*;
import static eu.europeana.fulltext.util.MorphiaUtils.MULTI_DELETE_OPTS;


/**
 * Created by luthien on 31/05/2018.
 */
@Repository
public class ResourceRepository {


    @Autowired
    private Datastore datastore;

    /**
     * Check if a Resource exists that matches the given parameters
     * @param datasetId ID of the associated dataset
     * @param localId   ID of the associated Annopage parent object
     * @param resId     ID of the Resource document
     * @return true if yes, otherwise false
     */
    public boolean existsByLimitOne(String datasetId, String localId, String resId) {
        return datastore.find(Resource.class)
                .filter(
                        eq(DATASET_ID, datasetId),
                        eq(LOCAL_ID, localId),
                        eq(RES_ID, resId))
                .count() > 0;
    }

    /**
     * @return the total number of resources in the database
     */
    public long count() {
        return datastore.find(Resource.class).count();
    }

    /**
     * Find a Resource that matches the given parameters
     * @param datasetId ID of the associated dataset
     * @param localId   ID of the associated Annopage parent object
     * @param resId     ID of the Resource document
     * @return List containing matching Resource(s) (should be just one)
     */
    public Resource findByDatasetLocalResId(String datasetId, String localId, String resId) {
        return datastore.find(Resource.class)
                .filter(
                        eq(DATASET_ID, datasetId),
                        eq(LOCAL_ID, localId),
                        eq(RES_ID, resId))
                .first();
    }

    /**
     * Deletes all resources associated with a particular dataset
     * @param datasetId ID of the associated dataset
     * @return the number of deleted resources
     */
    public long deleteDataset(String datasetId) {
        return datastore.find(Resource.class)
                .filter(eq(DATASET_ID, datasetId)).delete(MULTI_DELETE_OPTS).getDeletedCount();
    }

    public void save(Resource resToSave){
        datastore.save(resToSave);
    }
}
