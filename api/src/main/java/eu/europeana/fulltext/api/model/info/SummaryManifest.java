package eu.europeana.fulltext.api.model.info;



import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

import static eu.europeana.fulltext.api.config.FTDefinitions.*;

/**
 * Created by luthien on 07/04/2021.
 */
public class SummaryManifest implements Serializable {
    private static final long serialVersionUID = -8052995235828716772L;


    @JsonProperty("@context")
    private final String[] context = new String[]{MEDIA_TYPE_W3ORG_JSONLD, MEDIA_TYPE_IIIF_V3};

    @JsonIgnore
    private String              dataSetId;

    @JsonIgnore
    private String              localId;

    @JsonProperty("items")
    private List<SummaryCanvas> canvases;

    @JsonIgnore
    private Date            modified;

    /**
     * This is a container object to group "fake" SummaryCanvas objects containing original and translated AnnoPages
     * for a given Fulltext record / object
     *
     * @param dataSetId String containing the dataset of this Fulltext SummaryManifest
     * @param localId   String containing the localId of this Fulltext SummaryManifest
     */
    public SummaryManifest(String dataSetId, String localId){
        this.dataSetId = dataSetId;
        this.localId = localId;
        canvases = new ArrayList<>();
        modified = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
    }

    public String getDataSetId() {
        return dataSetId;
    }

    public void setDataSetId(String dataSetId) {
        this.dataSetId = dataSetId;
    }

    public String getLocalId() {
        return localId;
    }

    public void setLocalId(String localId) {
        this.localId = localId;
    }


    /**
     * Adds a *fake* SummaryCanvas containing original and translated versions of an AnnoPage (AnnotationLangPages)
     * @param summaryCanvas SummaryCanvas object to be added to the canvases List
     */
    public void addCanvas(SummaryCanvas summaryCanvas){
        canvases.add(summaryCanvas);
    }

    public List<SummaryCanvas> getCanvases() {
        return new ArrayList<>(canvases);
    }

    public void setCanvases(List<SummaryCanvas> canvases) {
        this.canvases = new ArrayList<>(canvases);
    }

    public String[] getContext() {
        return context;
    }

    public Date getModified() {
        return (Date) modified.clone();
    }

    public void setModified(Date modified) {
        this.modified = (Date) modified.clone();
    }
}
