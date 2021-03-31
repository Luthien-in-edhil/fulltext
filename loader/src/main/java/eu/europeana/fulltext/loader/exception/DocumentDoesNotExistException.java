package eu.europeana.fulltext.loader.exception;

import eu.europeana.api.commons.error.EuropeanaApiException;
import eu.europeana.fulltext.search.model.query.EuropeanaId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception throw when no documents can be found that match the query
 *
 * @author Lúthien
 * Created on 30 March 2021
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DocumentDoesNotExistException extends EuropeanaApiException {

    private static final long serialVersionUID = -2506967519765835153L;

    public DocumentDoesNotExistException(String datasetId, String whatFields) {
        super("No (further) records in dataset '" + datasetId + "' found without " + whatFields);
    }

    //@Override
    public HttpStatus getResponseStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
