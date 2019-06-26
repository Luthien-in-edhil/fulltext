package eu.europeana.fulltext.entity;

import dev.morphia.annotations.Embedded;

import java.util.List;

/**
 * Created by luthien on 31/05/2018.
 */
@Embedded
public class Annotation {

    private String       anId;   // IIIF_API_BASE_URL/               /            /annotation/{anId}

    private char         dcType;
    private String       motiv;  // can be stored but is initially not used for output
    private String       lang;   // optional, to override the page-level (actually resource-level) language
    private Integer      from;
    private Integer      to;
    private List<Target> tgs;    // Only the coordinates. Can be multiple e.g. in case of abbreviated words

    /*
     * Parameters below are only used when the Annotation's datasetId and /or localId differ from the other
     * Annotations for this AnnoPage. At time of first implementation it was uncertain if this would really be
     * needed, but I provided the possibility in any case by way of 'future-proofing' If necessary, these can be
     * removed with little effort, they are only read EDM2IIIFMapping.getResourceIdBaseUrl(), .getAnnotationIdUrl(),
     * and getTargetIdBaseUrl() and can be removed there without side effects
     * UPDATE aug 7: I removed them from the mapping class
     */
    private String anDsId;      // IIIF_API_BASE_URL/{anDsId}/        /annotation/..
    private String anLcId;      // IIIF_API_BASE_URL/        /{anLcId}/annotation/..
    private String anResUrl;    // Resource Base URL using a different namespace, eg for external resources
    private String anTgUrl;     // Target URL using a different namespace, eg for external targets

    public Annotation(){}

    public Annotation(String      anId,
                      char      dcType,
                      Integer     from,
                      Integer     to) {
        this.anId   = anId;
        this.dcType = dcType;
        this.from   = from;
        this.to     = to;
    }

    public Annotation(String       anId,
                      char       dcType,
                      Integer      from,
                      Integer      to,
                      List<Target> tgs) {
        this(anId, dcType, from, to);
        this.tgs = tgs;
    }

    public Annotation(String       anId,
                      char       dcType,
                      Integer      from,
                      Integer      to,
                      List<Target> tgs,
                      String       lang) {
        this(anId, dcType, from, to, tgs);
        this.lang = lang;
    }

    public String getAnId() {
        return anId;
    }

    public void setAnId(String anId) {
        this.anId = anId;
    }

    public char getDcType() {
        return dcType;
    }

    public void setDcType(char dcType) {
        this.dcType = dcType;
    }

    public String getMotiv() {
        return motiv;
    }

    public void setMotiv(String motiv) {
        this.motiv = motiv;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }

    public List<Target> getTgs() {
        return tgs;
    }

    public void setTgs(List<Target> tgs) {
        this.tgs = tgs;
    }

//    public String getAnDsId() {
//        return anDsId;
//    }
//
//    public void setAnDsId(String anDsId) {
//        this.anDsId = anDsId;
//    }
//
//    public String getAnLcId() {
//        return anLcId;
//    }
//
//    public void setAnLcId(String anLcId) {
//        this.anLcId = anLcId;
//    }
//
//    public String getAnResUrl() {
//        return anResUrl;
//    }
//
//    public void setAnResUrl(String anResUrl) {
//        this.anResUrl = anResUrl;
//    }
//
//    public String getAnTgUrl() {
//        return anTgUrl;
//    }
//
//    public void setAnTgUrl(String anTgUrl) {
//        this.anTgUrl = anTgUrl;
//    }
}
