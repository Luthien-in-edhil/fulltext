package eu.europeana.fulltext.repository;

import com.mongodb.client.model.Filters;
import dev.morphia.Datastore;
import dev.morphia.UpdateOptions;
import dev.morphia.aggregation.experimental.Aggregation;
import dev.morphia.aggregation.experimental.expressions.ArrayExpressions;
import dev.morphia.aggregation.experimental.stages.Projection;
import dev.morphia.query.experimental.updates.UpdateOperators;
import dev.morphia.query.internal.MorphiaCursor;
import eu.europeana.fulltext.AnnotationType;
import eu.europeana.fulltext.entity.AnnoPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

import static dev.morphia.aggregation.experimental.expressions.ArrayExpressions.filter;
import static dev.morphia.aggregation.experimental.expressions.BooleanExpressions.not;
import static dev.morphia.aggregation.experimental.expressions.Expressions.field;
import static dev.morphia.aggregation.experimental.expressions.Expressions.value;
import static dev.morphia.query.experimental.filters.Filters.*;
import static eu.europeana.fulltext.util.MorphiaUtils.Fields.*;
import static eu.europeana.fulltext.util.MorphiaUtils.MULTI_DELETE_OPTS;


/**
 * Repository for retrieving AnnoPage objects / data
 * Created by luthien on 31/05/2018.
 */
@Repository
public class AnnoPageRepository {


    @Autowired
    private Datastore datastore;

    /**
     * @return the total number of resources in the database
     */
    public long count() {
        return datastore.find(AnnoPage.class).count();
    }

    /**
     * Check if an AnnoPage exists that contains an Annotation that matches the given parameters
     * using DBCollection.count(). In ticket EA-1464 this method was tested as the best performing.
     * @param datasetId ID of the dataset
     * @param localId   ID of the parent of the Annopage object
     * @param pageId    index (page number) of the Annopage object within its parent
     * @return true if yes, otherwise false
     */
    public boolean existsByPageId(String datasetId, String localId, String pageId) {
        return datastore.find(AnnoPage.class).filter(
                eq(DATASET_ID, datasetId),
                eq(LOCAL_ID, localId),
                eq(PAGE_ID, pageId)
        ).count() > 0 ;
    }

    /**
     * Check if an AnnoPage exists that contains an Annotation that matches the given parameters
     * @param datasetId ID of the dataset
     * @param localId   ID of the parent of the Annopage object
     * @param annoId    ID of the annotation
     * @return true if yes, otherwise false
     */
    public boolean existsWithAnnoId(String datasetId, String localId, String annoId) {
        return datastore.find(AnnoPage.class)
                .filter(
                        eq(DATASET_ID, datasetId),
                        eq(LOCAL_ID, localId),
                        eq(ANNOTATIONS_ID, annoId)
                )
                .count() > 0;
    }

    /**
     * Find and return an AnnoPage that matches the given parameters.
     * Only annotations that match the specified text granularity values are retrieved from the data store.
     * <p>
     * The mongodb query implemented by this method is:
     * db.getCollection("AnnoPage").aggregate(
     * {$match: {"dsId": <datasetId>, "lcId": <localId>, "pgId": <pageId>}},
     * {$project: {
     *   "dsId": "$dsId",
     *   "lcId":"$lcId",
     *   "pgId": "$pgId",
     *   "tgtId": "$tgtId",
     *   "res": "$res",
     *   "className": "$className",
     *   "modified": "$modified",
     *   "ans": {
     *                 $filter: {
     *                   input: "$ans",
     *                   as: "annotation",
     *                   cond: { $in: [ '$$annotation.dcType', [<textGranValues>] ] }
     *                 }
     *             }
     * })
     *
     * @param datasetId      ID of the dataset
     * @param localId        ID of the parent of the Annopage object
     * @param pageId         index (page number) of the Annopage object within its parent
     * @param annoTypes      dcType values to filter annotations with
     * @return AnnoPage
     */
    public AnnoPage findByDatasetLocalPageId(String datasetId, String localId, String pageId, List<AnnotationType> annoTypes) {
        Aggregation<AnnoPage> query = datastore.aggregate(AnnoPage.class).match(
                eq(DATASET_ID, datasetId),
                eq(LOCAL_ID, localId),
                eq(PAGE_ID, pageId)
        );
        query = filterTextGranularity(query, annoTypes);
        return query.execute(AnnoPage.class).tryNext();
    }


