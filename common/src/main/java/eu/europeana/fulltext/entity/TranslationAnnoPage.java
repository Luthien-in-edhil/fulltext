package eu.europeana.fulltext.entity;

import dev.morphia.annotations.*;

/**
 * AnnoPage stored as translation
 * Created by P.Ehlert on 25 March 2021.
 */
@Entity(value = "TranslationAnnoPage", useDiscriminator = false)
@Indexes(@Index(fields = { @Field("dsId"), @Field("lcId"), @Field("pgId"), @Field("lang") }, options = @IndexOptions(unique = true)))
@SuppressWarnings("fb-contrib:FCBL_FIELD_COULD_BE_LOCAL")
public class TranslationAnnoPage extends AnnoPage {

    @Reference
    private TranslationResource res;

}
