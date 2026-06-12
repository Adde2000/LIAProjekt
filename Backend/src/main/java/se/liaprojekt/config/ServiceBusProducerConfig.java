package se.liaprojekt.config;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
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
    public TokenCredential credential() {
        return new ManagedIdentityCredentialBuilder().build();
    }

    /**
     * Sender (producer)
     */
    @Bean
    public ServiceBusSenderClient senderClient(TokenCredential credential) {

        return new ServiceBusClientBuilder()
                .credential(NAMESPACE, credential)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .sender()
                .queueName(QUEUE_NAME)
                .buildClient();
    }
}