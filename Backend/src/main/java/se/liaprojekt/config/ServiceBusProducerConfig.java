package se.liaprojekt.config;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Service Bus konfiguration med Managed Identity.
 * Ingen connection string används.
 */
@Configuration
public class ServiceBusProducerConfig {

    private static final String NAMESPACE =
            "sb-app-dev01.servicebus.windows.net";

    private static final String QUEUE_NAME =
            "email-queue";

    /**
     * Azure Managed Identity credential
     */
    @Bean
    public DefaultAzureCredential credential() {
        return new DefaultAzureCredentialBuilder().build();
    }

    /**
     * Sender (producer)
     */
    @Bean
    public ServiceBusSenderClient senderClient(DefaultAzureCredential credential) {

        return new ServiceBusClientBuilder()
                .credential(NAMESPACE, credential)
                .sender()
                .queueName(QUEUE_NAME)
                .buildClient();
    }
}