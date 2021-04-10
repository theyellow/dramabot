package dramabot.service;

import com.slack.api.methods.AsyncMethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersListRequest;
import com.slack.api.methods.response.files.FilesUploadResponse;
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersListResponse;
import dramabot.service.model.CatalogEntryBean;
import dramabot.slack.SlackApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static dramabot.slack.SlackApp.*;

public enum SlackManagerUtils {
    ;
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final Logger logger = LoggerFactory.getLogger(SlackCommandManager.class);


    public static String appendPayload(Map<String, ? extends List<CatalogEntryBean>> authors, Map<String, ? extends List<CatalogEntryBean>> allBeans, String payloadText,
                                       StringBuilder resultBuilder) {
        String responseType;
        if (null != payloadText) {
            responseType = getResponseTypeAndAppend(authors, allBeans, payloadText, resultBuilder);
        } else {
            // if null don't post in channel but private
            responseType = EPHEMERAL;
            resultBuilder.append(
                    ERROR_TEXT);

        }
        return responseType;
    }

    public static Map<String, List<CatalogEntryBean>> fillAuthorsAndReturnAllBeansWithDatabaseContent(Map<? super String, List<CatalogEntryBean>> authors, CatalogManager catalogManager) {
        List<CatalogEntryBean> eseBeans = new ArrayList<>();
        List<CatalogEntryBean> criticaBeans = new ArrayList<>();
        List<CatalogEntryBean> feedbackBeans = new ArrayList<>();
        List<CatalogEntryBean> everythingElseBeans = new ArrayList<>();
        List<CatalogEntryBean> allBeans = catalogManager.getBeansFromDatabase();
        for (CatalogEntryBean catalogEntryBean : allBeans) {
            if (null != catalogEntryBean.getType() && catalogEntryBean.getType().trim().equals(E_SE)) {
                eseBeans.add(catalogEntryBean);
            } else if (null != catalogEntryBean.getType() && catalogEntryBean.getType().trim().equals(CRITICA)) {
                criticaBeans.add(catalogEntryBean);
            } else if (null != catalogEntryBean.getType() && catalogEntryBean.getType().trim().equals(FEEDBACK)) {
                feedbackBeans.add(catalogEntryBean);
            } else {
                everythingElseBeans.add(catalogEntryBean);
            }
            fillAuthorMap(authors, catalogEntryBean);
        }
        Map<String, List<CatalogEntryBean>> result = new HashMap<>();
        result.put(E_SE, eseBeans);
        result.put(CRITICA, criticaBeans);
        result.put(FEEDBACK, feedbackBeans);
        result.put(EVERYTHING_ELSE, everythingElseBeans);
        return result;
    }

    private static void fillAuthorMap(Map<? super String, List<CatalogEntryBean>> authors, CatalogEntryBean catalogEntryBean) {
        String author = catalogEntryBean.getAuthor();
        if (null != author) {
            List<CatalogEntryBean> beans = authors.get(author.trim());
            if (null != beans) {
                beans.add(catalogEntryBean);
            } else {
                List<CatalogEntryBean> newEntries = new ArrayList<>();
                newEntries.add(catalogEntryBean);
                authors.put(author.trim(), newEntries);
            }
        }
    }


