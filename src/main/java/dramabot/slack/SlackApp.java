package dramabot.slack;

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.handler.BoltEventHandler;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.model.event.AppHomeOpenedEvent;
import com.slack.api.model.event.AppMentionEvent;
import com.slack.api.model.view.View;
import dramabot.service.CatalogManager;
import dramabot.service.model.CatalogEntryBean;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.view.Views.view;

@Configuration
@PropertySource({"file:config/slack-settings.properties"})
public class SlackApp {

    public static final String IN_CHANNEL = "in_channel";
    public static final String ERROR_TEXT = "Orpo, ce vustu? Hai bisogno di un feedback? O vuoi che ti faccia una bella domanda critica? Qualsiasi cosa ti crucci, chiedimi!";
    private static final Logger logger = LoggerFactory.getLogger(SlackApp.class);
    private static SecureRandom secureRandom;

    @Value(value = "${slack.botToken}")
    private String botToken;

    @Value(value = "${slack.socketToken}")
    private String socketToken;

    @Value(value = "${slack.signingSecret}")
    private String signingSecret;

    @Value(value = "${slack.clientSecret}")
    private String clientSecret;

    @Value(value = "${slack.clientId}")
    private String clientId;

    private static SlashCommandHandler dramabotCommandHandler(CatalogManager catalogManager) {
        return (req, ctx) -> {
            List<CatalogEntryBean> eses = new ArrayList<>();
            List<CatalogEntryBean> critics = new ArrayList<>();
            List<CatalogEntryBean> feedbacks = new ArrayList<>();
            List<CatalogEntryBean> everythingElses = new ArrayList<>();
            Map<String, List<CatalogEntryBean>> authors = new HashMap<>();
            fillBeansWithDatabaseContent(authors, catalogManager, eses, critics, feedbacks, everythingElses);
            SlashCommandPayload payload = req.getPayload();
            String userId = payload.getUserId();
            String userName = payload.getUserName();
            String channelId = payload.getChannelId();
            String channelName = payload.getChannelName();
            String command = payload.getCommand();
            String payloadText = payload.getText();
            String responseUrl = payload.getResponseUrl();

            StringBuilder resultBuilder = new StringBuilder();

            // default response in channel
            String responseType = IN_CHANNEL;

            logger.debug("In channel {} a command '{}' " + "was sent by {}. The text was '{}', as "
                            + "response url '{}' was given. UserId: {} ChannelId:{}",
                    channelName, command, userName, payloadText, responseUrl, userId, channelId);
            responseType = appendPayload(authors, eses, critics, feedbacks, everythingElses, payloadText,
                    resultBuilder, responseType);
            String text = resultBuilder.toString();
            SlashCommandResponse response = SlashCommandResponse.builder().text(text).responseType(responseType)
                    .build();
            return ctx.ack(response);
        };
    }

    private static String appendPayload(Map<String, List<CatalogEntryBean>> authors, List<CatalogEntryBean> eseBeans, List<CatalogEntryBean> criticaBeans,
                                        List<CatalogEntryBean> feedbackBeans, List<CatalogEntryBean> everythingElseBeans, String payloadText,
                                        StringBuilder resultBuilder, String responseType) {
        if (null != payloadText) {
            try {
                secureRandom = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                logger.error("Error getting SecureRandom: {}", e.getMessage());
                return responseType;
            }
            responseType = getResponseTypeAndAppend(authors, eseBeans, criticaBeans, feedbackBeans, everythingElseBeans, payloadText, resultBuilder, responseType);
        } else {
            // if null don't post in channel but private
            responseType = "ephemeral";
            resultBuilder.append(
                    ERROR_TEXT);

        }
        return responseType;
    }

