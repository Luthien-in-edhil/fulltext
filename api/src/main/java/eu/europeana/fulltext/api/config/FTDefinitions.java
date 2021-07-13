package eu.europeana.fulltext.api.config;

/**
 * Created by luthien on 14/06/2018.
 */
public final class FTDefinitions {

    public static final  String MEDIA_TYPE_JSONLD         = "application/ld+json";
    public static final  String MEDIA_TYPE_JSON           = "application/json";
    public static final  String V3_ANNO_PAGE_TYPE         = "AnnotationPage";
    public static final  String V3_ANNOTATION_TYPE        = "Annotation";
    public static final  String V3_ANNO_BODY_TYPE         = "SpecificResource";
    public static final  String EDM_FULLTEXTRESOURCE_TYPE = "FullTextResource";
    public static final  String INFO_CANVAS_TYPE          = "Canvas";
    public static final  String INFO_ANNOPAGE_TYPE        = "AnnotationPage";
    public static final  String UTF_8                     = "charset=UTF-8";

    public static final String ANNOPAGE_PATH             = "/annopage";
    public static final char ANNOTATION_PATH             = '/';
    public static final String CANVAS_PATH               = "/canvas";
    public static final String LANGUAGE_PARAM            = "lang=";
    private static final String PROFILE_IS               = ";profile=\"";

    /**
     * Media type for IIIF version 2
     */
    public static final String MEDIA_TYPE_IIIF_V2  = "https://iiif.io/api/presentation/2/context.json";

    /**
     * Media type for IIIF version 3
     */
    public static final String MEDIA_TYPE_IIIF_V3  = "https://iiif.io/api/presentation/3/context.json";

    /**
     * Media type used in @context tag of Fulltext Resource
     */
    public static final String MEDIA_TYPE_EDM_JSONLD  = "https://www.europeana.eu/schemas/context/edm.jsonld";

    /**
     * Media type used in @context tag of Fulltext Summary
     */
    public static final String MEDIA_TYPE_W3ORG_JSONLD  = "https://www.w3.org/ns/anno.jsonld";

    /**
     * Default Content-type returned on manifest requests for version 2
     */
    public static final String MEDIA_TYPE_IIIF_JSONLD_V2 = MEDIA_TYPE_JSONLD
                                                           + PROFILE_IS
                                                           + MEDIA_TYPE_IIIF_V2
                                                           + "\";" + UTF_8;

    /**
     * Default Content-type returned on manifest requests for version 3
     */
    public static final String MEDIA_TYPE_IIIF_JSONLD_V3 = MEDIA_TYPE_JSONLD
                                                           + PROFILE_IS
                                                           + MEDIA_TYPE_IIIF_V3
                                                           + "\";" + UTF_8;

    /**
     * JSON Content-type returned on manifest requests for version 2
     */
    public static final String MEDIA_TYPE_IIIF_JSON_V2 = MEDIA_TYPE_JSON
                                                           + PROFILE_IS
                                                           + MEDIA_TYPE_IIIF_V2
                                                           + "\";" + UTF_8;

    /**
     * JSON Content-type returned on manifest requests for version 3
     */
    public static final String MEDIA_TYPE_IIIF_JSON_V3 = MEDIA_TYPE_JSON
                                                           + PROFILE_IS
                                                           + MEDIA_TYPE_IIIF_V3
                                                           + "\";" + UTF_8;

    private FTDefinitions(){
        // empty constructor to prevent initialization
    }

}
