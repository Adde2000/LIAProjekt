package se.liaprojekt.config;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.liaprojekt.worker.EmailMessageHandler;

import java.util.Base64;

@Configuration
public class ServiceBusWorkerConfig {
    private static final Logger log = LoggerFactory.getLogger(ServiceBusWorkerConfig.class);

    private static final String NAMESPACE =
            "sb-app-dev01.servicebus.windows.net"; //TODO NAMESPACE IS HARDCODED

    private static final String QUEUE_NAME =
            "email-queue";

    /**
     * Consumer / Worker client
     */
    @Bean
    public ServiceBusProcessorClient processorClient(TokenCredential credential,
                                                     EmailMessageHandler handler) {

        // Log the actual token claims
        TokenRequestContext context = new TokenRequestContext()
                .addScopes("https://servicebus.azure.net/.default");

        credential.getToken(context).subscribe(token -> {
            String[] parts = token.getToken().split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getDecoder().decode(
                        parts[1].replace("-", "+").replace("_", "/")));
                log.info("Token claims: {}", payload);
            }
        });

        return new ServiceBusClientBuilder()
                .credential(NAMESPACE, credential)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .processor()
                .queueName(QUEUE_NAME)
                .processMessage(handler::handleMessage)
                .processError(handler::handleError)
                .buildProcessorClient();
    }
}