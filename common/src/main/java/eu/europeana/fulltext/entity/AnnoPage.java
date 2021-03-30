package eu.europeana.fulltext.entity;

import org.bson.types.ObjectId;
import dev.morphia.annotations.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Created by luthien on 31/05/2018.
 * Namespace assumptions (see the FTDefinitions class):
 * IIIF Api base URL: https://iiif.europeana.eu/presentation/
 * Resource base URL: https://www.europeana.eu/api/fulltext/
 *
 */
@Entity(value = "AnnoPage")
@Indexes(@Index(fields = { @Field("dsId"), @Field("lcId"), @Field("pgId") }, options = @IndexOptions(unique = true)))
public class AnnoPage {

    @Id
    private ObjectId         _id;
    private String           dsId;
    private String           lcId;
    private String           pgId;
    private String           tgtId;
    private String           lang;
    private Boolean          orig;
    private List<Annotation> ans;
    private Date             modified = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());

    @Reference
    private Resource res;


    public AnnoPage() {}

    public AnnoPage(String dsId, String lcId, String pgId, String tgtId, Resource res) {
        this.dsId  = dsId;
        this.lcId  = lcId;
        this.pgId  = pgId;
        this.tgtId = tgtId;
        this.res   = res;
    }

    public String getDsId() {
        return dsId;
    }

    public void setDsId(String dsId) {
        this.dsId = dsId;
    }

    public String getLcId() {
        return lcId;
    }

    public void setLcId(String lcId) {
        this.lcId = lcId;
    }

    public String getPgId() {
        return pgId;
    }

    public void setPgId(String pgId) {
        this.pgId = pgId;
    }

    public Resource getRes() {
        return res;
    }

    public void setRes(Resource res) {
        this.res = res;
    }

    public String getTgtId() {
        return tgtId;
    }

    public void setTgtId(String tgtId) {
        this.tgtId = tgtId;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public Boolean getOrig() {
        return orig;
    }

    public void setOrig(Boolean orig) {
        this.orig = orig;
    }

    public List<Annotation> getAns() {
        return ans;
    }

    public void setAns(List<Annotation> ans) {
        this.ans = ans;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
        this.modified = modified;
    }

    public String toString() {
        return "/" + this.dsId + "/" + this.getLcId() + "/" + this.getPgId();
    }
}
