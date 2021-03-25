package dramabot.service;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.handler.BoltEventHandler;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.files.FilesInfoRequest;
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersListRequest;
import com.slack.api.methods.response.files.FilesInfoResponse;
import com.slack.api.methods.response.usergroups.users.UsergroupsUsersListResponse;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.model.File;
import com.slack.api.model.ModelConfigurator;
import com.slack.api.model.block.ImageBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.event.*;
import com.slack.api.model.view.View;
import dramabot.service.model.CatalogEntryBean;
import dramabot.slack.SlackApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.view.Views.view;

@Service
public class SlackEventManager {

    private static final Logger logger = LoggerFactory.getLogger(SlackEventManager.class);
    @Value(value = "${slack.botToken}")
    private String botToken;
    @Autowired
    private CatalogManager catalogManager;

    public BoltEventHandler<AppMentionEvent> mentionEventHandler() {
        return (req, ctx) -> {
            AppMentionEvent event = req.getEvent();
            Map<String, List<CatalogEntryBean>> authors = new HashMap<>();
            Map<String, List<CatalogEntryBean>> allBeans = SlackManagerUtils.fillAuthorsAndReturnAllBeansWithDatabaseContent(authors, catalogManager);
            String payloadText = event.getText();
            String username = event.getUsername();
            username = null == username ? "" : username;
            StringBuilder resultBuilder = new StringBuilder();
            // default response in channel
            String responseType = SlackApp.IN_CHANNEL;
            responseType = SlackManagerUtils.appendPayload(authors, allBeans, payloadText,
                    resultBuilder, responseType);
            // egg 1
            String iconEmoji = payloadText.contains(" amo") ? ":heart:" : null;
            logger.debug("{} mentioned dramabot: {}", username, payloadText);
            String text = resultBuilder.toString();

            if (!SlackApp.IN_CHANNEL.equals(responseType)) {
                logger.info("Normally a chatmessage would be posted personally, but in channels with @ - mentioning it's public");
            }
            ChatPostMessageRequest reqq = ChatPostMessageRequest.builder().text(text).channel(event.getChannel())
                    .iconEmoji(iconEmoji).token(botToken).build();
            ctx.asyncClient().chatPostMessage(reqq);

            return ctx.ack();
        };
    }

    public BoltEventHandler<MessageEvent> getMessage() {
        return (req, ctx) -> {
            MessageEvent event = req.getEvent();
            return createMessageResponse(ctx, event.getChannel(), event.getText(), event.getType());
        };
    }

    public BoltEventHandler<MessageBotEvent> getBotMessage() {
        return (req, ctx) -> {
            MessageBotEvent event = req.getEvent();
            return createMessageResponse(ctx, event.getChannel(), event.getText(), event.getType());
        };
    }

    private Response createMessageResponse(EventContext ctx, String channel, String text, String eventType) {
        String channelId = ctx.getChannelId();
        String botId = ctx.getBotId();
        String botUserId = ctx.getBotUserId();
        String requestUserToken = ctx.getRequestUserToken();
        Integer num = ctx.getRetryNum();
        String retryReason = ctx.getRetryReason();
        logger.info("{}-message event in channel '{}' (ctx): {} request user token (ctx): {} ; bot was (ctx): {} botuser: {}; there were {} retries ; the text was: {}",
                eventType, channel, channelId, requestUserToken, botId, botUserId, num, text);
        if (0 < num) {
            logger.warn("reason for retry: {}", retryReason);
        }
        return ctx.ack();
    }

    public BoltEventHandler<FileCreatedEvent> getCreatedFile() {
        return (req, ctx) -> {
            FileCreatedEvent event = req.getEvent();
            String userId = event.getUserId();
            String requestUserId = ctx.getRequestUserId();
            String botUserId = ctx.getBotUserId();
            logger.info("file created by {}:{} or bot {} with id {}", userId, requestUserId, botUserId, event.getFileId());
            return ctx.ack();
        };
    }

    public BoltEventHandler<FileSharedEvent> getSharedFile() {
        return (req, ctx) -> {
            logger.info("there was a FileSharedEvent");
            FileSharedEvent event = req.getEvent();
            logger.info("file shared event: {}", event);
            MethodsClient client = ctx.client();
            FilesInfoResponse filesInfo = client.filesInfo(createFilesInfoRequest(event.getFile()));
            File sharedFile = filesInfo.getFile();
            String name = sharedFile.getName();
            String user = event.getUserId();
            logger.info("shared file '{}' by user '{}'", name, user);
            updateCatalogInternal(user, client, sharedFile, name);
            logger.info("end of FileSharedEvent");
            return ctx.ack();
        };
    }

