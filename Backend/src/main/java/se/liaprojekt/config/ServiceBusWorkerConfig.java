package se.liaprojekt.config;

import com.azure.identity.DefaultAzureCredential;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.liaprojekt.worker.EmailMessageHandler;

@Configuration
public class ServiceBusWorkerConfig {

    private static final String NAMESPACE =
            "sb-app-dev01.servicebus.windows.net";

    private static final String QUEUE_NAME =
            "email-queue";

    /**
     * Consumer / Worker client
     */
    @Bean
    public ServiceBusProcessorClient processorClient(DefaultAzureCredential credential,
                                                     EmailMessageHandler handler) {

        return new ServiceBusClientBuilder()
                .credential(NAMESPACE, credential)
                .processor()
                .queueName(QUEUE_NAME)
                .processMessage(handler::handleMessage)
                .processError(handler::handleError)
                .buildProcessorClient();
    }
}