    private static String getResponseTypeAndAppend(Map<String, ? extends List<CatalogEntryBean>> authorsMap, Map<String, ? extends List<CatalogEntryBean>> allBeans, String payloadText, StringBuilder resultBuilder) {
        String responseType = IN_CHANNEL;
        logger.debug("create text for reply ");
        Map<String, String[]> authorTranslations = new HashMap<>();
        authorTranslations.put("gubiani", new String[]{"Anna", "Gubiani", "anute"});
        authorTranslations.put("tollis", new String[]{"Giulia", "Tollis"});
        authorTranslations.put("ursella", new String[]{"Stefania", "Ursella"});
        authorTranslations.put("dipauli", new String[]{"Alessandro", "Pauli", "dipi"});
        String[] allAuthors = authorTranslations.values().stream().flatMap(Arrays::stream).toArray(String[]::new);

        String[] feedbackKeywords = {FEEDBACK, "vorrei", " pens", "pens", "secondo te"};
        String[] criticKeywords = {"domanda", "critic", " devo ", " devi ", "devo ", "devi "};
        String[] eseKeywords = {"capisc", "dubbi", "spiega", "caga", "aiut", "dire", "dici", "dimmi"};
        String[] keywordsMeToo = {"ador", " amo", "amo"};

        String[] helpCommands = {"theyellow", "il tedesco", "help", "bee", "stupid", "merda"};

        List<CatalogEntryBean> eseBeans = allBeans.get(E_SE);
        List<CatalogEntryBean> criticaBeans = allBeans.get(CRITICA);
        List<CatalogEntryBean> feedbackBeans = allBeans.get(FEEDBACK);
        List<CatalogEntryBean> everythingElseBeans = allBeans.get(EVERYTHING_ELSE);
        logger.debug("beans categorized for random reply");
        String something = "qualcosa";
        if (containsOne(payloadText, feedbackKeywords)) {
            appendRandomText(feedbackBeans, resultBuilder);
        } else if (containsOne(payloadText, criticKeywords)) {
            appendRandomText(criticaBeans, resultBuilder);
        } else if (containsOne(payloadText, allAuthors)) {
            List<CatalogEntryBean> beansForAuthor = getBeansForAuthor(authorsMap, authorTranslations, payloadText);
            appendRandomText(beansForAuthor, resultBuilder);
        } else if (containsOne(payloadText, eseKeywords)) {
            appendRandomText(eseBeans, resultBuilder);
        } else if (containsOne(payloadText, something)) {
            appendRandomText(everythingElseBeans, resultBuilder);
        } else if (containsOne(payloadText, keywordsMeToo)) {
            resultBuilder.append("Anch'io!");
        } else if (containsOne(payloadText, helpCommands)) {
            appendCommands(resultBuilder, feedbackKeywords, criticKeywords, eseKeywords, keywordsMeToo);
        }
        else {
            // if not found don't post in channel but private
            responseType = SlackApp.EPHEMERAL;
            logger.debug("found no corresponding keyword in payloadText");
            resultBuilder.append(ERROR_TEXT);
        }
        logger.debug("appended text to resultBuilder, response type would be {}", responseType);
        return responseType;
    }

    private static void appendCommands(StringBuilder resultBuilder, String[] feedbackKeywords, String[] criticKeywords, String[] eseKeywords, String[] keywordsMeToo) {
        logger.debug("append commands to ");
        resultBuilder.append("\nComandi possibili: ");
        Consumer<String> stringTicksAround = x -> resultBuilder.append(TICK_IN).append(x).append(TICK_OUT);
        Arrays.stream(feedbackKeywords).forEach(stringTicksAround);
        Arrays.stream(criticKeywords).forEach(stringTicksAround);
        Arrays.stream(eseKeywords).forEach(stringTicksAround);
        Arrays.stream(keywordsMeToo).forEach(stringTicksAround);
    }

    private static boolean containsOne(String payloadText, String... keywords) {
        long count = 0;
        if (null != keywords) {
            count = Arrays.stream(keywords).map(x -> x.toLowerCase(Locale.ROOT)).filter(payloadText.toLowerCase(Locale.ROOT)::contains).count();
        }
        boolean containsOne = 0 != count;
        if (containsOne) {
            logger.debug("payload contains at least one of the keywords");
        }
        return containsOne;
    }

