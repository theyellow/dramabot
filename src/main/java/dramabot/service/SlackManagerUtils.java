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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public enum SlackManagerUtils {
    ;

    private static SecureRandom secureRandom;

    private static final Logger logger = LoggerFactory.getLogger(SlackCommandManager.class);

    public static String appendPayload(Map<String, ? extends List<CatalogEntryBean>> authors, Map<String, ? extends List<CatalogEntryBean>> allBeans, String payloadText,
                                       StringBuilder resultBuilder, String responseType) {
        if (null != payloadText) {
            try {
                secureRandom = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                logger.error("Error getting SecureRandom: {}", e.getMessage());
                return responseType;
            }
            responseType = getResponseTypeAndAppend(authors, allBeans, payloadText, resultBuilder, responseType);
        } else {
            // if null don't post in channel but private
            responseType = "ephemeral";
            resultBuilder.append(
                    SlackApp.ERROR_TEXT);

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
            if (null != catalogEntryBean.getType() && catalogEntryBean.getType().trim().equals(SlackApp.E_SE)) {
                eseBeans.add(catalogEntryBean);
            } else if (null != catalogEntryBean.getType() && catalogEntryBean.getType().trim().equals(SlackApp.CRITICA)) {
                criticaBeans.add(catalogEntryBean);
            } else if (null != catalogEntryBean.getType() && catalogEntryBean.getType().trim().equals(SlackApp.FEEDBACK)) {
                feedbackBeans.add(catalogEntryBean);
            } else {
                everythingElseBeans.add(catalogEntryBean);
            }
            fillAuthorMap(authors, catalogEntryBean);
        }
        Map<String, List<CatalogEntryBean>> result = new HashMap<>();
        result.put(SlackApp.E_SE, eseBeans);
        result.put(SlackApp.CRITICA, criticaBeans);
        result.put(SlackApp.FEEDBACK, feedbackBeans);
        result.put(SlackApp.EVERYTHING_ELSE, everythingElseBeans);
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


    private static String getResponseTypeAndAppend(Map<String, ? extends List<CatalogEntryBean>> authorsMap, Map<String, ? extends List<CatalogEntryBean>> allBeans, String payloadText, StringBuilder resultBuilder, String responseType) {
        Map<String, String[]> authorTranslations = new HashMap<>();
        authorTranslations.put("gubiani", new String[]{"Anna", "Gubiani", "anute"});
        authorTranslations.put("tollis", new String[]{"Giulia", "Tollis"});
        authorTranslations.put("ursella", new String[]{"Stefania", "Ursella"});
        authorTranslations.put("dipauli", new String[]{"Alessandro", "Pauli", "dipi"});
        String[] allAuthors = authorTranslations.values().stream().flatMap(Arrays::stream).toArray(String[]::new);

        String[] feedbackKeywords = {SlackApp.FEEDBACK, "vorrei", " pens", "pens", "secondo te"};
        String[] criticKeywords = {"domanda", "critic", " devo ", " devi ", "devo ", "devi "};
        String[] eseKeywords = {"capisc", "dubbi", "spiega", "caga", "aiut", "dire", "dici", "dimmi"};
        String[] keywordsMeToo = {"ador", " amo", "amo"};

        String[] helpCommands = {"theyellow", "il tedesco", "help", "bee", "stupid", "merda"};

        List<CatalogEntryBean> eseBeans = allBeans.get(SlackApp.E_SE);
        List<CatalogEntryBean> criticaBeans = allBeans.get(SlackApp.CRITICA);
        List<CatalogEntryBean> feedbackBeans = allBeans.get(SlackApp.FEEDBACK);
        List<CatalogEntryBean> everythingElseBeans = allBeans.get(SlackApp.EVERYTHING_ELSE);

        String something = "qualcosa";
        if (containsOne(payloadText, feedbackKeywords)) {
            appendRandomText(feedbackBeans, resultBuilder);
        } else if (containsOne(payloadText, criticKeywords)) {
            appendRandomText(criticaBeans, resultBuilder);
        } else if (containsOne(payloadText, allAuthors)) {
            appendRandomText(getBeansForAuthor(authorsMap, authorTranslations, payloadText), resultBuilder);
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
            responseType = "ephemeral";
            resultBuilder.append(SlackApp.ERROR_TEXT);
        }
        return responseType;
    }

    private static void appendCommands(StringBuilder resultBuilder, String[] feedbackKeywords, String[] criticKeywords, String[] eseKeywords, String[] keywordsMeToo) {
        resultBuilder.append("\nComandi possibili: ");
        Arrays.stream(feedbackKeywords).forEach(x -> resultBuilder.append(SlackApp.TICK_IN).append(x).append(SlackApp.TICK_OUT));
        Arrays.stream(criticKeywords).forEach(x -> resultBuilder.append(SlackApp.TICK_IN).append(x).append(SlackApp.TICK_OUT));
        Arrays.stream(eseKeywords).forEach(x -> resultBuilder.append(SlackApp.TICK_IN).append(x).append(SlackApp.TICK_OUT));
        Arrays.stream(keywordsMeToo).forEach(x -> resultBuilder.append(SlackApp.TICK_IN).append(x).append(SlackApp.TICK_OUT));
    }

    private static boolean containsOne(String payloadText, String... keywords) {
        long count = 0;
        if (null != keywords) {
            count = Arrays.stream(keywords).map(x -> x.toLowerCase(Locale.ROOT)).filter(payloadText.toLowerCase(Locale.ROOT)::contains).count();
        }
        return 0 != count;
    }

    private static void appendRandomText(List<? extends CatalogEntryBean> feedbackBeans, StringBuilder resultBuilder) {
        int size = feedbackBeans.size();
        if (0 < size) {
            resultBuilder.append(feedbackBeans.get(secureRandom.nextInt(size)).getText());
        }
    }

    private static List<CatalogEntryBean> getBeansForAuthor(Map<String, ? extends List<CatalogEntryBean>> authorsMap, Map<String, String[]> authorTranslations, String payloadText) {
        return authorsMap.entrySet()
                .stream().filter(
                        x -> !x.getKey().trim().isEmpty() && containsOne(payloadText, authorTranslations.get(x.getKey())))
                .map(Map.Entry::getValue).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public static void doCatalogCsvResponse(AsyncMethodsClient client, String user, String channelId, String botToken) throws IOException, SlackApiException {
        Future<UsergroupsUsersListResponse> responseFuture = client.usergroupsUsersList(createUsergroupsUsersListRequest(botToken));
        UsergroupsUsersListResponse usergroupsUsersListResponse = null;
        try {
            usergroupsUsersListResponse = responseFuture.get();
        } catch (InterruptedException e) {
            logger.warn("future of doCatalogCsvResponse got interrupted", e);
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

    private static void uploadCatalog(AsyncMethodsClient client, String botToken, String channelId) {
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