    /**
     * Find and return AnnoPage that contains an annotation that matches the given parameters
     * @param datasetId ID of the dataset
     * @param localId   ID of the parent of the Annopage object
     * @param annoId    ID of the annotation
     * @return AnnoPage
     */
    public AnnoPage findByDatasetLocalAnnoId(String datasetId, String localId, String annoId) {
        return datastore.find(AnnoPage.class).filter(
                eq(DATASET_ID, datasetId),
                eq(LOCAL_ID, localId),
                eq(ANNOTATIONS_ID, annoId))
                .first();
    }

    /**
     * Find and return AnnoPages that contains an annotation that matches the given parameters.
     *
     * Returns a {@link MorphiaCursor} that can be iterated on to obtain matching AnnoPages.
     * The cursor must be closed after iteration is completed.
     *
     * The Cursor returned by this method must be closed
     * @param datasetId ID of the dataset
     * @param localId   ID of the parent of the Annopage object
     * @param imageIds  ID of the image
     * @param annoTypes type of annotations that should be retrieve, if null or empty all annotations of that
     *                        annopage will be retrieved
     * @return MorphiaCursor containing AnnoPage entries.
     */
    public MorphiaCursor<AnnoPage> findByDatasetLocalImageId(String datasetId, String localId, List<String> imageIds,
                                                             List<AnnotationType> annoTypes) {
        Aggregation<AnnoPage> query = datastore.aggregate(AnnoPage.class).match(
                eq(DATASET_ID, datasetId),
                eq(LOCAL_ID, localId),
                in(IMAGE_ID, imageIds)
        );
        query = filterTextGranularity(query, annoTypes);
        return query.execute(AnnoPage.class);
    }

    /**
     * Deletes all annotation pages part of a particular dataset
     * @param datasetId ID of the dataset to be deleted
     * @return the number of deleted annotation pages
     */
    // TODO move this to the loader?
    public long deleteDataset(String datasetId) {
        return datastore.find(AnnoPage.class).filter(
                eq(DATASET_ID,datasetId))
                .delete(MULTI_DELETE_OPTS).getDeletedCount();
    }

    // TODO move this to the loader?
    public void save(AnnoPage apToSave){
        datastore.save(apToSave);
    }


    /**
     * Creates an AnnoPage aggregation query to return only matching annotation types.
     * @param annoPageQuery aggregation query
     * @param annoTypes list containing text granularity values to match
     * @return Updated aggregation query
     */
    private Aggregation<AnnoPage> filterTextGranularity(Aggregation<AnnoPage> annoPageQuery, List<AnnotationType> annoTypes) {
        if (annoTypes.isEmpty()) {
            return annoPageQuery;
        }

        // ans.dcType stored as first letter of text granularity value in uppercase. ie. WORD -> 'W'
        List<String> dcTypes = annoTypes.stream().map(s -> String.valueOf(s.getAbbreviation())).collect(Collectors.toUnmodifiableList());

        // _id implicitly included in projection
        return annoPageQuery.project(
                Projection.of()
                        .include(DATASET_ID)
                        .include(LOCAL_ID)
                        .include(PAGE_ID)
                        .include(RESOURCE)
                        .include(CLASSNAME)
                        .include(IMAGE_ID)
                        .include(MODIFIED)
                        .include(ANNOTATIONS,
                                filter(field(ANNOTATIONS),
                                        ArrayExpressions.in(value("$$annotation.dcType"), value(dcTypes))
                                ).as("annotation")
                        )
        );
    }

//    public void setLangAndOrigin(String datasetId){
//        MorphiaCursor<AnnoPage> addLangAndOrigin = datastore.
//        datastore
//                .find(AnnoPage.class)
//                .update(UpdateOperators.set("lang", "Fairmont Chateau Laurier"))
//                .execute(new UpdateOptions()
//                                 .multi(true));
//    }

    public MorphiaCursor<AnnoPage> findByDatasetNoLang(String datasetId) {
        return datastore.aggregate(AnnoPage.class).match(
                eq(DATASET_ID, datasetId),
                exists("lang").not())
                .execute(AnnoPage.class);
    }

    public MorphiaCursor<AnnoPage> findByDatasetNoLangOrOrig(String datasetId) {
        return datastore.aggregate(AnnoPage.class).match(
                eq(DATASET_ID, datasetId),
                exists("orig").not(),
                exists("lang").not())
                        .execute(AnnoPage.class);
    }

    public MorphiaCursor<AnnoPage> findByDatasetNoOrig(String datasetId) {
        return datastore.aggregate(AnnoPage.class).match(
                eq(DATASET_ID, datasetId),
                exists("orig").not())
                        .execute(AnnoPage.class);
    }


}