    private static void appendRandomText(List<? extends CatalogEntryBean> feedbackBeans, StringBuilder resultBuilder) {
        int size = feedbackBeans.size();
        logger.debug("append one of {} beans", size);
        if (0 < size) {
            int index = secureRandom.nextInt(size);
            logger.debug("take bean {}", index);
            CatalogEntryBean catalogEntryBean = feedbackBeans.get(index);
            String text = catalogEntryBean.getText();
            logger.debug("append {} to resultBuilder, type was {}", text, catalogEntryBean.getType());
            resultBuilder.append(text);
        }
    }

    private static List<CatalogEntryBean> getBeansForAuthor(Map<String, ? extends List<CatalogEntryBean>> authorsMap, Map<String, String[]> authorTranslations, String payloadText) {
        logger.debug("try to get beans for author");
        List<CatalogEntryBean> beans = authorsMap.entrySet()
                .stream().filter(
                        x -> !x.getKey().trim().isEmpty() && containsOne(payloadText, authorTranslations.get(x.getKey())))
                .map(Map.Entry::getValue).flatMap(List::stream)
                .collect(Collectors.toList());
        logger.debug("found {} author beans for the payload", beans.size());
        return beans;
    }

    public static void doCatalogCsvResponse(AsyncMethodsClient client, String user, String channelId, String botToken) throws IOException, SlackApiException, InterruptedException {
        Future<UsergroupsUsersListResponse> responseFuture = client.usergroupsUsersList(createUsergroupsUsersListRequest(botToken));
        UsergroupsUsersListResponse usergroupsUsersListResponse = null;
        try {
            usergroupsUsersListResponse = responseFuture.get();
        } catch (InterruptedException e) {
            logger.warn("future of doCatalogCsvResponse got interrupted", e);
            throw e;
        } catch (ExecutionException e) {
            logger.warn("future of doCatalogCsvResponse couldn't be executed", e);
        }
        if (null != usergroupsUsersListResponse && usergroupsUsersListResponse.isOk() && usergroupsUsersListResponse.getUsers().contains(user)) {
            uploadCatalog(client, botToken, channelId);
        } else {
            logger.info("the user {} is not in administrators bot group, so nothing was imported", user);
        }
    }

    public static UsergroupsUsersListRequest createUsergroupsUsersListRequest(String botToken) {
        return UsergroupsUsersListRequest.builder().token(botToken).usergroup("S01RM9CR39C").build();
    }

    private static void uploadCatalog(AsyncMethodsClient client, String botToken, String channelId) throws InterruptedException {
        // The name of the file to upload
        String filepath = "./config/catalog.csv";
        Path path = null;
        try {
            URL systemResource = ClassLoader.getSystemResource(filepath);
            if (null != systemResource) {
                path = Paths.get(systemResource.toURI());
            } else {
                path = FileSystems.getDefault().getPath(filepath);
            }
        } catch (URISyntaxException e) {
            logger.error("Could not find file {} ", filepath);
        }
        if (null != path) {
            // effectively final for lambda expression... :
            Path finalPath = path.normalize();
            logger.info("uploading {}...", finalPath.toAbsolutePath());
            CompletableFuture<FilesUploadResponse> resultFuture = client.filesUpload(r -> r
                    // The token you used to initialize your app is stored in the `context` object
                    .token(botToken)
                    .initialComment("Here's my catalog :smile:")
                    .file(finalPath.toFile())
                    .filename("catalog.csv")
                    .channels(Collections.singletonList(channelId))
                    .filetype("csv")
            );
            FilesUploadResponse response = null;
            try {
                response = resultFuture.get();
            } catch (InterruptedException e) {
                logger.warn("future of updateCatalog got interrupted", e);
                throw e;
            } catch (ExecutionException e) {
                logger.warn("future of updateCatalog couldn't be executed", e);
            }
            if (null == response || !response.isOk()) {
                logger.warn("could not upload file {}", response);
            } else {
                logger.info("file {} uploaded", response.getFile().getName());
            }
        } else {
            logger.warn("there were no file found for upload, file is '{}'", filepath);
        }
    }

}
