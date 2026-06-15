package se.liaprojekt.config;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.liaprojekt.worker.EmailMessageHandler;

import java.util.Base64;

@Configuration
public class ServiceBusWorkerConfig {
//    private static final Logger log = LoggerFactory.getLogger(ServiceBusWorkerConfig.class);

//    private static final String NAMESPACE =
//            "sb-app-dev01.servicebus.windows.net"; //TODO NAMESPACE IS HARDCODED
//
//    private static final String QUEUE_NAME =
//            "email-queue";

    @Value("${spring.cloud.azure.servicebus.connection-string}")
    private String connectionString;

    @Value("${spring.cloud.azure.servicebus.queue-name}")
    private String queueName;

    /**
     * Consumer / Worker client
     */
    @Bean
    public ServiceBusProcessorClient processorClient(EmailMessageHandler handler) {

        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .processor()
                .queueName(queueName)
                .processMessage(handler::handleMessage)
                .processError(handler::handleError)
                .buildProcessorClient();
    }
}