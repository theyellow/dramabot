package dramabot.service;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.*;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersListRequest;
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersListResponse;
import dramabot.hibernate.bootstrap.model.CatalogEntry;
import dramabot.service.model.CatalogEntryBean;
import dramabot.service.model.CsvBean;
import dramabot.service.model.CsvTransfer;
import dramabot.service.repository.CatalogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class CatalogManager {

    private static final String CONFIG_PATH_NAME = "./config/";
    private static final String CATALOG_CSV = "catalog.csv";
    private static final String CONFIG_FILE_NAME = CONFIG_PATH_NAME + CATALOG_CSV;
    private static final Path CONFIG_PATH = FileSystems.getDefault().getPath(CONFIG_FILE_NAME);
    private static final Path MAGIC_CONFIG_PATH = FileSystems.getDefault().getPath(CATALOG_CSV);
    private static final Logger logger = LoggerFactory.getLogger(CatalogManager.class);

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private RestTemplate restTemplate;

    public List<String[]> readAll(Reader reader) throws IOException {
        CSVParser parser = new CSVParserBuilder().withSeparator(';').withIgnoreQuotations(true).build();
        CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(0).withCSVParser(parser).build();
        List<String[]> list = csvReader.readAll();
        reader.close();
        csvReader.close();
        return list;
    }

    public List<String[]> readFile(String file) throws IOException, URISyntaxException {
        Reader reader = Files.newBufferedReader(Paths.get(ClassLoader.getSystemResource(file).toURI()));
        return readAll(reader);
    }

    public List<String[]> readCatalog() throws IOException, URISyntaxException {
        return readFile(CONFIG_FILE_NAME);
    }

    public <T extends CsvBean> List<T> csvBeanBuilder(Path path, Class<? extends T> clazz) throws IOException {
        CsvTransfer<T> csvTransfer = new CsvTransfer<>();
        HeaderColumnNameMappingStrategy<T> ms = new HeaderColumnNameMappingStrategy<>();
        ms.setType(clazz);

        Reader reader = Files.newBufferedReader(path);
        CsvToBean<T> cb = new CsvToBeanBuilder<T>(reader).withSeparator(';').withIgnoreQuotations(true).withType(clazz)
                .withMappingStrategy(ms).build();

        csvTransfer.setCsvList(cb.parse());
        reader.close();
        return csvTransfer.getCsvList();
    }

    public List<CatalogEntryBean> readBeansFromFile() throws URISyntaxException, IOException {
        Path path;
        if (!Files.isReadable(CONFIG_PATH)) {
            if (!Files.isReadable(MAGIC_CONFIG_PATH)) {
                path = Paths.get(ClassLoader.getSystemResource(CATALOG_CSV).toURI());
            } else {
                path = MAGIC_CONFIG_PATH;
            }
        } else {
            path = CONFIG_PATH;
        }
        return csvBeanBuilder(path, CatalogEntryBean.class);
    }

    public List<CatalogEntryBean> getBeansFromDatabase() {
        List<CatalogEntryBean> beans = new ArrayList<>();
        Iterable<CatalogEntry> all = catalogRepository.findAll();
        long size = all.spliterator().estimateSize();
        if (0 >= size) {
            logger.error("No entries found on database");
        }
        all.forEach(x -> {
            CatalogEntryBean entryBean = new CatalogEntryBean(x.getEntryText(), x.getEntryAuthor(), x.getEntryType());
            beans.add(entryBean);
        });
        return beans;
    }

    public boolean writeBeansFromDatabaseToCsv()
            throws CsvDataTypeMismatchException, CsvRequiredFieldEmptyException, IOException {
        return writeBeansToCatalogCsv(getBeansFromDatabase(), null);
    }

    public <T extends CsvBean> boolean writeBeansToCatalogCsv(List<T> entryBeans, Class<? extends T> clazz)
            throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
        boolean result;
        Path path = null;
        // Look for standard-catalog-file if no path is given
        if (Files.isWritable(CONFIG_PATH)) {
            path = CONFIG_PATH;
        } else if (Files.isWritable(MAGIC_CONFIG_PATH)) {
            path = MAGIC_CONFIG_PATH;
        } else {
            logger.error("Could not write config.csv. Is it writable?");
        }
        if (null != path) {
            result = writeToFile(entryBeans, path, clazz);
        } else {
            result = false;
        }
        return result;
    }

    private <T extends CsvBean> boolean writeToFile(List<T> entryBeans, Path path, Class<? extends T> clazz)
            throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
        boolean result;
        HeaderColumnNameMappingStrategy<T> ms = new HeaderColumnNameMappingStrategy<>();
        ms.setType(clazz);

        Writer writer = Files.newBufferedWriter(path);
        StatefulBeanToCsv<T> beanToCsv = new StatefulBeanToCsvBuilder<T>(writer).withSeparator(';')
                .withMappingStrategy(ms).withOrderedResults(true).build();

        beanToCsv.write(entryBeans);
        writer.close();
        List<CsvException> capturedExceptions = beanToCsv.getCapturedExceptions();
        if (null != capturedExceptions && !capturedExceptions.isEmpty()) {
            logger.warn("there were {} exceptions thrown: {}", capturedExceptions.size(),
                    capturedExceptions.stream().flatMap(e -> Stream.of(e.getMessage())));
            result = false;
        } else {
            result = true;
        }
        return result;
    }

    public boolean initialize()
            throws URISyntaxException, IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
        List<CatalogEntryBean> beansFromFile = readBeansFromFile();
        catalogRepository.deleteAll();
        for (CatalogEntryBean catalogEntryBean : beansFromFile) {
            CatalogEntry dbEntry = new CatalogEntry(catalogEntryBean.getText(), catalogEntryBean.getAuthor(),
                    catalogEntryBean.getType());
            catalogRepository.save(dbEntry);
        }
        catalogRepository.flush();
        int size = beansFromFile.size();
        long count = catalogRepository.count();
        if (size != count) {
            logger.error("There are {} entries on database but {} in csv-file", count, size);
        } else {
            logger.info("{} entries written to database", count);
        }
        return writeBeansToCatalogCsv(beansFromFile, CatalogEntryBean.class);
    }

    public boolean updateCatalog(String catalogUrl) {
        boolean result = true;
        try {
            Path path = null;
            if (Files.isWritable(CONFIG_PATH)) {
                path = CONFIG_PATH;
            } else if (Files.isWritable(MAGIC_CONFIG_PATH)) {
                path = MAGIC_CONFIG_PATH;
            }
            if (null != path) {
                File file = restTemplate.execute(catalogUrl, HttpMethod.GET, null, clientHttpResponse -> {
                    FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
                    File ret = Files.createTempFile("downloadCatalog", "tmp", attr).toFile();
                    StreamUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(ret));
                    return ret;
                });
                if (null != file) {
                    writeFile(path, file);
                } else {
                    logger.warn("file {} not downloaded", catalogUrl);
                    result = false;
                }
            } else {
                logger.error("Could not write config.csv. Is it writable?");
                result = false;
            }
        } catch (FileNotFoundException e) {
            logger.warn("file not found for {}", catalogUrl);
            result = false;
        } catch (MalformedURLException e) {
            logger.warn("malformed url {}", catalogUrl);
            result = false;
        } catch (IOException e) {
            logger.warn("io-exception for {}", catalogUrl);
            result = false;
        }
        return result;
    }

    private void writeFile(Path path, File file) throws IOException {
        try (ReadableByteChannel readableByteChannel = Channels.newChannel(new FileInputStream(file));
             FileOutputStream fileOutputStream = new FileOutputStream(path.toUri().getPath())) {
                FileChannel fileChannel;
                fileChannel = fileOutputStream.getChannel();
                if (null != readableByteChannel) {
                    fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                }
        }
    }

    public void updateCatalogInternal(String user, MethodsClient client, com.slack.api.model.File sharedFile, UsergroupsUsersListRequest request) throws IOException, SlackApiException {
        UsergroupsUsersListResponse usergroupsUsersListResponse = client.usergroupsUsersList(request);
        String name = sharedFile.getName();
        if (!"catalog.csv".equals(name) || !usergroupsUsersListResponse.isOk() ) {
            logger.info("the file {} is not catalog.csv, so nothing was imported", name);
        } else if (usergroupsUsersListResponse.getUsers().contains(user) && updateCatalog(sharedFile.getUrlPrivate())) {
            try {
                if (initialize()) {
                    logger.info("updated catalog.csv from user {}", user);
                } else {
                    logger.warn("initializing beans from file to database failed");
                }
            } catch (URISyntaxException | CsvDataTypeMismatchException | CsvRequiredFieldEmptyException e) {
                logger.error("problem while reinitializing catalog: {}", e.getMessage());
            }
        } else {
            logger.warn("got error on response of usergroupuserlist-request: {}", usergroupsUsersListResponse.getError());
        }
    }

}
