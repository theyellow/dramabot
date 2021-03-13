package dramabot;

import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import dramabot.service.CatalogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@SpringBootApplication
public class Dramabot {

    @Value(value = "${slack.socketToken}")
    private String socketToken;

    private final Logger logger = LoggerFactory.getLogger(Dramabot.class);

    /**
     * Entry point of dramabot. Everything starts here...
     *
	 * @param args arguments given on commandline
     */
    public static void main(String[] args) {
        SpringApplication.run(Dramabot.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            SocketModeApp socketModeApp = ctx.getBean(SocketModeApp.class);
            CatalogManager catalogManager = ctx.getBean(CatalogManager.class);
            boolean initialized = catalogManager.initialize();
            if (!initialized) {
                logger.error("catalog.csv could not be read");
            }
            socketModeApp.start();
        };
    }

    @Bean
    public SocketModeApp socketModeApp(ApplicationContext ctx) {
        App app = ctx.getBean(App.class);
        try {
            return new SocketModeApp(socketToken, app);
        } catch (IOException e) {
            logger.error("SocketModeApp could not be started: {}", e.getMessage());
        }
        throw new BeanInitializationException("SocketModeApp could not be started.");
    }
}