    private static String getResponseTypeAndAppend(Map<String, List<CatalogEntryBean>> authorsMap, List<CatalogEntryBean> eseBeans, List<CatalogEntryBean> criticaBeans, List<CatalogEntryBean> feedbackBeans, List<CatalogEntryBean> everythingElseBeans, String payloadText, StringBuilder resultBuilder, String responseType) {
        Map<String, String[]> authorTranslations = new HashMap<>();
        authorTranslations.put("gubiani", new String[]{"Anna", "Gubiani", "anute"});
        authorTranslations.put("tollis", new String[]{"Giulia", "Tollis"});
        authorTranslations.put("ursella", new String[]{"Stefania", "Ursella"});
        authorTranslations.put("dipauli", new String[]{"Alessandro", "Pauli", "dipi"});
        String[] allAuthors = authorTranslations.entrySet().stream().map(Map.Entry::getValue).flatMap(Arrays::stream).toArray(String[]::new);

        String[] feedbackKeywords = {"feedback", "vorrei", " pens", "pens", "secondo te"};
        String[] criticKeywords = {"domanda", "critic", " devo ", " devi ", "devo ", "devi "};
        String[] eseKeywords = {"capisc", "dubbi", "spiega", "caga", "aiut", "dire", "dici", "dimmi"};
        String[] keywordsMeToo = {"ador", " amo", "amo"};

        String[] helpCommands = {"theyellow", "il tedesco", "help", "bee", "stupid", "merda"};

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
            resultBuilder.append(ERROR_TEXT);
        }
        return responseType;
    }

    private static void appendCommands(StringBuilder resultBuilder, String[] feedbackKeywords, String[] criticKeywords, String[] eseKeywords, String[] keywordsMeToo) {
        resultBuilder.append("\nComandi possibili: ");
        Arrays.stream(feedbackKeywords).forEach(x -> resultBuilder.append(" '" + x + "'"));
        Arrays.stream(criticKeywords).forEach(x -> resultBuilder.append(" '" + x + "'"));
        Arrays.stream(eseKeywords).forEach(x -> resultBuilder.append(" '" + x + "'"));
        Arrays.stream(keywordsMeToo).forEach(x -> resultBuilder.append(" '" + x + "'"));
    }

    @NotNull
    private static List<CatalogEntryBean> getBeansForAuthor(Map<String, List<CatalogEntryBean>> authorsMap, Map<String, String[]> authorTranslations, String payloadText) {
        return authorsMap.entrySet()
                .stream().filter(
                        x ->  !x.getKey().trim().equals("") && containsOne(payloadText, authorTranslations.get(x.getKey())))
                .map(Map.Entry::getValue).flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private static boolean containsOne(String payloadText, String... keywords) {
        long count = 0;
        if (keywords != null) {
            count = Arrays.stream(keywords).map(x -> x.toLowerCase(Locale.ROOT)).filter(payloadText.toLowerCase(Locale.ROOT)::contains).count();
        }
        return count != 0;
    }

    private static void appendRandomText(List<CatalogEntryBean> feedbackBeans, StringBuilder resultBuilder) {
        int size = feedbackBeans.size();
        if (size > 0) {
            resultBuilder.append(feedbackBeans.get(secureRandom.nextInt(size)).getText());
        }
    }

    private static void fillBeansWithDatabaseContent(Map<String, List<CatalogEntryBean>> authors, CatalogManager catalogManager, List<CatalogEntryBean> eseBeans,
                                                     List<CatalogEntryBean> criticaBeans, List<CatalogEntryBean> feedbackBeans,
                                                     List<CatalogEntryBean> everythingElseBeans) {
        List<CatalogEntryBean> allBeans = catalogManager.getBeansFromDatabase();
        for (CatalogEntryBean catalogEntryBean : allBeans) {
            if (catalogEntryBean.getType() != null && catalogEntryBean.getType().trim().equals("e se")) {
                eseBeans.add(catalogEntryBean);
            } else if (catalogEntryBean.getType() != null && catalogEntryBean.getType().trim().equals("critica")) {
                criticaBeans.add(catalogEntryBean);
            } else if (catalogEntryBean.getType() != null && catalogEntryBean.getType().trim().equals("feedback")) {
                feedbackBeans.add(catalogEntryBean);
            } else {
                everythingElseBeans.add(catalogEntryBean);
            }
            fillAuthorMap(authors, catalogEntryBean);
        }
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

    private BoltEventHandler<AppHomeOpenedEvent> getHome() {
        return (payload, ctx) -> {
            // Build a Home tab view
            ZonedDateTime now = ZonedDateTime.now();
            View appHomeView = view(viewBuilder ->
                    viewBuilder
                            .type("home")
                            .blocks(asBlocks(
                                    section(sectionBuilder ->
                                            sectionBuilder
                                                    .text(markdownText(markdownTextBuilder ->
                                                            markdownTextBuilder
                                                                    .text(":wave: Ciao, benvenut* a casa del* dramabot! (Last updated: " + now + ")"))))/*,
                                    image(imageBuilder ->
                                            imageBuilder.imageUrl("https://www.matearium.it/wp-content/uploads/2020/02/tondo_bianco.png"))*/)));

            // Update the App Home for the given user
            ViewsPublishResponse res = ctx.client().viewsPublish(
                    r -> r.userId(payload.getEvent().getUser()) //. hash(payload.getEvent().getView().getHash()) // To
                            // protect
                            // against
                            // possible
                            // race
                            // conditions
                            .view(appHomeView));
            if (!res.isOk()) {
                logger.error("Home response was not ok");
            }
            return ctx.ack();
        };
    }

    @Bean
    public App initSlackApp(CatalogManager catalogManager) {
        AppConfig appConfig = AppConfig.builder().
                /*clientId(clientId).
                requestVerificationEnabled(false).*/
                        clientSecret(clientSecret).
                        signingSecret(signingSecret).
                        singleTeamBotToken(botToken).build();
        App app = new App(appConfig);
        app.event(AppMentionEvent.class, mentionEventHandler(catalogManager));
        app.command("/dramabot", dramabotCommandHandler(catalogManager));
        app.event(AppHomeOpenedEvent.class, getHome());
        return app;
    }

    private BoltEventHandler<AppMentionEvent> mentionEventHandler(CatalogManager catalogManager) {
        return (req, ctx) -> {
            AppMentionEvent event = req.getEvent();
            List<CatalogEntryBean> eseBeans = new ArrayList<>();
            List<CatalogEntryBean> criticaBeans = new ArrayList<>();
            List<CatalogEntryBean> feedbackBeans = new ArrayList<>();
            List<CatalogEntryBean> everythingElseBeans = new ArrayList<>();
            Map<String, List<CatalogEntryBean>> authors = new HashMap<>();
            try {
                fillBeansWithDatabaseContent(authors, catalogManager, eseBeans, criticaBeans, feedbackBeans,
                        everythingElseBeans);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
            String payloadText = event.getText();
            String username = event.getUsername();
            username = username == null ? "" : username;
            StringBuilder resultBuilder = new StringBuilder();
            // default response in channel
            String responseType = IN_CHANNEL;
            responseType = appendPayload(authors, eseBeans, criticaBeans, feedbackBeans, everythingElseBeans, payloadText,
                    resultBuilder, responseType);
            String iconEmoji = payloadText.contains(" amo") ? ":heart:" : null;
            logger.info("{} mentioned dramabot: {}", username, payloadText);
            String text = resultBuilder.toString();

            if (!IN_CHANNEL.equals(responseType)) {
                logger.info("Normally a chatmessage would be posted personally, but in channels with @ - mentioning it's public");
            }
            ChatPostMessageRequest reqq = ChatPostMessageRequest.builder().text(text).channel(event.getChannel())
                    .iconEmoji(iconEmoji).token(botToken).build();
            ctx.asyncClient().chatPostMessage(reqq);

            return ctx.ack();
        };
    }

}
