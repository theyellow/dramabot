package dramabot.slack;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.model.event.*;
import dramabot.service.SlackCommandManager;
import dramabot.service.SlackEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SlackApp {

    public static final String ERROR_TEXT = "Orpo, ce vustu? Hai bisogno di un feedback? O vuoi che ti faccia una bella domanda critica? Qualsiasi cosa ti crucci, chiedimi!";
    public static final String IN_CHANNEL = "in_channel";
    public static final String EPHEMERAL = "ephemeral";
    private static final Logger logger = LoggerFactory.getLogger(SlackApp.class);
    public static final String E_SE = "e se";
    public static final String CRITICA = "critica";
    public static final String FEEDBACK = "feedback";
    public static final String EVERYTHING_ELSE = "everything else";
    public static final String TICK_OUT = "'";
    public static final String TICK_IN = " '";

    @Bean
    public App dramabotApp(SlackCommandManager commandManager, SlackEventManager eventManager) {
        AppConfig appConfig = AppConfig.builder().
                /*clientId(clientId).
                requestVerificationEnabled(false).*/
                        clientSecret(System.getenv("SLACK_CLIENT_SECRET")).
                        signingSecret(System.getenv("SLACK_SIGNING_SECRET")).
                        singleTeamBotToken(System.getenv("SLACK_BOT_TOKEN")).build();
        App app = new App(appConfig);
        app.event(AppMentionEvent.class, eventManager.mentionEventHandler());
        app.command("/dramabot", commandManager.dramabotCommandHandler());
        app.event(AppHomeOpenedEvent.class, eventManager.getHome());
        app.event(MessageFileShareEvent.class, eventManager.getMessageSharedFile());
        app.event(FileSharedEvent.class, eventManager.getSharedFile());
        app.event(MessageBotEvent.class, eventManager.getBotMessage());
        app.event(MessageEvent.class, eventManager.getMessage());
        app.event(FileCreatedEvent.class, eventManager.getCreatedFile());
        logger.info("registered event-handlers and commands of dramabot");
        return app;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor interceptor = (request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + System.getenv("SLACK_BOT_TOKEN"));
            return execution.execute(request, body);
        };
        return builder.interceptors(interceptor).build();
    }

    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(5);
    }

}
