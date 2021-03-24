package dramabot.service;

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.methods.response.files.FilesUploadResponse;
import dramabot.service.model.CatalogEntryBean;
import dramabot.slack.SlackApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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

    public SlashCommandHandler getCatalogCommandHandler() {
        return (req, ctx) -> {
            //var logger = ctx.logger;
            SlashCommandPayload payload = req.getPayload();
            String userId = payload.getUserId();
            if (!("U01L33BB9B2".equals(userId) || "U01K8BX3YSK".equals(userId))) {
                return ctx.ack("There is no catalog");
            }
            // The name of the file you're going to upload
            String filepath = "./config/catalog.csv";

            // Call the files.upload method using the built-in WebClient
            // The token you used to initialize your app is stored in the `context` object

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
                FilesUploadResponse result = ctx.client().filesUpload(r -> r
                        // The token you used to initialize your app is stored in the `context` object
                        .token(ctx.getBotToken())
                        .channels(Collections.singletonList(payload.getChannelId()))
                        .initialComment("Here's my catalog :smile:")
                        .file(finalPath.toFile())
                        .filename("catalog.csv")
                        .filetype("csv")
                );
                if (!result.isOk()) {
                    logger.warn("could not upload file {}", result);
                } else {
                    logger.info("file {} uploaded", result.getFile());
                }
            } else {
                logger.warn("there were no file found for upload, file is '{}'", filepath);
            }
            // Print result
            return ctx.ack();
        };
    }




}
