package dramabot.service;

import com.slack.api.Slack;
import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.AsyncMethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import dramabot.service.model.CatalogEntryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;


@Service
public class SlackCommandManager {

    private static final Logger logger = LoggerFactory.getLogger(SlackCommandManager.class);

    @Autowired
    private CatalogManager catalogManager;

    @Autowired
    public ExecutorService executorService;

    @Value(value = "${slack.botToken}")
    private String botToken;

    public SlashCommandHandler dramabotCommandHandler() {
        return (req, ctx) -> {
            Response ack = ctx.ack();
            executorService.execute(() -> {
                try {
                    createAsyncDramabotResponse(req);
                } catch (ExecutionException e) {
                    logger.warn("problem with asynchronous dramabot response", e);
                } catch (InterruptedException e) {
                    logger.warn("problem with asynchronous dramabot response: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            });
            return ack;
        };
    }

    private void createAsyncDramabotResponse(com.slack.api.bolt.request.builtin.SlashCommandRequest req) throws ExecutionException, InterruptedException {
        Slack slack = Slack.getInstance();
        AsyncMethodsClient client = slack.methodsAsync(botToken);
        Map<String, List<CatalogEntryBean>> authors = new HashMap<>();
        Map<String, List<CatalogEntryBean>> allBeans = SlackManagerUtils.fillAuthorsAndReturnAllBeansWithDatabaseContent(authors, catalogManager);
        SlashCommandPayload payload = req.getPayload();
        String userId = payload.getUserId();
        String userName = payload.getUserName();
        String channelId = payload.getChannelId();
        String channelName = payload.getChannelName();
        String command = payload.getCommand();
        String payloadText = payload.getText();
/*
        String responseUrl = payload.getResponseUrl();
*/

        StringBuilder resultBuilder = new StringBuilder();

        // default response in channel


        logger.debug("In channel {} '{}' " + "was sent by {}. The text was '{}', with UserId: {} ChannelId:{}",
                channelName, command, userName, payloadText, userId, channelId);
        if (!payloadText.toLowerCase(Locale.ROOT).contains("catalogo")) {
            String responseType = SlackManagerUtils.appendPayload(authors, allBeans, payloadText,
                    resultBuilder);
            logger.debug("the responseType of 'catalogo' should be {}, but we can just send a ChatPostMessageRequest to channel", responseType);
            logger.debug("starting StringBuilder.toString() for answer");
            String text = resultBuilder.toString();
            logger.debug("answer is: {} ; now starting chatPostMessageRequest", text);
            String iconEmoji = payloadText.contains(" amo") ? ":heart:" : null;
            ChatPostMessageRequest asyncRequest = ChatPostMessageRequest.builder().text(text).channel(channelId)
                    .iconEmoji(iconEmoji).token(botToken).build();
            CompletableFuture<ChatPostMessageResponse> postMessageResponseCompletableFuture = client.chatPostMessage(asyncRequest);
            ChatPostMessageResponse chatPostMessageResponse = postMessageResponseCompletableFuture.get();
            logger.debug("async reply with text was send - result was {}", chatPostMessageResponse.isOk() ? "ok" : chatPostMessageResponse.getErrors());
        } else {
            try {
                SlackManagerUtils.doCatalogCsvResponse(client, userId, channelId, botToken);
            } catch (IOException | SlackApiException e) {
                logger.debug("Error in /dramabot - command while searching for catalog", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.debug("");
    }

}
