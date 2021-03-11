package dramabot.slack;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.image;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.view.Views.view;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse;
import com.slack.api.app_backend.slash_commands.response.SlashCommandResponse.SlashCommandResponseBuilder;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.handler.BoltEventHandler;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.bolt.util.BuilderConfigurator;
import com.slack.api.methods.SlackApiRequest;
import com.slack.api.methods.request.chat.ChatPostEphemeralRequest;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatPostMessageRequest.ChatPostMessageRequestBuilder;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.model.event.AppHomeOpenedEvent;
import com.slack.api.model.event.AppMentionEvent;
import com.slack.api.model.view.View;

import dramabot.service.CatalogManager;
import dramabot.service.model.CatalogEntryBean;

@Configuration
@PropertySource({ "file:config/slack-settings.properties" })
public class SlackApp {

	private static Logger logger = LoggerFactory.getLogger(SlackApp.class);

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

	@Autowired
	private CatalogManager catalogManager;

	@Bean
	public App initSlackApp() {
		AppConfig appConfig = AppConfig.builder().
//				clientId(clientId).
//				requestVerificationEnabled(false).
//				clientSecret(clientSecret).
//				signingSecret(signingSecret).
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
			try {
				fillBeansWithDatabaseContent(catalogManager, eseBeans, criticaBeans, feedbackBeans,
						everythingElseBeans);
			} catch (Exception e) {
				logger.error(e.getMessage());
			}
			String payloadText = event.getText();
			String username = event.getUsername();
			username = username == null ? "" : username;
			StringBuilder resultBuilder = new StringBuilder();
			// default response in channel
			String responseType = "in_channel";
			responseType = appendPayload(eseBeans, criticaBeans, feedbackBeans, everythingElseBeans, payloadText,
					resultBuilder, responseType);
			String iconEmoji = payloadText.contains(" amo") ? ":heart:" : null;
			logger.info("{} mentioned dramabot: {}", username, payloadText);
			String text = resultBuilder.toString();
//			if ("in_channel".equals(responseType)) {
			ChatPostMessageRequest reqq = ChatPostMessageRequest.builder().text(text).channel(event.getChannel())
					.iconEmoji(iconEmoji).token(botToken).build();
			ctx.asyncClient().chatPostMessage(reqq);
//			} else {
//				ChatPostEphemeralRequest reqq = ChatPostEphemeralRequest.builder().text(text).user(event.getUser()).channel(event.getChannel()).iconEmoji(iconEmoji).token(botToken).build();
//				ctx.asyncClient().chatPostEphemeral(reqq);
//			}
			return ctx.ack();
		};
	}

	private static SlashCommandHandler dramabotCommandHandler(CatalogManager catalogManager) {

		SlashCommandHandler slashCommandHandler = (req, ctx) -> {
			List<CatalogEntryBean> eseBeans = new ArrayList<>();
			List<CatalogEntryBean> criticaBeans = new ArrayList<>();
			List<CatalogEntryBean> feedbackBeans = new ArrayList<>();
			List<CatalogEntryBean> everythingElseBeans = new ArrayList<>();
			fillBeansWithDatabaseContent(catalogManager, eseBeans, criticaBeans, feedbackBeans, everythingElseBeans);
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
			String responseType = "in_channel";

			logger.info(
					"In channel {} a command '{}' " + "was sent by {}. The text was '{}', as "
							+ "response url '{}' was given. UserId: {} ChannelId:{}",
					channelName, command, userName, payloadText, responseUrl, userId, channelId);
			responseType = appendPayload(eseBeans, criticaBeans, feedbackBeans, everythingElseBeans, payloadText,
					resultBuilder, responseType);
			String text = resultBuilder.toString();
			SlashCommandResponse response = SlashCommandResponse.builder().text(text).responseType(responseType)
					.build();
			return ctx.ack(response);
		};
		return slashCommandHandler;
	}
	

	private static BoltEventHandler<AppHomeOpenedEvent> getHome() {
		return (payload, ctx) -> {
			// Build a Home tab view
			ZonedDateTime now = ZonedDateTime.now();
			View appHomeView = view(view -> view.type("home").blocks(asBlocks(
					section(section -> section.text(markdownText(
							mt -> mt.text(":wave: Ciao, benvenut* a casa del* dramabot! (Last updated: " + now + ")")))),
					image(img -> img
							.imageUrl("https://www.matearium.it/wp-content/uploads/2020/02/tondo_bianco.png")))));
			// Update the App Home for the given user
			ViewsPublishResponse res = ctx.client().viewsPublish(
					r -> r.userId(payload.getEvent().getUser()).hash(payload.getEvent().getView().getHash()) // To
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
	
	private static String appendPayload(List<CatalogEntryBean> eseBeans, List<CatalogEntryBean> criticaBeans,
			List<CatalogEntryBean> feedbackBeans, List<CatalogEntryBean> everythingElseBeans, String payloadText,
			StringBuilder resultBuilder, String responseType) {
		if (null != payloadText && (payloadText.contains("feedback") || payloadText.contains("vorrei")
				|| payloadText.contains(" pens") || payloadText.startsWith("pens"))) {
			try {
				int size = feedbackBeans.size();
				if (size > 0) {
					int random = RandomUtils.nextInt(0, size);
					resultBuilder.append(feedbackBeans.get(random).getText());
				}
			} catch (Exception e) {
				logger.error("Error getting 'feedback' bean.");
			}
		} else if (null != payloadText && (payloadText.contains("domanda") || payloadText.contains("critic")
				|| payloadText.contains(" devo ") || payloadText.contains(" devi ") || payloadText.startsWith("devo ")
				|| payloadText.startsWith("devi "))) {
			try {
				int size = criticaBeans.size();
				if (size > 0) {
					int random = RandomUtils.nextInt(0, size);
					resultBuilder.append(criticaBeans.get(random).getText());
				}
			} catch (Exception e) {
				logger.error("Error getting 'critical' bean.");
			}
		} else if (null != payloadText && (payloadText.contains("capisc") || payloadText.contains("dubbi"))) {
			try {
				int size = eseBeans.size();
				if (size > 0) {
					int random = RandomUtils.nextInt(0, size);
					resultBuilder.append(eseBeans.get(random).getText());
				}
			} catch (Exception e) {
				logger.error("Error getting 'e se' bean.");
			}
		} else if (null != payloadText && payloadText.contains("qualcosa")) {
			try {
				int size = everythingElseBeans.size();
				if (size > 0) {
					int random = RandomUtils.nextInt(0, size);
					resultBuilder.append(everythingElseBeans.get(random).getText());
				} else {
					resultBuilder.append("Qualcosa!");
				}
			} catch (Exception e) {
				logger.error("Error getting 'everything else' bean.");
			}
		} else if (null != payloadText && payloadText.contains("ador")) {
			resultBuilder.append("Anch'io!");
		} else if (null != payloadText && (payloadText.contains(" amo") || payloadText.startsWith("amo"))) {
			resultBuilder.append("Anch'io!");
		} else {
			// if not found don't post in channel but private
			responseType = "ephemeral";
			resultBuilder.append(
					"Orpo, ce vustu? Hai bisogno di un feedback? O vuoi che ti faccia una bella domanda critica? Qualsiasi cosa ti crucci, chiedimi!");
		}
		return responseType;
	}



	private static void fillBeansWithDatabaseContent(CatalogManager catalogManager, List<CatalogEntryBean> eseBeans,
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
		}
	}

}
