package eu.europeana.fulltext.loader.service;

import eu.europeana.fulltext.entity.AnnoPage;
import eu.europeana.fulltext.loader.config.LoaderDefinitions;
import eu.europeana.fulltext.loader.config.LoaderSettings;
import eu.europeana.fulltext.loader.exception.LoaderException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by luthien on 26/07/2018.
 */
@Service
public class LoadArchiveService extends SimpleFileVisitor<Path> {

    private static final Logger LOG = LogManager.getLogger(LoadArchiveService.class);

    private XMLParserService parser;
    private MongoService mongoService;
    private LoaderSettings settings;
    private int apCounter = 0;
    private List<AnnoPage> apList = new ArrayList<>();

    public LoadArchiveService(XMLParserService parser, MongoService mongoService, LoaderSettings settings) {
        this.parser = parser;
        this.mongoService = mongoService;
        this.settings = settings;
    }

    /**
     * Load a single zip file (or all available zip files)
     * @param archive
     * @param saveMode
     * @return string containing summary of results (but only for a single zip, doesn't work for all archives yet)
     */
    public String importZipBatch(String archive, MongoSaveMode saveMode) {
        String batchBaseDirectory = settings.getBatchBaseDirectory();
        String zipBatchDir = StringUtils.removeEnd(batchBaseDirectory, "/") + "/";

        if (StringUtils.equalsIgnoreCase(archive, LoaderDefinitions.ALL_ARCHIVES)) {
            try {
                // TODO collect results for each zip and return that as result
                Files.walkFileTree(Paths.get(zipBatchDir), this);
            } catch (IOException e) {
                LogFile.OUT.error("I/O error occurred reading archives at: " + archive , e);
            }
        } else {
            try {
                return processArchive(zipBatchDir + archive, saveMode);
            } catch (LoaderException e) {
                return "Unable to load data in MongoDB: " + e.getMessage();
            }
        }
        return "Finished";
    }

    /**
     * Work around problem with a lambda function throwing a LoaderException, by bypassing the compiler check.
     * Not particularly elegant, but it works.
     */
    @SuppressWarnings("unchecked")
    static <T extends Exception, R> R sneakyThrow(Exception t) throws T {
        throw (T) t; // ( ͡° ͜ʖ ͡°)
    }

    /**
     * Loads a zip file and starts processing it.
     * @param path
     */
    public String processArchive(String path, MongoSaveMode saveMode) throws LoaderException {
        LogFile.setFileName(path);
        LogFile.OUT.info("Processing archive {} with save mode {}", path, saveMode);
        apList.clear();
        apCounter = 0;

        ProgressLogger progressFiles = new ProgressLogger(30);
        ProgressLogger progressAnnotations = new ProgressLogger(-1);
        try (ZipFile archive = new ZipFile(path)) {

            // the size() method counts the folders as well
            int size = getNrOfFiles(archive);
            LogFile.OUT.info("Archive has {} files", size);
            progressFiles.setExpectedItems(size);

            archive.stream()
                    .filter(p -> p.getName().contains(".xml"))
                    .filter(p -> !p.getName().startsWith("__"))
                    .forEach(p -> {
                        try {
                            parseArchiveFile(p, archive, progressFiles, progressAnnotations, saveMode);
                        } catch (LoaderException e) {
                            sneakyThrow(new LoaderException(e.getMessage(), e.getCause()));
                        }
                    });

            if (apCounter > 0) {
                LOG.debug("... remaining {} xml files parsed, flushing to MongoDB ...", apCounter);
                mongoService.saveAnnoPageList(apList, saveMode);
                LOG.debug("... done.");
                apList = new ArrayList<>();
                apCounter = 0;
            }
        } catch (IOException  e) {
            LogFile.OUT.error("Unable to read archive {}", path, e);
            return "Unable to read archive " + path + "; message:" + e.getMessage();
        }

        StringBuilder results = new StringBuilder(progressFiles.getResults());
        results.append(" ");
        results.append(progressAnnotations.getItemsFail());
        results.append(" annotations were skipped.");
        String result = results.toString();
        LogFile.OUT.info(result);
        return result;
    }

    private int getNrOfFiles(ZipFile zips){
        int count = 0;
        Enumeration<? extends ZipEntry> zippies = zips.entries();
        while (zippies.hasMoreElements()) {
            ZipEntry zippy = zippies.nextElement();
            if (!zippy.isDirectory()) {
                count++;
            }
        }
        return count;
    }

    private void parseArchiveFile(ZipEntry element, ZipFile archive, ProgressLogger progressFiles,
                                  ProgressLogger progressAnnotations, MongoSaveMode saveMode) throws LoaderException {
        LOG.debug("Parsing file {} ", element.getName());
        try (InputStream  inputStream = archive.getInputStream(element)) {
            String pageId = getPageIdFromFileName(element.getName());
            AnnoPage ap = parser.parse(pageId, inputStream, element.getName(), progressAnnotations);
            apList.add(ap);
            apCounter++;
            progressFiles.addItemOk();
        } catch (IOException | LoaderException e) {
            progressFiles.addItemFail();
            LogFile.OUT.error("{} - Error parsing file: {}", element.getName(), getRootCauseMsg(e), e);
        }

        if (apCounter > 99){
            LOG.debug("... 100 xml files parsed, flushing to MongoDB ...");
            mongoService.saveAnnoPageList(apList, saveMode);
            LOG.debug("... done, continuing ...");
            apList.clear();
            apCounter = 0;
        }
        LOG.debug("Done parsing file {} ", element.toString());
    }

    private String getPageIdFromFileName(String fileName ) {
        String pageId = fileName;
        if (StringUtils.contains(pageId, "/")) {
            pageId = StringUtils.substringAfterLast(pageId, "/");
        }
        return StringUtils.removeEndIgnoreCase(pageId, ".xml");
    }

    private String getRootCauseMsg(Throwable e) {
        String result = null;
        if (e != null) {
            if (e.getCause() == null) {
                result = e.getMessage();
            } else {
                result = getRootCauseMsg(e.getCause());
            }
        }
        return result;
    }

}
