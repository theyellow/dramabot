package dramabot.service;

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import dramabot.service.model.CatalogEntryBean;
import dramabot.slack.SlackApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class SlackCommandManager {

    private static final Logger logger = LoggerFactory.getLogger(SlackCommandManager.class);

    @Autowired
    private CatalogManager catalogManager;

    public SlashCommandHandler dramabotCommandHandler() {
        return (req, ctx) -> {
            Map<String, List<CatalogEntryBean>> authors = new HashMap<>();
            Map<String, List<CatalogEntryBean>> allBeans = SlackManagerUtils.fillAuthorsAndReturnAllBeansWithDatabaseContent(authors, catalogManager);
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
            String responseType = SlackApp.IN_CHANNEL;

            logger.debug("In channel {} a command '{}' " + "was sent by {}. The text was '{}', as "
                            + "response url '{}' was given. UserId: {} ChannelId:{}",
                    channelName, command, userName, payloadText, responseUrl, userId, channelId);
            responseType = SlackManagerUtils.appendPayload(authors, allBeans, payloadText,
                    resultBuilder, responseType);
            String text = resultBuilder.toString();
            SlashCommandResponse response = SlashCommandResponse.builder().text(text).responseType(responseType)
                    .build();
            return ctx.ack(response);
        };
    }

}
