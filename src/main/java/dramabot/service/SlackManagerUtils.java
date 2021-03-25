package dramabot.service;

import dramabot.service.model.CatalogEntryBean;
import dramabot.slack.SlackApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

public final class SlackManagerUtils {


    private static SecureRandom secureRandom;

    private static final Logger logger = LoggerFactory.getLogger(SlackCommandManager.class);

    private SlackManagerUtils() {}

    public static String appendPayload(Map<String, List<CatalogEntryBean>> authors, Map<String, List<CatalogEntryBean>> allBeans, String payloadText,
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

    public static Map<String, List<CatalogEntryBean>> fillAuthorsAndReturnAllBeansWithDatabaseContent(Map<String, List<CatalogEntryBean>> authors, CatalogManager catalogManager) {
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

    private static void fillAuthorMap(Map<String, List<CatalogEntryBean>> authors, CatalogEntryBean catalogEntryBean) {
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


    private static String getResponseTypeAndAppend(Map<String, List<CatalogEntryBean>> authorsMap, Map<String, List<CatalogEntryBean>> allBeans, String payloadText, StringBuilder resultBuilder, String responseType) {
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
        } else {
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

    private static void appendRandomText(List<CatalogEntryBean> feedbackBeans, StringBuilder resultBuilder) {
        int size = feedbackBeans.size();
        if (0 < size) {
            resultBuilder.append(feedbackBeans.get(secureRandom.nextInt(size)).getText());
        }
    }

    private static List<CatalogEntryBean> getBeansForAuthor(Map<String, List<CatalogEntryBean>> authorsMap, Map<String, String[]> authorTranslations, String payloadText) {
        return authorsMap.entrySet()
                .stream().filter(
                        x -> !x.getKey().trim().isEmpty() && containsOne(payloadText, authorTranslations.get(x.getKey())))
                .map(Map.Entry::getValue).flatMap(List::stream)
                .collect(Collectors.toList());
    }


}
