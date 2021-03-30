package eu.europeana.fulltext.util;

import dev.morphia.DeleteOptions;
import dev.morphia.mapping.DiscriminatorFunction;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.mapping.NamingStrategy;

public class MorphiaUtils {

    public static final MapperOptions MAPPER_OPTIONS = MapperOptions
            .builder()
            // use legacy settings for backwards-compatibility
            .discriminatorKey(Fields.CLASSNAME)
            .discriminator(DiscriminatorFunction.className())
            .fieldNaming(NamingStrategy.identity())
            .build();

    // Morphia deletes the first matching document by default. This is required for deleting all matches.
    public static final DeleteOptions MULTI_DELETE_OPTS = new DeleteOptions().multi(true);

    private MorphiaUtils() {
        // private constructor to prevent instantiation
    }

    // Collection field names
    public static class Fields {

        public static final String RES_ID             = "_id";
        public static final String DATASET_ID = "dsId";
        public static final String LOCAL_ID = "lcId";
        public static final String PAGE_ID = "pgId";
        public static final String IMAGE_ID = "tgtId";
        public static final String CLASSNAME = "className";
        public static final String ANNOTATIONS = "ans";
        public static final String RESOURCE = "res";
        public static final String MODIFIED = "modified";
        public static final String ORIG               = "orig";
        public static final String ANNOTATIONS_DCTYPE = ANNOTATIONS + ".dcType";
        public static final String ANNOTATIONS_ID = ANNOTATIONS + ".anId";

        private Fields() {
            // private constructor to prevent instantiation
        }
    }

}
