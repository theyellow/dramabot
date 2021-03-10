package dramabot.service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

import dramabot.hibernate.bootstrap.model.CatalogEntry;
import dramabot.service.model.CatalogEntryBean;
import dramabot.service.model.CsvBean;
import dramabot.service.model.CsvTransfer;
import dramabot.service.repository.CatalogRepository;

@Service
public class CatalogManager {

	private static Logger logger = LoggerFactory.getLogger(CatalogManager.class);

	private static final String CONFIG_PATH_NAME = "./config/";

	private static final String CATALOG_CSV = "catalog.csv";

	private static final String CONFIG_FILE_NAME = CONFIG_PATH_NAME + CATALOG_CSV;

	private static final Path CONFIG_PATH = FileSystems.getDefault().getPath(CONFIG_FILE_NAME);
	private static final Path MAGIC_CONFIG_PATH = FileSystems.getDefault().getPath(CATALOG_CSV);

	@Autowired
	private CatalogRepository catalogRepository;
	
	public List<String[]> readAll(Reader reader) throws IOException {
		CSVParser parser = new CSVParserBuilder().withSeparator(';').withIgnoreQuotations(true).build();
		CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(0).withCSVParser(parser).build();
		List<String[]> list = new ArrayList<>();
		list = csvReader.readAll();
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
		Path path = null;
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
		if (size <= 0) {
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
		boolean result = true;
		Path path = null;
		// Look for standard-catalog-file if no path is given
		if (Files.isWritable(CONFIG_PATH)) {
			path = CONFIG_PATH;
		} else if (Files.isWritable(MAGIC_CONFIG_PATH)) {
			path = MAGIC_CONFIG_PATH;
		} else {
			logger.error("Could not write config.csv. Is it writable?");
		}
		if (path != null) {
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
		if (capturedExceptions != null && !capturedExceptions.isEmpty()) {
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
		boolean beansToCatalogCsv = writeBeansToCatalogCsv(beansFromFile, CatalogEntryBean.class);
		return beansToCatalogCsv;
	}
}
