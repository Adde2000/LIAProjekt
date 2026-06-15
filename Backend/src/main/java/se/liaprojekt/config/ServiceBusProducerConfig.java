package se.liaprojekt.config;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Service Bus konfiguration med Managed Identity.
 * Ingen connection string används.
 */
@Configuration
public class ServiceBusProducerConfig {

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
     * Azure Managed Identity credential
     */
//    @Bean
//    public TokenCredential credential() {
//        return new DefaultAzureCredentialBuilder().build();
//    }

    /**
     * Sender (producer)
     */
    @Bean
    public ServiceBusSenderClient senderClient() {

        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName)
                .buildClient();
    }
}