    private FilesInfoRequest createFilesInfoRequest(FileSharedEvent.File file) {
        return FilesInfoRequest.builder().token(botToken).file(file.getId()).build();
    }

    private void updateCatalogInternal(String user, MethodsClient client, File sharedFile, String name) throws IOException, SlackApiException {
        UsergroupsUsersListResponse usergroupsUsersListResponse = client.usergroupsUsersList(createUsergroupsUsersListRequest());
        if (!"catalog.csv".equals(name) || !usergroupsUsersListResponse.isOk() ) {
            logger.info("the file {} is not catalog.csv, so nothing was imported", name);
        } else if (usergroupsUsersListResponse.getUsers().contains(user) && catalogManager.updateCatalog(sharedFile.getUrlPrivate())) {

            try {
                if (catalogManager.initialize()) {
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

    private UsergroupsUsersListRequest createUsergroupsUsersListRequest() {
        return UsergroupsUsersListRequest.builder().token(botToken).usergroup("S01RM9CR39C").build();
    }

    public BoltEventHandler<MessageFileShareEvent> getMessageSharedFile() {
        return (req, ctx) -> {
            logger.info("there was a MessageFileShareEvent");
            MessageFileShareEvent event = req.getEvent();
            String text = event.getText();
            List<com.slack.api.model.File> files = event.getFiles();
            if (null != files) {
                List<String> externalUrls = files.stream().map(File::getUrlPrivate).collect(Collectors.toList());
                logger.info("found files with external urls: {}", externalUrls);
            } else {
                logger.error("file shared event without shared file?");
            }

            if (!text.trim().isEmpty()) {
                logger.info("message event-text was: {}", text);
            }
            String teamId = req.getTeamId();
            String user = event.getUser();
            logger.info("team: {}  ; user from event: {}", teamId, user);

            logger.info("end of MessageFileShareEvent");
            return ctx.ack();
        };
    }

    public BoltEventHandler<AppHomeOpenedEvent> getHome() {
        return (payload, ctx) -> {
            // Build a Home tab view
            ZonedDateTime now = ZonedDateTime.now();
            int dayOfMonth = now.getDayOfMonth();
            int monthValue = now.getMonthValue();
            int year = now.getYear();

            View appHomeView = view(getModelConfigurator(dayOfMonth, monthValue, year));

            // Update the App Home for the given user
            ViewsPublishResponse res = ctx.client().viewsPublish(
                    r -> r.userId(payload.getEvent().getUser())
                            //.hash(payload.getEvent().getView().getHash()) // To protect  against  possible  race  conditions
                            .view(appHomeView));
            if (!res.isOk()) {
                logger.error("Home response was not ok");
            }
            return ctx.ack();
        };
    }

    private ModelConfigurator<View.ViewBuilder> getModelConfigurator(int dayOfMonth, int monthValue, int year) {
        return view -> view.type("home").blocks(asBlocks(
                createMainSection(dayOfMonth, monthValue, year),
                createImageSection()));
    }

    private ImageBlock createImageSection() {
        return image(imageBuilder -> imageBuilder
                .imageUrl("https://www.matearium.it/wp-content/uploads/2020/02/tondo_bianco.png")
                .altText("Matearium"));
    }

    private SectionBlock createMainSection(int dayOfMonth, int monthValue, int year) {
        return section(section -> section.text(markdownText(mt ->
                mt.text(":wave: Ciao, benvenut* a casa del* dramabot!" +
                        "\nPuoi chiedermi feedback sul tuo testo, " +
                        "farti fare una domanda critica, raccontarmi i tuoi dubbi o chiedermi cosa " +
                        "hanno da dire Alessandro, Stefania, Giulia o Anna.\n" +
                        "Se ti rispondo male, puoi chiedere aiuto belando \"bee\"!\n" +
                        "Non ti dimenticare ogni volta di \"chiamarmi\" prima di parlare con me, " +
                        "digitando /dramabot oppure @dramabot \n" +
                        "(Oggi è il " + dayOfMonth + "." + monthValue + "." + year + ")"))));
    }
}
